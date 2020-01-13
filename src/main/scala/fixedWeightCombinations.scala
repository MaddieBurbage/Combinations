//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
//Works for string lengths up to 32
// Maddie Burbage, 2020
package fixedWeightCombinations

import Chisel._
import freechips.rocketchip.tile._ //For LazyRoCC
import freechips.rocketchip.config._ //For Config
import freechips.rocketchip.diplomacy._ //For LazyModule
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq} //something and level one cache

class FixedWeightCombinations(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
    override lazy val module = new FixedWeightCombinationsImp(this)
}

class FixedWeightCombinationsImp(outer: FixedWeightCombinations)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) with HasCoreParameters{

    val s_idle :: s_resp :: Nil = Enum(Bits(), 2)
    val state = Reg(init = s_idle) //state starts idle, is remembered

    //Communication values based on the accelerator's state
    io.cmd.ready := (state === s_idle)
    io.resp.valid := (state === s_resp)
    io.busy := (state =/= s_idle)


    //Get length of binary strings and previous string from the source registers
    val length = Reg(io.cmd.bits.rs1(4,0)) //Length of binary string
    val previous = Reg(io.cmd.bits.rs2) //Previous


    //Output the new string to the destination register
    val rd = Reg(io.cmd.bits.inst.rd) //Output location


    //When a new command is received, capture inputs and become busy
    when(io.cmd.fire()) {
        state := s_resp
        length := (io.cmd.bits.rs1(4,0))
        previous := io.cmd.bits.rs2
        rd := io.cmd.bits.inst.rd
    }

    //After responding, become ready for new commands
    when(io.resp.fire()) {
//      printf("result, %b", result);
	//printf("trimmed %d, trailed %d\n indexShift %d, indexTrailed %d\n subtracted %d, fixed %d\n result %d, stopper %d\n\n", trimmed, trailed, indexShift, indexTrailed, subtracted, fixed, result, stopper)
        state := s_idle
    }


    //Calculations
    val trimmed = previous & (previous + 1.U)
    val trailed = trimmed ^ (trimmed - 1.U)

    val indexShift = trailed + 1.U
    val indexTrailed = trailed & previous

    val subtracted = (indexShift & previous) - 1.U
    val fixed = Mux(subtracted.asSInt < 0.S, 0.U, subtracted)

    val result = previous + indexTrailed - fixed

    val stopper = 1.U(1.W) << length

    //Fill result with all 1s if finished, same response as divide by zero has
    io.resp.bits.data := Mux(result >> length =/= 0.U, ~(0.U), result % stopper)


    //Response
    io.resp.bits.rd := rd
    io.interrupt := Bool(false)
    io.mem.req.valid := Bool(false)

}

class WithFixedWeightCombinations extends Config((site, here, up) => {
    case BuildRoCC => Seq((p: Parameters) => {
        val fixedWeightCombinations = LazyModule.apply(new FixedWeightCombinations(OpcodeSet.custom0) (p))
        fixedWeightCombinations
    })
})

/**
 * Add this into the example project's RocketConfigs.scala file:
class FixedWeightCombinationsRocketConfig extends Config(
    new WithTop ++
    new WithBootROM ++
    new freechips.rocketchip.subsystem.WithInclusiveCache ++
    new fixedWeightCombinations.WithFixedWeightCombinations ++
    new freechips.rocketchip.subsystem.WithNBigCores(1) ++
    new freechips.rocketchip.system.BaseConfig
)

 */
