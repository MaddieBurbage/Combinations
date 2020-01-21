//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
// (c) Maddie Burbage, 2020
package combinations

import Chisel._
import freechips.rocketchip.tile._ //For LazyRoCC
import freechips.rocketchip.config._ //For Config
import freechips.rocketchip.diplomacy._ //For LazyModule
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq} //For connections

class Combinations(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
    override lazy val module = new CombinationsImp(this)
}

class CombinationsImp(outer: Combinations)(implicit p: Parameters) extends LazyRoCCModuleImp(outer){

    val s_idle :: s_work :: s_resp :: Nil = Enum(Bits(), 3)
    val state = Reg(init = s_idle) //state starts idle, is remembered

    //Submodules for functions: FixedWeight, Lexicographic, General, Ranged, CaptureWeights, Memory
    val submodules = Array(Module(new FixedWeight()(p)), Module(new Lexicographic()(p)), Module(new GeneralCombinations()(p)))
    val rangedCombinations = Module(new RangedCombinations()(p))
    val captureWeights = Module(new CaptureWeights()(p))
    val memoryCombinations = Module(new MemoryFixedCombinations()(p))

    //Instruction inputs
    val length = Reg(init = io.cmd.bits.rs1(4,0)) //Length of binary string
    val previous = Reg(init = io.cmd.bits.rs2) //Previous binary string
    val rd = Reg(init = io.cmd.bits.inst.rd) //Output location
    val function = Reg(init = io.cmd.bits.inst.funct) //Specific operation


    //Set up main submodule inputs
    for(x <- submodules) {
       x.io.length := length
       x.io.previous := previous
    }
    captureWeights.io.newMin := length
    captureWeights.io.newMax := previous
    rangedCombinations.io.length := length
    rangedCombinations.io.previous := previous
    memoryCombinations.io.length := length
    memoryCombinations.io.address := previous

    //Set up Unique submodule inputs
    rangedCombinations.io.minWeight := captureWeights.io.minWeight
    rangedCombinations.io.maxWeight := captureWeights.io.maxWeight
    captureWeights.io.reset := Mux(function === 3.U && io.resp.bits.data === ~(0.U(64.W)), 1.U, 0.U)
    captureWeights.io.set := Mux(function === 4.U, 1.U, 0.U)



    //Accelerator State
    io.cmd.ready := (state === s_idle)

    when(io.cmd.fire()) {
	when(function === 3.U) {
	    state := s_work
	    memoryCombinations.io.start := 1.U
	} .otherwise {
            state := s_resp
	}
	length := (io.cmd.bits.rs1(4,0))
	previous := io.cmd.bits.rs2
	rd := io.cmd.bits.inst.rd
	function := io.cmd.bits.inst.funct

    } .otherwise {
	memoryCombinations.io.start := 0.U
    }

    when(state === s_work && memoryCombinations.io.working === 0.U) {
	state := s_resp
    }

    when(io.resp.fire()) {
        state := s_idle
    }

    //Accelerator response
    val lookups = Array(0.U->submodules(0).io.out,1.U->submodules(1).io.out, 2.U->submodules(2).io.out,
        3.U->memoryCombinations.io.out, 4.U->rangedCombinations.io.out, 5.U->captureWeights.io.success)
    io.resp.bits.data := MuxLookup(function, submodules(0).io.out, lookups)

    io.resp.bits.rd := rd
    io.resp.valid := state === s_resp

    //Memory request interface
    io.mem.req.valid := memoryCombinations.io.working
    io.busy := memoryCombinations.io.working
    io.mem.req.bits.addr := memoryCombinations.io.currentAddress
    io.mem.req.bits.tag := memoryCombinations.io.out(9,0) //Change for out-of-order
    io.mem.req.bits.cmd := 0.U //change when actually storing
    io.mem.req.bits.data := Bits(0) //Also changed
    io.mem.req.bits.size := log2Ceil(32).U
    io.mem.req.bits.signed := Bool(false)
    io.mem.req.bits.phys := Bool(false)

    when(io.mem.resp.valid) {
	memoryCombinations.io.mem.respValid := 1.U
	memoryCombinations.io.mem.respData := io.mem.resp.bits.data
	memoryCombinations.io.mem.respTag := io.mem.resp.bits.tag
    } .otherwise {
	memoryCombinations.io.mem.respValid := 0.U
    }

    when(io.mem.req.fire()) {
	memoryCombinations.io.mem.reqFire := 1.U
    } .otherwise {
	memoryCombinations.io.mem.reqFire := 0.U
    }

    //Always false
    io.interrupt := Bool(false)

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

//Generates all binary strings based on an input and "saves" it to memory.
//Strings of length up to 32 work.
class MemoryFixedCombinations()(implicit p: Parameters) extends Module() {
    val io = IO(new Bundle { //address and length
	val length = Input(UInt(5.W))
	val address = Input(UInt(64.W))
	val mem = Input(new Bundle {
	    val respValid = Input(UInt(1.W))
	    val respTag = Input(UInt(10.W))
	    val respData = Input(UInt(32.W))
	    val reqFire = Input(UInt(1.W))
	})
	val start = Input(UInt(1.W))
	val currentAddress = Output(UInt(64.W))
	val out = Output(UInt(64.W))
	val working = Output(UInt(1.W))
    })

    val s_store ::  s_idle :: Nil = Enum(Bits(), 2)
    val state = Reg(init = s_idle)

    val initial = (1.U << io.length) - 1.U
    val nextSent = Reg(UInt(64.W))

    when(io.start === 1.U) {
	state := s_store
	nextSent := initial
    }

    when(nextSent === ~(0.U(64.W))) {
	state := s_idle
    }
    
    //Calculations
    val trimmed = nextSent(31,1) | (nextSent(31,1) - 1.U)
    val trailed = trimmed ^ (trimmed + 1.U)
    val mask = Wire(UInt(32.W))
    mask := (trailed << 1.U) + 1.U
    val lastTemp = Wire(UInt(32.W))
    lastTemp :=  trailed + 1.U
    val lastLimit = 1.U << (io.length - 1.U)
    val lastPosition = Mux(lastTemp > lastLimit || lastTemp === 0.U, lastLimit, lastTemp)
    val cap = 1.U << io.length
    val first = Mux(mask < cap, 1.U & nextSent, 1.U & ~nextSent)
    val shifted = (nextSent & mask) >> 1.U
    val rotated = Mux(first === 1.U, shifted | lastPosition, shifted)
    val result = rotated | (~mask & nextSent)
    val stopper = 1.U(1.W) << io.length


    val offset = Reg(init = 0.U)
    val sum = Reg(init = 0.U)
    
    //Outputs
    nextSent := Mux(result >> io.length =/= 0.U, Fill(64,1.U), result % stopper)
    io.out := sum
    io.currentAddress := io.address + offset

    //Signals to accelerator
    io.working := state === s_store

    //After getting response from memory
    when(io.mem.respValid === 1.U) {
	sum := sum + io.mem.respData
	nextSent := result
	offset := offset + 32.U
    }
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
class RangedCombinations()(implicit p: Parameters) extends Module {
    val io = IO(new wideIO)

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

    when(inUse === 0.U && io.set === 1.U) {
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

class wideIO extends subIO {
    val minWeight = Input(UInt(5.W))
    val maxWeight = Input(UInt(5.W))
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
