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

    val fixedWeightModule = Module(new FixedWeight()(p))
    val lexicographicModule = Module(new Lexicographic()(p))


    //Instruction inputs
    val length = Reg(io.cmd.bits.rs1(4,0)) //Length of binary string
    val previous = Reg(io.cmd.bits.rs2) //Previous binary string

    //Secondary Inputs
    val rd = Reg(io.cmd.bits.inst.rd) //Output location
    val function = Reg(io.cmd.bits.inst.funct) //Specific operation

    //Set up submodule inputs
    fixedWeightModule.io.length := length
    fixedWeightModule.io.previous := previous
    lexicographicModule.io.length := length
    lexicographicModule.io.previous := previous

    //State-based communication values
    io.cmd.ready := (state === s_idle)
    io.busy := (state =/= s_idle)

    //When a new command is received, capture inputs and become busy
    when(io.cmd.fire()) {
        state := s_resp
        length := (io.cmd.bits.rs1(4,0))
        previous := io.cmd.bits.rs2
        rd := io.cmd.bits.inst.rd
        function := io.cmd.bits.inst.funct
    }

    //Obtain accelerator output from the correct submodule for the function
    io.resp.bits.data := MuxLookup(function, fixedWeightModule.io.out,
            Array(0.U->fixedWeightModule.io.out, 1.U->lexicographicModule.io.out))

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
class FixedWeight()(implicit p: Parameters) extends Submodule {

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
    io.out := Mux(result >> io.length =/= 0.U, ~(0.U), result % stopper)
}

//Generates the lexicographically-next binary string
class Lexicographic()(implicit p: Parameters) extends Submodule {
    val result = io.previous + 1.U
    io.out := Mux(((result >> io.length) & 1.U) === 1.U, ~(0.U), result)
}

//Base class for this accelerator's submodules
abstract class Submodule()(implicit p: Parameters) extends Module {
    val io = IO(new Bundle {
        val length = Input(UInt(5.W))
        val previous = Input(UInt(32.W))
        val out = Output(UInt(32.W))
    })
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
