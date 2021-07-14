// SPDX-License-Identifier: Apache-2.0

package firrtl.transforms

import firrtl.options.Dependency
import firrtl.annotations._
import firrtl.testutils.LeanTransformSpec

class ClockAndResetTreeSpec extends LeanTransformSpec(Seq(Dependency(ClockAndResetTreeAnalysisPass))) {
  behavior.of("ClockAndResetTreeAnalysis")

  import ClockAnalysisExamples._

  it should "analyze a circuit with a single clock" in {
    val m = CircuitTarget("Inverter").module("Inverter")
    val state = compile(inverter)

    // there is exactly one register connected to the clock
    assert(state.annotations.contains(ClockSourceAnnotation(m.ref("clock"), 1)))
    assert(state.annotations.contains(ClockAnnotation(m.ref("clock"), source = "clock")))
  }

  it should "analyze a clock divider" in {
    val c = CircuitTarget("ClockDiv")
    val m = c.module("ClockDiv")
    val state = compile(clockDiv)

    // there are two source: 1) the clock input 2) the divided clock
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clock"), 2)
      )
    )
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.instOf("divider", "Divider").ref("cReg"), 1)
      )
    )

    // sinks
    assert(
      state.annotations.contains(
        ClockAnnotation(m.ref("clock"), "clock")
      )
    )
    // instance port and module ports get a separate annotation
    assert(
      state.annotations.contains(
        ClockAnnotation(m.instOf("divider", "Divider").ref("clockIn"), "clock")
      )
    )
    assert(
      state.annotations.contains(
        ClockAnnotation(c.module("Divider").ref("clockIn"), "clock")
      )
    )
    assert(
      state.annotations.contains(
        ClockAnnotation(m.instOf("divider", "Divider").ref("clockOut"), "divider.cReg")
      )
    )
    assert(
      state.annotations.contains(
        ClockAnnotation(c.module("Divider").ref("clockOut"), "divider.cReg")
      )
    )

  }

  it should "analyze an inlined clock divider" in {
    val m = CircuitTarget("InternalClockDiv").module("InternalClockDiv")
    val state = compile(internalClockDiv)

    // there are two source: 1) the clock input 2) the divided clock
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clock"), 2)
      )
    )
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("cReg"), 1)
      )
    )
  }

  it should "analyze a clock going through multiple modules (sideways)" in {
    val m = CircuitTarget("PassThrough").module("PassThrough")
    val state = compile(passThroughSideways)

    val clocks = state.annotations.collect { case a: ClockSourceAnnotation => a }
    assert(clocks == List(ClockSourceAnnotation(m.ref("clock"), 1)))
  }

  it should "analyze a clock going through multiple modules (vertical)" in {
    val m = CircuitTarget("PassThrough").module("PassThrough")
    val state = compile(passThroughVertical)

    val clocks = state.annotations.collect { case a: ClockSourceAnnotation => a }
    assert(clocks == List(ClockSourceAnnotation(m.ref("clock"), 1)))
  }

  it should "analyze a clock going through multiple modules (vertical) w/ internal reg" in {
    val m = CircuitTarget("PassThrough").module("PassThrough")
    val state = compile(passThroughVerticalReg)

    val clocks = state.annotations.collect { case a: ClockSourceAnnotation => a }
    assert(clocks == List(ClockSourceAnnotation(m.ref("clock"), 2)))
  }

  it should "analyze inverted clock signals" in {
    val m = CircuitTarget("InvertedClock").module("InvertedClock")
    val state = compile(invertedClock)

    val clocks = state.annotations.collect { case a: ClockSourceAnnotation => a }
    assert(clocks == List(ClockSourceAnnotation(m.ref("clock"), 8)))
  }

  it should "analyze a circuit with a single clock and reset" in {
    val m = CircuitTarget("InverterWithReset").module("InverterWithReset")
    val state = compile(inverterWithReset)

    // there is a normal Chisel reset and clock source in the toplevel
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clock"), 1)
      )
    )
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("reset"), 1)
      )
    )
  }

  it should "analyze a circuit with a different number of clock per instance" in {
    val c = CircuitTarget("SameModuleDifferentNumberOfClocks")
    val m = c.module("SameModuleDifferentNumberOfClocks")
    val state = compile(sameModuleDifferentNumberOfClocks)

    // we have two clock and one reset
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clockA"), 3)
      )
    )
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clockB"), 1)
      )
    )
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("reset"), 2)
      )
    )

    // the "clock" input of the "Child" module is always connected to clockA
    assert(
      state.annotations.contains(
        ClockAnnotation(c.module("Child").ref("clock"), "clockA")
      )
    )
  }

  it should "analyze the iCache" in {
    val m = CircuitTarget("ICache").module("ICache")
    val state = compile(iCache)

    // there is a normal Chisel reset and clock source in the toplevel
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clock"), 45)
      )
    )
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("reset"), 6)
      )
    )
  }

  it should "analyze Rocket Core" in {
    val m = CircuitTarget("RocketCore").module("RocketCore")
    val state = compile(rocket)

    // there is a normal Chisel reset and clock source in the toplevel
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clock"), 316)
      )
    )
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("reset"), 58)
      )
    )
  }

  it should "analyze the AsyncQueueSink" in {
    val m = CircuitTarget("AsyncQueueSink").module("AsyncQueueSink")
    val state = compile(asyncQueueSink)

    // there is only a single boring toplevel clock
    assert(
      state.annotations.contains(
        ClockSourceAnnotation(m.ref("clock"), 28)
      )
    )
    // there is also one boring toplevel reset
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("reset"), 18)
      )
    )

    // however there are multiple derived resets:
    //
    // node _sink_valid_0_reset_T_2 = or(_sink_valid_0_reset_T, _sink_valid_0_reset_T_1) @[AsyncQueue.scala 173:42]
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("_sink_valid_0_reset_T_2"), 3)
      )
    )
    // node _sink_valid_1_reset_T_2 = or(_sink_valid_1_reset_T, _sink_valid_1_reset_T_1) @[AsyncQueue.scala 174:42]
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("_sink_valid_1_reset_T_2"), 3)
      )
    )
    // node _source_extend_reset_T_2 = or(_source_extend_reset_T, _source_extend_reset_T_1) @[AsyncQueue.scala 175:42]
    assert(
      state.annotations.contains(
        ResetSourceAnnotation(m.ref("_source_extend_reset_T_2"), 3)
      )
    )
  }

  it should "analyze Rocket Chip generated for Firesim" in {
    val m = CircuitTarget("FireSim").module("FireSim")
    val state = compile(firesimRocket)

    val clocks = state.annotations.collect { case a: ClockSourceAnnotation => a }
    val resets = state.annotations.collect { case a: ResetSourceAnnotation => a }

    // there are two clock sources, both originating from the RationalClockBridge
    val clockBridge = m.instOf("RationalClockBridge", "RationalClockBridge")
    assert(clocks.contains(ClockSourceAnnotation(clockBridge.ref("clocks_0"), 6088)))
    assert(clocks.contains(ClockSourceAnnotation(clockBridge.ref("clocks_1"), 467)))
    assert(clocks.size == 2)

    // there are multiple reset in the design, many coming from the ClockGroupResetSynchronizer and AsyncQueues
    val peekPokeBridge = m.instOf("peekPokeBridge", "PeekPokeBridge")
    assert(resets.contains(ResetSourceAnnotation(peekPokeBridge.ref("reset"), 30)))
  }

  it should "analyze Rocket Chip generated for Firesim with a single clock" in {
    val m = CircuitTarget("FireSim").module("FireSim")
    val state = compile(firesimRocketSingleClock)

    val clocks = state.annotations.collect { case a: ClockSourceAnnotation => a }
    val resets = state.annotations.collect { case a: ResetSourceAnnotation => a }

    // there are two clock sources, both originating from the RationalClockBridge
    val clockBridge = m.instOf("RationalClockBridge", "RationalClockBridge")
    assert(clocks.contains(ClockSourceAnnotation(clockBridge.ref("clocks_0"), 6088 + 467)))
    assert(clocks.size == 1)

    // there are multiple reset in the design, many coming from the ClockGroupResetSynchronizer and AsyncQueues
    val peekPokeBridge = m.instOf("peekPokeBridge", "PeekPokeBridge")
    assert(resets.contains(ResetSourceAnnotation(peekPokeBridge.ref("reset"), 30)))
  }
}
