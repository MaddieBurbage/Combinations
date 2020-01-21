//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
// (c) Maddie Burbage, 2020
package combinations

import Chisel._
import freechips.rocketchip.tile._ //For LazyRoCC
import freechips.rocketchip.config._ //For Config
import freechips.rocketchip.diplomacy._ //For LazyModule
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq} //something and level one cache

class Combinations(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
    override lazy val module = new CombinationsImp(this)
}

class CombinationsImp(outer: Combinations)(implicit p: Parameters) extends LazyRoCCModuleImp(outer){

    val s_idle :: s_resp :: Nil = Enum(Bits(), 2)
    val state = Reg(init = s_idle) //state starts idle, is remembered


    val submodules = Array(Module(new FixedWeight()(p)), Module(new Lexicographic()(p)), Module(new GeneralCombinations()(p)), Module(new RangedCombinations()(p)))
    val captureWeights = Module(new CaptureWeights()(p))
    //Instruction inputs
    val length = Reg(io.cmd.bits.rs1(4,0)) //Length of binary string
    val previous = Reg(io.cmd.bits.rs2) //Previous binary string

    //Secondary Inputs
    val rd = Reg(io.cmd.bits.inst.rd) //Output location
    val function = Reg(io.cmd.bits.inst.funct) //Specific operation

    //Set up submodule inputs
    for(x <- submodules) {
       x.io.length := length
       x.io.previous := previous
    }
    captureWeights.io.newMin := length
    captureWeights.io.newMax := previous
    submodules(3).io.minWeight := captureWeights.io.minWeight
    submodules(3).io.maxWeight := captureWeights.io.maxWeight
    captureWeights.io.reset = Mux(function === 3.U && io.out === -1.S, 1.U, 0.U)
    captureWeights.io.set = Mux(function === 4.U, 1.U, 0.U)



    //State-based communication values
    io.cmd.ready := (state === s_idle)
    io.busy := (state =/= s_idle)

    //When a new command is received, capture inputs and become busy
    when(io.cmd.fire()) {
        state := s_resp
    }

    length := (io.cmd.bits.rs1(4,0))
    previous := io.cmd.bits.rs2
    rd := io.cmd.bits.inst.rd
    function := io.cmd.bits.inst.funct

    //Obtain accelerator output from the correct submodule for the function
    val lookups = Array(0.U->submodules(0).io.out,1.U->submodules(1).io.out, 2.U->submodules(2).io.out,
        3.U->submodules(3).io.out, 4.U->captureWeights.io.success)
//  val newLookups = lookups.zipWithIndex.map(_.swap)}
    io.resp.bits.data := MuxLookup(function, submodules(0).io.out, lookups)



    io.resp.bits.rd := rd
    io.resp.valid := (state === s_resp)

    //After responding, become ready for new commands
    when(io.resp.fire()) {
        state := s_idle
    }

    //These features not used
    io.interrupt := Bool(false)
    io.mem.req.valid := Bool(false)

}

//Generates a fixed-weight binary string based on a previous string of the same
//weight and length. Binary strings up to length 32 will work.
class FixedWeight()(implicit p: Parameters) extends Module {
    val io = IO(new subIO)

    //Calculations to generate the next combination
    val trimmed = io.previous & (io.previous + 1.U)
    val trailed = trimmed ^ (trimmed - 1.U)

    val indexShift = trailed + 1.U
    val indexTrailed = trailed & io.previous

    val subtracted = (indexShift & io.previous) - 1.U
    val fixed = Mux(subtracted.asSInt < 0.S, 0.U, subtracted)

    val result = io.previous + indexTrailed - fixed

    val stopper = 1.U(1.W) << io.length

    //Fill result with all 1s if finished
    io.out := Mux(result >> io.length =/= 0.U, Fill(64,1.U), result % stopper)
}

//Generates the lexicographically-next binary string, up to a length of 32
class Lexicographic()(implicit p: Parameters) extends Module {
    val io = IO(new subIO)

    val result = io.previous + 1.U
    io.out := Mux(((result >> io.length) & 1.U) === 1.U, Fill(64,1.U), result)
}


//Generates the next binary string of a certain length based on the cool-er ordering
class GeneralCombinations()(implicit p: Parameters) extends Module {
    val io = IO(new subIO)

    //Calculations
    val trimmed = io.previous(31,1) | (io.previous(31,1) - 1.U)
    val trailed = trimmed ^ (trimmed + 1.U)
    val mask = Wire(UInt(32.W))
    mask := (trailed << 1.U) + 1.U

    val lastTemp = Wire(UInt(32.W))
    lastTemp :=  trailed + 1.U
    val lastLimit = 1.U << (io.length - 1.U)
    val lastPosition = Mux(lastTemp > lastLimit || lastTemp === 0.U, lastLimit, lastTemp)

    val cap = 1.U << io.length
    val first = Mux(mask < cap, 1.U & io.previous, 1.U & ~io.previous)
    val shifted = (io.previous & mask) >> 1.U
    val rotated = Mux(first === 1.U, shifted | lastPosition, shifted)
    val result = rotated | (~mask & io.previous)

    io.out := Mux(result === (cap - 1.U), Fill(64,1.U), result)
}

//Generates the next binary string within a weight range, based on cool-est ordering
class RangedCombinations()(implicit p: Parameters) extends Submodule {
    val io = IO(new subIO{
        val minWeight = Input(UInt(5.W))
        val maxWeight = Input(UInt(5.W))
    })

    //Calculations
    val trimmed = io.previous(31,1) | (io.previous(31,1) - 1.U)
    val trailed = trimmed ^ (trimmed + 1.U)
    val mask = Wire(UInt(32.W))
    mask := (trailed << 1.U) + 1.U

    val lastTemp = Wire(UInt(32.W))
    lastTemp :=  trailed + 1.U
    val lastLimit = 1.U << (io.length - 1.U)
    val lastPosition = Mux(lastTemp > lastLimit || lastTemp === 0.U, lastLimit, lastTemp)

    //Find number of bits set using muxes of partial counts, then adding them together
    //val lookups = Array(0.U,1.U,2.U,2.U,1.U,2.U,2.U,3.U,1.U,2.U,2.U,3.U,2.U,3.U,3.U,4.U)
    //val partials = Array[Wire](8)
    //for(i <- 0 until 8) {
    //    partials(i) = MuxLookup(io.previous((i+1)*4-1,i*4), 0.U, lookups.zipWithIndex.map(_.swap))
    //}
    //val first = MuxLookup(io.previous(3,0), 0.U, lookups.zipWithIndex.map(_.swap))

    val count = PopCount(io.previous)

    val cap = 1.U << io.length
    val first = Mux(mask < cap, 1.U & io.previous, 1.U & ~io.previous)
    val shifted = (io.previous & mask) >> 1.U

    //Flip the bit while rotating if no 01 and new string is valid
    val rotated = Mux(first === 1.U && count <= io.maxWeight && count >= io.minWeight, shifted | lastPosition, shifted)
    val result = rotated | (~mask & io.previous)

    io.out := Mux(result === (cap - 1.U), Fill(64,1.U), result)
}

//Stores the min and max weight used for ranged combinations (function 3). The
//cycle of ranged combinations must complete before loading new weights
class CaptureWeights()(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val newMin = Input(UInt(5.W))
        val newMax = Input(UInt(5.W))
        val reset = Input(UInt(1.W))
        val set = Input(UInt(1.W))
        val success = Output(UInt(64.W))
        val minWeight = Output(UInt(5.W))
        val maxWeight = Output(UInt(5.W))
    })

    val lastMinWeight = Reg(UInt(5.W))
    val lastMaxWeight = Reg(UInt(5.W))
    val inUse = Reg(UInt(1.W))

    when(inUse === 0.U && set === 1.U) {
        lastMinWeight := io.newMin
        lastMaxWeight := io.newMax
        inUse := 1.U
        io.success := 0.U
    }
    .otherwise {
        io.success := Fill(64, 1.U)
    }

    when(reset === 1.U) {
        inUse := 0.U
    }

    io.minWeight := lastMinWeight
    io.maxWeight := lastMaxWeight
}

//Base class for this accelerator's submodule IOs
class subIO extends Bundle {
    val length = Input(UInt(5.W))
    val previous = Input(UInt(64.W))
    val out = Output(UInt(64.W))
}


class WithCombinations extends Config((site, here, up) => {
    case BuildRoCC => Seq((p: Parameters) => {
        val Combinations = LazyModule.apply(new Combinations(OpcodeSet.custom0) (p))
        Combinations
    })
})

/**
 * Add this into the example project's RocketConfigs.scala file:

class CombinationsRocketConfig extends Config(
    new WithTop ++
    new WithBootROM ++
    new freechips.rocketchip.subsystem.WithInclusiveCache ++
    new combinations.WithCombinations ++
    new freechips.rocketchip.subsystem.WithNBigCores(1) ++
    new freechips.rocketchip.system.BaseConfig
)

 */
