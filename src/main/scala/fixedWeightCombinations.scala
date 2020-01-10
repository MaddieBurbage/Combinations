//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
//Works for string lengths up to xLen - 1 (31? 63?)
// Maddie Burbage, 2020
package fixedWeightCombos

import Chisel._
import freechips.rocketchip.tile._ //For LazyRoCC
import freechips.rocketchip.config._ //For Config
import freechips.rocketchip.diplomacy._ //For LazyModule
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq} //something and level one cache

class FixedWeightCombos(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
    override lazy val module = new FixedWeightCombosImp(this)
}

class FixedWeightCombosImp(outer: FixedWeightCombos)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {

    val unset = ~(0.U)

    //Get length of binary strings and previous string from the source registers
    val length = Reg(unset) //Length of binary string
    val previous = Reg(unset) //Previous
    //Output the new string to the destination register
    val rd = Reg(io.cmd.bits.inst.rd) //Output location

    //Busy when not ready for a new instruction
    io.busy := ~io.cmd.ready

    //When a new command is received, capture inputs and become busy
    when(io.cmd.fire()) {
        io.cmd.ready := 0.U
        length := io.cmd.bits.rs1
        previous := io.cmd.bits.rs2
        rd := io.cmd.bits.inst.rd
        io.resp.valid := Bool(false)
    }

    //After responding, become ready for new commands
    when(io.resp.fire()) {
        io.cmd.ready := 1.U
        length := Reg(unset)
        previous := Reg(unset)
    }

    //Calculations
    val trimmed = previous & (previous + 1)
    val trailed = trimmed ^ (trimmed - 1)

    val indexShift = trailed + 1
    val indexTrailed = trailed & previous

    val subtracted = (indexShift & previous) - 1
    val fixed = Mux(SInt(subtracted) > 0, subtracted, 0.U) //make signed?

    val result = previous + indexTrailed - fixed

    //Response not ready until result value has changed from the default
    when(result =/= unset - 1) {
        io.resp.valid := Bool(true)
    }

    val stopper = 1 << length

    //Fill result with all 1s if finished, same response as divide by zero has
    when(result >> length === 0.U) {
        io.resp.bits.data := ~ (0.U)
    } .otherwise {
        io.resp.bits.data := result(length - 1, 0)
    }


    //Response
    io.resp.bits.rd := rd
    io.interrupt := Bool(false)
    io.mem.req.valid := Bool(false)

}

class WithFixedWeightCombos extends Config((site, here, up) => {
    case BuildRoCC => Seq((p: Parameters) => {
        val fixedWeightCombos = LazyModule.apply(new FixedWeightCombos(OpcodeSet.custom0) (p))
        fixedWeightCombos
    })
})

/**
 * Add this into the example project's RocketConfigs.scala file:
class FixedWeightCombosConfig extends Config(
    new WithTop ++
    new WithBootROM ++
    new freechips.rocketchip.subsystem.WithInclusiveCache ++
    new FixedWeightCombos.WithFixedWeightCombos ++
)

 */
