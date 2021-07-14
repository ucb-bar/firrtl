// SPDX-License-Identifier: Apache-2.0

package firrtl.transforms

import firrtl.annotations.CircuitTarget
import firrtl.options.Dependency
import firrtl.testutils.LeanTransformSpec

class ClockDomainAnalysisSpec extends LeanTransformSpec(Seq(Dependency(ClockDomainAnalysisPass))) {
  behavior.of("ClockDomainAnalysis")
  import ClockAnalysisExamples._

  it should "analyze a circuit with a single clock" ignore {
    val m = CircuitTarget("Inverter").module("Inverter")
    val state = compile(inverter)

    // there is only a single clock!
    assert(
      state.annotations.contains(
        SingleClockModule(m)
      )
    )
  }

  it should "analyze a circuit with a single clock and reset" ignore {
    val m = CircuitTarget("InverterWithReset").module("InverterWithReset")
    val state = compile(inverterWithReset)

    // there is only a single clock!
    assert(
      state.annotations.contains(
        SingleClockModule(m)
      )
    )
  }

  it should "analyze a clock going through multiple modules (sideways)" in {
    val m = CircuitTarget("PassThrough").module("PassThrough")
    val state = compile(passThroughSideways)

  }

  it should "analyze the iCache" in {
    compile(iCache)
  }

  it should "analyze Rocket Core" in {
    compile(rocket)
  }

  it should "analyze the AsyncQueueSink" in {
    compile(asyncQueueSink)
  }

  it should "analyze Rocket Chip generated for Firesim" ignore {
    compile(firesimRocket)
  }

  it should "analyze Rocket Chip generated for Firesim with a single clock" ignore {
    compile(firesimRocketSingleClock)
  }
}
