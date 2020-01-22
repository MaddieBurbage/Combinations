//Simple Combinational Accelerator, based on the fortyTwo accelerator template.
// (c) Maddie Burbage, 2020
package combinations

import Chisel._
import freechips.rocketchip.tile._ //For LazyRoCC
import freechips.rocketchip.config._ //For Config
import freechips.rocketchip.diplomacy._ //For LazyModule
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq} //For outward connections

//Wrapper for the accelerator
class Combinations(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
    override lazy val module = new CombinationsImp(this)
}

//Main accelerator class, directs instruction inputs to submodules for computation
class CombinationsImp(outer: Combinations)(implicit p: Parameters) extends LazyRoCCModuleImp(outer){
    //Accelerator States idle, busy (accessing memory), resp (sending response)
    val s_idle :: s_busy :: s_resp :: Nil = Enum(Bits(), 3)
    val state = Reg(init = s_idle) //state starts idle, is remembered
    val tryStore = state === s_busy

    //Instruction inputs
    val length = Reg(init = io.cmd.bits.rs1(4,0)) //Length of binary string
    val previous = Reg(init = io.cmd.bits.rs2) //Previous binary string
    val address = previous //Renamed for readability
    val rd = Reg(init = io.cmd.bits.inst.rd) //Output location
    val function = Reg(init = io.cmd.bits.inst.funct) //Specific operation

    //Submodules for functions: FixedWeight, Lexicographic, General, Ranged, CaptureWeights, Memory (functions 0-5)
    val captureWeights = Module(new CaptureWeights()(p))
    val outputs = Array(nextCombination.fixedWeight(length, previous), nextCombination.lexicographic(length, previous), nextCombination.generalCombinations(length, previous),
                        nextCombination.rangedCombinations(length, previous, captureWeights.io.minWeight, captureWeights.io.maxWeight))

    //Set up submodule inputs
    captureWeights.io.newMin := length
    captureWeights.io.newMax := previous
    captureWeights.io.reset := Mux(function === 3.U && io.resp.bits.data === ~(0.U(64.W)), 1.U, 0.U)
    captureWeights.io.set := Mux(function === 4.U, 1.U, 0.U)

    //Command and response setup
    io.cmd.ready := state === s_idle
    io.resp.valid := state === s_resp

    //Accelerator response
    val lookups = Array(0.U->outputs(0),1.U->outputs(1), 2.U->outputs(2),
        3.U->outputs(3), 4.U->captureWeights.io.success, 5.U->memoryOutputs)
    io.resp.bits.data := MuxLookup(function, outputs(0), lookups)
    io.resp.bits.rd := rd

    //Accelerator state control
    when(io.cmd.fire()) {
    	when(function > 4.U) {
    	    state := s_busy
            summedReturns := 0.U
            currentAddress := address
    	} .otherwise {
            state := s_resp
    	}
    	length := (io.cmd.bits.rs1(4,0))
    	previous := io.cmd.bits.rs2
    	rd := io.cmd.bits.inst.rd
    	function := io.cmd.bits.inst.funct
    }

    when(tryStore && finished) {
	    state := s_resp
    }

    when(io.resp.fire()) {
        state := s_idle
    }

    //Memory access attempt
    //Generate next general combination
    val combinationStream = memoryAccess.cycleCombinations(length, getNext)
    val summedReturns = Reg(UInt(64.W))
    val currentAddress = Reg(UInt(64.W))

    //Controls for memory acces
    val finished = result === Fill(64,1.U)
    val getNext = tryStore && !finished && !stallStore
    //Stalled if trying to send request but mem not ready
    val stallStore = tryStore && !io.mem.req.ready

    //Memory request interface
    io.mem.req.valid := tryStore
    io.busy := tryStore
    io.mem.req.bits.addr := address
    io.mem.req.bits.tag :=  combinationStream(9,0) //Change for out-of-order
    io.mem.req.bits.cmd := 0.U //change when actually storing to 1.U?
    io.mem.req.bits.data := Bits(0) //combinationStream
    io.mem.req.bits.size := log2Ceil(32).U
    io.mem.req.bits.signed := Bool(false)
    io.mem.req.bits.phys := Bool(false)

    when(io.mem.resp.valid) {
        summedReturns := summedReturns + io.mem.resp.bits.data
        currentAddress := currentAddress + 32.U
    }

    //Always false
    io.interrupt := Bool(false)
}



//Generates all binary strings based on an input and "saves" it to memory.
//Strings of length up to 32 work.
object MemoryAccess {
    def cycleCombinations(length: UInt, getNext: Bool) {
        val initial = Wire(UInt(32.W))
        initial := (1.U << io.length) - 1.U
        val nextSent = Reg(init = initial)
        val result = nextCombination.GeneralCombinations(length, nextSent)
        nextSent := Mux(getNext, result, nextSent)
    }
}

object nextCombination {
    //Generates a fixed-weight binary string based on a previous string of the same
    //weight and length. Binary strings up to length 32 will work.
    def fixedWeight(length: UInt, previous: UInt) = {
        //Calculations to generate the next combination
        val trimmed = previous & (previous + 1.U)
        val trailed = trimmed ^ (trimmed - 1.U)

        val indexShift = trailed + 1.U
        val indexTrailed = trailed & previous

        val subtracted = (indexShift & previous) - 1.U
        val fixed = Mux(subtracted.asSInt < 0.S, 0.U, subtracted)

        val result = previous + indexTrailed - fixed

        val stopper = 1.U(1.W) << length(4,0)

        //Fill result with all 1s if finished
        Mux(result >> length =/= 0.U, Fill(64,1.U), result % stopper)
    }

    //Generates the lexicographically-next binary string, up to a length of 32
    def lexicographic(length: UInt, previous: UInt) = {
        val result = previous + 1.U
        Mux(((result >>length) & 1.U) === 1.U, Fill(64,1.U), result)
    }

    //Generates the next binary string of a certain length based on the cool-er ordering
    def generalCombinations(length: UInt, previous: UInt) = {
        //Calculations
        //Mask up to the right-most '01' before the end of the string
        val trimmed = previous(31,1) | (previous(31,1) - 1.U) //Remove trailing 0s
        val trailed = trimmed ^ (trimmed + 1.U) //Make a mask for the right-most 01 onwards
        val mask = Wire(UInt(32.W)) //Shift the mask to a 32 bit wire instead of 31
        mask := (trailed << 1.U) + 1.U

        //Find the last spot in the mask, to use for rotating the 0th bit
        val lastTemp = Wire(UInt(32.W))
        lastTemp :=  trailed + 1.U //If there is a valid 01, this is the last bit
        val lastLimit = 1.U << (length(4,0) - 1.U) //Otherwise use the final bit
        val lastPosition = Mux(lastTemp > lastLimit || lastTemp === 0.U, lastLimit, lastTemp) //Choose which bit position to use

        val cap = 1.U << length(4,0) //One bit beyond the width of the string
        val first = Mux(mask < cap, 1.U & previous, 1.U & ~previous) //Flip the first bit if there is no valid 01
        val shifted = (previous & mask) >> 1.U //Shift the masked region
        val rotated = Mux(first === 1.U, shifted | lastPosition, shifted) //Move the first bit to the end of the shifting
        val result = rotated | (~mask & previous) //Combine the rotated and non-rotated parts of the string

        Mux(result === (cap - 1.U), Fill(64,1.U), result) //If finished, the result is all 1s
    }

    //Generates the next binary string within a weight range, based on cool-est ordering
    def rangedCombinations(length: UInt, previous: UInt, minWeight: UInt, maxWeight: UInt) = {
        //Calculations
        val trimmed = previous(31,1) | (previous(31,1) - 1.U)
        val trailed = trimmed ^ (trimmed + 1.U)
        val mask = Wire(UInt(32.W))
        mask := (trailed << 1.U) + 1.U

        val lastTemp = Wire(UInt(32.W))
        lastTemp :=  trailed + 1.U
        val lastLimit = 1.U << (length(4,0) - 1.U)
        val lastPosition = Mux(lastTemp > lastLimit || lastTemp === 0.U, lastLimit, lastTemp)

        val count = Wire(UInt(32.W))
	count := PopCount(previous(31,0))

        val cap = 1.U << length(4,0)
        val first = Mux(mask < cap, 1.U & previous, 1.U & ~previous)
        val shifted = (previous & mask) >> 1.U

        //Flip the bit while rotating if no 01 and new string is valid
        val rotated = Mux(first === 1.U && count <= maxWeight && count >= minWeight, shifted | lastPosition, shifted)
        val result = rotated | (~mask & previous)

        Mux(result === (cap - 1.U), Fill(64,1.U), result)
    }
}

//Stores the min and max weight used for ranged combinations (function 4). The
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


//Setup for the accelerator
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
