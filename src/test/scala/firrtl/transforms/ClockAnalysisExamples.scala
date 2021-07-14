// SPDX-License-Identifier: Apache-2.0

package firrtl.transforms

import firrtl.annotations.JsonProtocol
import firrtl.{AnnotationSeq, FileUtils}

/** Collection of example circuits used for testing both the [[ClockAndResetTreeAnalysisPass]] and the [[ClockDomainAnalysisPass]] */
object ClockAnalysisExamples {
  /* Original Chisel:
  class Inverter extends Module {
    val io = IO(new Bundle {
      val in = Input(Bool())
      val out = Output(Bool())
    })
    io.out := RegNext(~io.in)
  }
   */
  val inverter =
    """circuit Inverter :
      |  module Inverter :
      |    input clock : Clock
      |    input reset : UInt<1>
      |    output io : { flip in : UInt<1>, out : UInt<1>}
      |
      |    node _T = not(io.in) @[main.scala 12:21]
      |    reg REG : UInt<1>, clock with :
      |      reset => (UInt<1>("h0"), REG) @[main.scala 12:20]
      |    REG <= _T @[main.scala 12:20]
      |    io.out <= REG @[main.scala 12:10]
      |""".stripMargin

  /*
  class InverterWithReset extends Module {
    val io = IO(new Bundle {
      val in = Input(Bool())
      val out = Output(Bool())
    })
    val inverted = RegInit(false.B)
    inverted := ~io.in
    io.out := inverted
  }
   */
  val inverterWithReset =
    """circuit InverterWithReset :
      |  module InverterWithReset :
      |    input clock : Clock
      |    input reset : UInt<1>
      |    output io : { flip in : UInt<1>, out : UInt<1>}
      |
      |    reg inverted : UInt<1>, clock with :
      |      reset => (reset, UInt<1>("h0")) @[main.scala 11:25]
      |    node _T = not(io.in) @[main.scala 12:15]
      |    inverted <= _T @[main.scala 12:12]
      |    io.out <= inverted @[main.scala 13:10]
      |
      |""".stripMargin

  val sameModuleDifferentNumberOfClocks =
    """circuit SameModuleDifferentNumberOfClocks:
      |  module Child:
      |    input clock : Clock
      |    input reset : Reset
      |    input in0: UInt<8>
      |    output out0: UInt<8>
      |    output out1: UInt<8>
      |
      |    ; this signal is just wired through this module, but not actually clocked by it
      |    out0 <= in0
      |
      |    reg counter : UInt<8>, clock with :
      |      reset => (reset, UInt<8>(0))
      |    counter <= add(counter, UInt(1))
      |    ; this signal on the other hand is always under the domain of our clock
      |    out1 <= counter
      |
      |  module SameModuleDifferentNumberOfClocks:
      |    input reset: AsyncReset
      |    input clockA: Clock
      |    input clockB: Clock
      |    input in0: UInt<8>
      |    input in1: UInt<8>
      |    output out0: UInt<8>
      |    output out1: UInt<8>
      |    output out2: UInt<8>
      |    output out3: UInt<8>
      |
      |    ; we have one register for each clock domain
      |    reg r0 : UInt<8>, clockA
      |    r0 <= in0
      |    reg r1 : UInt<8>, clockB
      |    r1 <= in1
      |
      |    ; c0 will only have a single clock
      |    inst c0 of Child
      |    c0.clock <= clockA
      |    c0.reset <= reset
      |    c0.in0 <= r0
      |    out0 <= c0.out0
      |    out1 <= c0.out1
      |
      |    ; c1 is clocked by clockA, however its input is driven by clockB
      |    inst c1 of Child
      |    c1.clock <= clockA
      |    c1.reset <= reset
      |    c1.in0 <= r1
      |    out2 <= c1.out0
      |    out3 <= c1.out1
      |""".stripMargin

  val clockDiv =
    """circuit ClockDiv:
      |  module Divider:
      |    input clockIn : Clock
      |    output clockOut : Clock
      |    reg cReg : UInt<1>, clockIn
      |    cReg <= not(cReg)
      |    clockOut <= asClock(cReg)
      |  module ClockDiv:
      |    input reset : AsyncReset   ; unused reset input as chisel would generate
      |    input clock : Clock
      |    input in : UInt<8>
      |    output out0 : UInt<8>
      |    output out1 : UInt<8>
      |
      |    reg out0Reg : UInt<8>, clock
      |    out0Reg <= in
      |    out0 <= out0Reg
      |
      |    inst divider of Divider
      |    divider.clockIn <= clock
      |    reg out1Reg : UInt<8>, divider.clockOut
      |    out1Reg <= in
      |    out1 <= out1Reg
      |""".stripMargin

  val internalClockDiv =
    """circuit InternalClockDiv:
      |  module Divider:
      |    input clockIn : Clock
      |    output clockOut : Clock
      |    reg cReg : UInt<1>, clockIn
      |    cReg <= not(cReg)
      |    clockOut <= asClock(cReg)
      |  module InternalClockDiv:
      |    input reset : AsyncReset   ; unused reset input as chisel would generate
      |    input clock : Clock
      |    input in : UInt<8>
      |    output out0 : UInt<8>
      |    output out1 : UInt<8>
      |
      |    reg out0Reg : UInt<8>, clock
      |    out0Reg <= in
      |    out0 <= out0Reg
      |
      |    reg cReg : UInt<1>, clock
      |    cReg <= not(cReg)
      |    reg out1Reg : UInt<8>, asClock(cReg)
      |    out1Reg <= in
      |    out1 <= out1Reg
      |""".stripMargin

  val passThroughSideways =
    """circuit PassThrough:
      |  module Pass:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    out <= in
      |  module PassThrough:
      |    input reset : AsyncReset   ; unused reset input as chisel would generate
      |    input clock : Clock
      |    input in : UInt<8>
      |    output out0 : UInt<8>
      |
      |    inst p0 of Pass
      |    inst p1 of Pass
      |    inst p2 of Pass
      |    inst p3 of Pass
      |    inst p4 of Pass
      |    inst p5 of Pass
      |    inst p6 of Pass
      |    inst p7 of Pass
      |    inst p8 of Pass
      |    inst p9 of Pass
      |    p0.in <= asUInt(clock)
      |    p1.in <= p0.out
      |    p2.in <= p1.out
      |    p3.in <= p2.out
      |    p4.in <= p3.out
      |    p5.in <= p4.out
      |    p6.in <= p5.out
      |    p7.in <= p6.out
      |    p8.in <= p7.out
      |    p9.in <= p8.out
      |    node clk = asClock(p9.out)
      |
      |    reg out0Reg : UInt<8>, clk
      |    out0Reg <= in
      |    out0 <= out0Reg
      |""".stripMargin

  val passThroughVertical =
    """circuit PassThrough:
      |  module Pass0:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    out <= in
      |  module Pass1:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass0
      |    p.in <= in
      |    out <= p.out
      |  module Pass2:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass1
      |    p.in <= in
      |    out <= p.out
      |  module Pass3:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass2
      |    p.in <= in
      |    out <= p.out
      |  module Pass4:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass3
      |    p.in <= in
      |    out <= p.out
      |  module Pass5:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass4
      |    p.in <= in
      |    out <= p.out
      |  module PassThrough:
      |    input reset : AsyncReset   ; unused reset input as chisel would generate
      |    input clock : Clock
      |    input in : UInt<8>
      |    output out0 : UInt<8>
      |
      |    inst p of Pass5
      |    p.in <= asUInt(clock)
      |    node clk = asClock(p.out)
      |
      |    reg out0Reg : UInt<8>, clk
      |    out0Reg <= in
      |    out0 <= out0Reg
      |""".stripMargin

  val passThroughVerticalReg =
    """circuit PassThrough:
      |  module Pass0:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    out <= in
      |    reg r: UInt<1>, asClock(in)
      |    r <= not(r)
      |  module Pass1:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass0
      |    p.in <= in
      |    out <= p.out
      |  module Pass2:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass1
      |    p.in <= in
      |    out <= p.out
      |  module Pass3:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass2
      |    p.in <= in
      |    out <= p.out
      |  module Pass4:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass3
      |    p.in <= in
      |    out <= p.out
      |  module Pass5:
      |    input in : UInt<1>
      |    output out : UInt<1>
      |    inst p of Pass4
      |    p.in <= in
      |    out <= p.out
      |  module PassThrough:
      |    input reset : AsyncReset   ; unused reset input as chisel would generate
      |    input clock : Clock
      |    input in : UInt<8>
      |    output out0 : UInt<8>
      |
      |    inst p of Pass5
      |    p.in <= asUInt(clock)
      |    node clk = asClock(p.out)
      |
      |    reg out0Reg : UInt<8>, clk
      |    out0Reg <= in
      |    out0 <= out0Reg
      |""".stripMargin

  val invertedClock =
    """circuit InvertedClock:
      |  module InvertedClock:
      |    input reset : AsyncReset   ; unused reset input as chisel would generate
      |    input clock : Clock
      |    input in : UInt<8>
      |    output out0 : UInt<8>
      |
      |    wire clock2 : UInt<1>
      |    wire clock3 : Clock
      |    node clock0 = asUInt(clock)
      |    node clock1 = not(clock0)
      |    clock3 <= asClock(clock2)
      |    clock2 <= clock1
      |
      |    ; @posedge clock
      |    reg r0 : UInt<8>, clock
      |    r0 <= in
      |    ; @negedge clock
      |    ; TODO: support inversion at the register
      |    ;reg r1 : UInt<8>, asClock(not(asUInt(clock)))
      |    reg r1 : UInt<8>, clock
      |    r1 <= in
      |    ; @posedge clock
      |    reg r2 : UInt<8>, asClock(not(asUInt(asClock(not(asUInt(clock))))))
      |    r2 <= in
      |    ; @posedge clock
      |    reg r3 : UInt<8>, asClock(not(not(asUInt(clock))))
      |    r3 <= in
      |    ; @posedge clock
      |    reg r4 : UInt<8>, asClock(clock0)
      |    r4 <= in
      |    ; @negedge clock
      |    reg r5 : UInt<8>, asClock(clock1)
      |    r5 <= in
      |    ; @negedge clock
      |    reg r6 : UInt<8>, asClock(clock2)
      |    r6 <= in
      |    ; @negedge clock
      |    reg r7 : UInt<8>, asClock(clock3)
      |    r7 <= in
      |
      |    out0 <= xor(r0, xor(r1, xor(r2, xor(r3, xor(r4, xor(r5, xor(r6, r7)))))))
      |""".stripMargin

  def asyncQueueSink: String = FileUtils.getTextResource("/transforms/ClockAnalysis/AsyncQueueSink.fir")
  def iCache:         String = FileUtils.getTextResource("/regress/ICache.fir")
  def rocket:         String = FileUtils.getTextResource("/regress/RocketCore.fir")
  def firesimRocket:  String = FileUtils.getTextResource("/transforms/ClockAnalysis/FireSimRocketConfig.fir")
  def firesimRocketSingleClock: String =
    FileUtils.getTextResource("/transforms/ClockAnalysis/FireSimRocketConfig.SingleClock.fir")
}
