//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
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

    //Get length of binary strings and previous string from the source registers
    val length = Reg(io.cmd.rs1) //Length of binary string
    val previous = Reg(io.cmd.rs2) //Previous
    //Output the new string to the same register containing the previous string
    val rd = Reg(io.cmd.inst.rs2) //Output location

    //Busy when not ready for a new instruction
    io.busy := ~io.cmd.ready

    //When a new command is received, capture inputs and become busy
    when(io.cmd.fire()) {
        io.cmd.ready := 0.U
        length := io.cmd.rs1
        previous := io.cmd.rs2
        rd := io.cmd.inst.rs2
    }

    when(io.resp.fire()) {
        io.cmd.ready := 1.U
    }

    //response
    io.resp.valid :=
    io.resp.bits.rd := rd
    io.resp.bits.data :=
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
