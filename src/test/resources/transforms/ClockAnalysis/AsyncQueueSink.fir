; generated from chipyard
circuit AsyncQueueSink :
  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_1 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_2 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_3 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerShiftReg_w4_d3_i0 :
    input clock : Clock
    input reset : Reset
    output io : {flip d : UInt<4>, q : UInt<4>}

    node _output_T = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_1 = bits(io.d, 0, 0) @[SynchronizerReg.scala 87:41]
    inst output_chain of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0 @[ShiftReg.scala 45:23]
    output_chain.clock <= clock
    output_chain.reset <= _output_T
    output_chain.io.d <= _output_T_1 @[ShiftReg.scala 47:16]
    wire output_0 : UInt<1> @[ShiftReg.scala 48:24]
    output_0 <= output_chain.io.q @[ShiftReg.scala 48:24]
    node _output_T_2 = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_3 = bits(io.d, 1, 1) @[SynchronizerReg.scala 87:41]
    inst output_chain_1 of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_1 @[ShiftReg.scala 45:23]
    output_chain_1.clock <= clock
    output_chain_1.reset <= _output_T_2
    output_chain_1.io.d <= _output_T_3 @[ShiftReg.scala 47:16]
    wire output_1 : UInt<1> @[ShiftReg.scala 48:24]
    output_1 <= output_chain_1.io.q @[ShiftReg.scala 48:24]
    node _output_T_4 = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_5 = bits(io.d, 2, 2) @[SynchronizerReg.scala 87:41]
    inst output_chain_2 of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_2 @[ShiftReg.scala 45:23]
    output_chain_2.clock <= clock
    output_chain_2.reset <= _output_T_4
    output_chain_2.io.d <= _output_T_5 @[ShiftReg.scala 47:16]
    wire output_2 : UInt<1> @[ShiftReg.scala 48:24]
    output_2 <= output_chain_2.io.q @[ShiftReg.scala 48:24]
    node _output_T_6 = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_7 = bits(io.d, 3, 3) @[SynchronizerReg.scala 87:41]
    inst output_chain_3 of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_3 @[ShiftReg.scala 45:23]
    output_chain_3.clock <= clock
    output_chain_3.reset <= _output_T_6
    output_chain_3.io.d <= _output_T_7 @[ShiftReg.scala 47:16]
    wire output_3 : UInt<1> @[ShiftReg.scala 48:24]
    output_3 <= output_chain_3.io.q @[ShiftReg.scala 48:24]
    node io_q_lo = cat(output_1, output_0) @[Cat.scala 30:58]
    node io_q_hi = cat(output_3, output_2) @[Cat.scala 30:58]
    node _io_q_T = cat(io_q_hi, io_q_lo) @[Cat.scala 30:58]
    io.q <= _io_q_T @[SynchronizerReg.scala 90:8]

  module ClockCrossingReg_w121 :
    input clock : Clock
    input reset : Reset
    output io : {flip d : UInt<121>, q : UInt<121>, flip en : UInt<1>}

    reg cdc_reg : UInt<121>, clock @[Reg.scala 15:16]
    when io.en : @[Reg.scala 16:19]
      cdc_reg <= io.d @[Reg.scala 16:23]
      skip @[Reg.scala 16:19]
    io.q <= cdc_reg @[SynchronizerReg.scala 202:8]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_4 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerShiftReg_w1_d3_i0 :
    input clock : Clock
    input reset : Reset
    output io : {flip d : UInt<1>, q : UInt<1>}

    node _output_T = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_1 = bits(io.d, 0, 0) @[SynchronizerReg.scala 87:41]
    inst output_chain of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_4 @[ShiftReg.scala 45:23]
    output_chain.clock <= clock
    output_chain.reset <= _output_T
    output_chain.io.d <= _output_T_1 @[ShiftReg.scala 47:16]
    wire output_0 : UInt<1> @[ShiftReg.scala 48:24]
    output_0 <= output_chain.io.q @[ShiftReg.scala 48:24]
    io.q <= output_0 @[SynchronizerReg.scala 90:8]

  module AsyncValidSync :
    output io : {flip in : UInt<1>, out : UInt<1>}
    input clock : Clock
    input reset : AsyncReset

    inst io_out_sink_valid_0 of AsyncResetSynchronizerShiftReg_w1_d3_i0 @[ShiftReg.scala 45:23]
    io_out_sink_valid_0.clock <= clock
    io_out_sink_valid_0.reset <= reset
    io_out_sink_valid_0.io.d <= io.in @[ShiftReg.scala 47:16]
    wire _io_out_WIRE : UInt<1> @[ShiftReg.scala 48:24]
    _io_out_WIRE <= io_out_sink_valid_0.io.q @[ShiftReg.scala 48:24]
    io.out <= _io_out_WIRE @[AsyncQueue.scala 66:12]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_5 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerShiftReg_w1_d3_i0_1 :
    input clock : Clock
    input reset : Reset
    output io : {flip d : UInt<1>, q : UInt<1>}

    node _output_T = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_1 = bits(io.d, 0, 0) @[SynchronizerReg.scala 87:41]
    inst output_chain of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_5 @[ShiftReg.scala 45:23]
    output_chain.clock <= clock
    output_chain.reset <= _output_T
    output_chain.io.d <= _output_T_1 @[ShiftReg.scala 47:16]
    wire output_0 : UInt<1> @[ShiftReg.scala 48:24]
    output_0 <= output_chain.io.q @[ShiftReg.scala 48:24]
    io.q <= output_0 @[SynchronizerReg.scala 90:8]

  module AsyncValidSync_1 :
    output io : {flip in : UInt<1>, out : UInt<1>}
    input clock : Clock
    input reset : AsyncReset

    inst io_out_sink_valid_1 of AsyncResetSynchronizerShiftReg_w1_d3_i0_1 @[ShiftReg.scala 45:23]
    io_out_sink_valid_1.clock <= clock
    io_out_sink_valid_1.reset <= reset
    io_out_sink_valid_1.io.d <= io.in @[ShiftReg.scala 47:16]
    wire _io_out_WIRE : UInt<1> @[ShiftReg.scala 48:24]
    _io_out_WIRE <= io_out_sink_valid_1.io.q @[ShiftReg.scala 48:24]
    io.out <= _io_out_WIRE @[AsyncQueue.scala 66:12]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_6 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerShiftReg_w1_d3_i0_2 :
    input clock : Clock
    input reset : Reset
    output io : {flip d : UInt<1>, q : UInt<1>}

    node _output_T = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_1 = bits(io.d, 0, 0) @[SynchronizerReg.scala 87:41]
    inst output_chain of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_6 @[ShiftReg.scala 45:23]
    output_chain.clock <= clock
    output_chain.reset <= _output_T
    output_chain.io.d <= _output_T_1 @[ShiftReg.scala 47:16]
    wire output_0 : UInt<1> @[ShiftReg.scala 48:24]
    output_0 <= output_chain.io.q @[ShiftReg.scala 48:24]
    io.q <= output_0 @[SynchronizerReg.scala 90:8]

  module AsyncValidSync_2 :
    output io : {flip in : UInt<1>, out : UInt<1>}
    input clock : Clock
    input reset : AsyncReset

    inst io_out_source_extend of AsyncResetSynchronizerShiftReg_w1_d3_i0_2 @[ShiftReg.scala 45:23]
    io_out_source_extend.clock <= clock
    io_out_source_extend.reset <= reset
    io_out_source_extend.io.d <= io.in @[ShiftReg.scala 47:16]
    wire _io_out_WIRE : UInt<1> @[ShiftReg.scala 48:24]
    _io_out_WIRE <= io_out_source_extend.io.q @[ShiftReg.scala 48:24]
    io.out <= _io_out_WIRE @[AsyncQueue.scala 66:12]

  module AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_7 :
    input clock : Clock
    input reset : AsyncReset
    output io : {flip d : UInt<1>, q : UInt<1>}

    reg sync_0 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_1 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    reg sync_2 : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[SynchronizerReg.scala 51:87]
    node _sync_2_T = bits(io.d, 0, 0) @[SynchronizerReg.scala 54:22]
    sync_2 <= _sync_2_T @[SynchronizerReg.scala 54:14]
    sync_0 <= sync_1 @[SynchronizerReg.scala 57:10]
    sync_1 <= sync_2 @[SynchronizerReg.scala 57:10]
    io.q <= sync_0 @[SynchronizerReg.scala 59:8]

  module AsyncResetSynchronizerShiftReg_w1_d3_i0_3 :
    input clock : Clock
    input reset : Reset
    output io : {flip d : UInt<1>, q : UInt<1>}

    node _output_T = asAsyncReset(reset) @[SynchronizerReg.scala 86:21]
    node _output_T_1 = bits(io.d, 0, 0) @[SynchronizerReg.scala 87:41]
    inst output_chain of AsyncResetSynchronizerPrimitiveShiftReg_d3_i0_7 @[ShiftReg.scala 45:23]
    output_chain.clock <= clock
    output_chain.reset <= _output_T
    output_chain.io.d <= _output_T_1 @[ShiftReg.scala 47:16]
    wire output_0 : UInt<1> @[ShiftReg.scala 48:24]
    output_0 <= output_chain.io.q @[ShiftReg.scala 48:24]
    io.q <= output_0 @[SynchronizerReg.scala 90:8]

  module AsyncValidSync_3 :
    output io : {flip in : UInt<1>, out : UInt<1>}
    input clock : Clock
    input reset : AsyncReset

    inst io_out_source_valid of AsyncResetSynchronizerShiftReg_w1_d3_i0_3 @[ShiftReg.scala 45:23]
    io_out_source_valid.clock <= clock
    io_out_source_valid.reset <= reset
    io_out_source_valid.io.d <= io.in @[ShiftReg.scala 47:16]
    wire _io_out_WIRE : UInt<1> @[ShiftReg.scala 48:24]
    _io_out_WIRE <= io_out_source_valid.io.q @[ShiftReg.scala 48:24]
    io.out <= _io_out_WIRE @[AsyncQueue.scala 66:12]

  module AsyncQueueSink :
    input clock : Clock
    input reset : UInt<1>
    output io : {deq : {flip ready : UInt<1>, valid : UInt<1>, bits : {opcode : UInt<3>, param : UInt<3>, size : UInt<3>, source : UInt<4>, address : UInt<35>, user : {}, echo : {}, mask : UInt<8>, data : UInt<64>, corrupt : UInt<1>}}, flip async : {mem : {opcode : UInt<3>, param : UInt<3>, size : UInt<3>, source : UInt<4>, address : UInt<35>, user : {}, echo : {}, mask : UInt<8>, data : UInt<64>, corrupt : UInt<1>}[8], flip ridx : UInt<4>, widx : UInt<4>, safe : {flip ridx_valid : UInt<1>, widx_valid : UInt<1>, source_reset_n : UInt<1>, flip sink_reset_n : UInt<1>}}}

    wire source_ready : UInt<1>
    source_ready <= UInt<1>("h01")
    node _ridx_T = asAsyncReset(reset) @[AsyncQueue.scala 144:30]
    node _ridx_T_1 = and(io.deq.ready, io.deq.valid) @[Decoupled.scala 40:37]
    node _ridx_T_2 = eq(source_ready, UInt<1>("h00")) @[AsyncQueue.scala 144:79]
    wire ridx_incremented : UInt<4> @[AsyncQueue.scala 51:27]
    reg ridx_ridx_bin : UInt, clock with : (reset => (_ridx_T, UInt<1>("h00"))) @[AsyncQueue.scala 52:25]
    ridx_ridx_bin <= ridx_incremented @[AsyncQueue.scala 52:25]
    node _ridx_incremented_T = add(ridx_ridx_bin, _ridx_T_1) @[AsyncQueue.scala 53:43]
    node _ridx_incremented_T_1 = tail(_ridx_incremented_T, 1) @[AsyncQueue.scala 53:43]
    node _ridx_incremented_T_2 = mux(_ridx_T_2, UInt<1>("h00"), _ridx_incremented_T_1) @[AsyncQueue.scala 53:23]
    ridx_incremented <= _ridx_incremented_T_2 @[AsyncQueue.scala 53:17]
    node _ridx_T_3 = shr(ridx_incremented, 1) @[AsyncQueue.scala 54:32]
    node ridx = xor(ridx_incremented, _ridx_T_3) @[AsyncQueue.scala 54:17]
    inst widx_widx_gray of AsyncResetSynchronizerShiftReg_w4_d3_i0 @[ShiftReg.scala 45:23]
    widx_widx_gray.clock <= clock
    widx_widx_gray.reset <= reset
    widx_widx_gray.io.d <= io.async.widx @[ShiftReg.scala 47:16]
    wire widx : UInt<4> @[ShiftReg.scala 48:24]
    widx <= widx_widx_gray.io.q @[ShiftReg.scala 48:24]
    node _valid_T = neq(ridx, widx) @[AsyncQueue.scala 146:36]
    node valid = and(source_ready, _valid_T) @[AsyncQueue.scala 146:28]
    node _index_T = bits(ridx, 2, 0) @[AsyncQueue.scala 152:43]
    node _index_T_1 = bits(ridx, 3, 3) @[AsyncQueue.scala 152:62]
    node _index_T_2 = shl(_index_T_1, 2) @[AsyncQueue.scala 152:75]
    node index = xor(_index_T, _index_T_2) @[AsyncQueue.scala 152:55]
    inst io_deq_bits_deq_bits_reg of ClockCrossingReg_w121 @[SynchronizerReg.scala 207:25]
    io_deq_bits_deq_bits_reg.clock <= clock
    io_deq_bits_deq_bits_reg.reset <= reset
    node io_deq_bits_deq_bits_reg_io_d_lo_lo = cat(io.async.mem[index].data, io.async.mem[index].corrupt) @[SynchronizerReg.scala 209:24]
    node io_deq_bits_deq_bits_reg_io_d_lo_hi = cat(io.async.mem[index].address, io.async.mem[index].mask) @[SynchronizerReg.scala 209:24]
    node io_deq_bits_deq_bits_reg_io_d_lo = cat(io_deq_bits_deq_bits_reg_io_d_lo_hi, io_deq_bits_deq_bits_reg_io_d_lo_lo) @[SynchronizerReg.scala 209:24]
    node io_deq_bits_deq_bits_reg_io_d_hi_lo = cat(io.async.mem[index].size, io.async.mem[index].source) @[SynchronizerReg.scala 209:24]
    node io_deq_bits_deq_bits_reg_io_d_hi_hi = cat(io.async.mem[index].opcode, io.async.mem[index].param) @[SynchronizerReg.scala 209:24]
    node io_deq_bits_deq_bits_reg_io_d_hi = cat(io_deq_bits_deq_bits_reg_io_d_hi_hi, io_deq_bits_deq_bits_reg_io_d_hi_lo) @[SynchronizerReg.scala 209:24]
    node _io_deq_bits_deq_bits_reg_io_d_T = cat(io_deq_bits_deq_bits_reg_io_d_hi, io_deq_bits_deq_bits_reg_io_d_lo) @[SynchronizerReg.scala 209:24]
    io_deq_bits_deq_bits_reg.io.d <= _io_deq_bits_deq_bits_reg_io_d_T @[SynchronizerReg.scala 209:18]
    io_deq_bits_deq_bits_reg.io.en <= valid @[SynchronizerReg.scala 210:19]
    wire _io_deq_bits_WIRE : {opcode : UInt<3>, param : UInt<3>, size : UInt<3>, source : UInt<4>, address : UInt<35>, user : {}, echo : {}, mask : UInt<8>, data : UInt<64>, corrupt : UInt<1>} @[SynchronizerReg.scala 211:26]
    wire _io_deq_bits_WIRE_1 : UInt<121>
    _io_deq_bits_WIRE_1 <= io_deq_bits_deq_bits_reg.io.q
    node _io_deq_bits_T = bits(_io_deq_bits_WIRE_1, 0, 0) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.corrupt <= _io_deq_bits_T @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_1 = bits(_io_deq_bits_WIRE_1, 64, 1) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.data <= _io_deq_bits_T_1 @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_2 = bits(_io_deq_bits_WIRE_1, 72, 65) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.mask <= _io_deq_bits_T_2 @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_3 = bits(_io_deq_bits_WIRE_1, 107, 73) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.address <= _io_deq_bits_T_3 @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_4 = bits(_io_deq_bits_WIRE_1, 111, 108) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.source <= _io_deq_bits_T_4 @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_5 = bits(_io_deq_bits_WIRE_1, 114, 112) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.size <= _io_deq_bits_T_5 @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_6 = bits(_io_deq_bits_WIRE_1, 117, 115) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.param <= _io_deq_bits_T_6 @[SynchronizerReg.scala 211:26]
    node _io_deq_bits_T_7 = bits(_io_deq_bits_WIRE_1, 120, 118) @[SynchronizerReg.scala 211:26]
    _io_deq_bits_WIRE.opcode <= _io_deq_bits_T_7 @[SynchronizerReg.scala 211:26]
    io.deq.bits.corrupt <= _io_deq_bits_WIRE.corrupt @[AsyncQueue.scala 159:15]
    io.deq.bits.data <= _io_deq_bits_WIRE.data @[AsyncQueue.scala 159:15]
    io.deq.bits.mask <= _io_deq_bits_WIRE.mask @[AsyncQueue.scala 159:15]
    io.deq.bits.address <= _io_deq_bits_WIRE.address @[AsyncQueue.scala 159:15]
    io.deq.bits.source <= _io_deq_bits_WIRE.source @[AsyncQueue.scala 159:15]
    io.deq.bits.size <= _io_deq_bits_WIRE.size @[AsyncQueue.scala 159:15]
    io.deq.bits.param <= _io_deq_bits_WIRE.param @[AsyncQueue.scala 159:15]
    io.deq.bits.opcode <= _io_deq_bits_WIRE.opcode @[AsyncQueue.scala 159:15]
    node _valid_reg_T = asAsyncReset(reset) @[AsyncQueue.scala 161:35]
    reg valid_reg : UInt<1>, clock with : (reset => (_valid_reg_T, UInt<1>("h00"))) @[AsyncQueue.scala 161:56]
    valid_reg <= valid @[AsyncQueue.scala 161:56]
    node _io_deq_valid_T = and(valid_reg, source_ready) @[AsyncQueue.scala 162:29]
    io.deq.valid <= _io_deq_valid_T @[AsyncQueue.scala 162:16]
    node _ridx_reg_T = asAsyncReset(reset) @[AsyncQueue.scala 164:34]
    reg ridx_gray : UInt, clock with : (reset => (_ridx_reg_T, UInt<1>("h00"))) @[AsyncQueue.scala 164:55]
    ridx_gray <= ridx @[AsyncQueue.scala 164:55]
    io.async.ridx <= ridx_gray @[AsyncQueue.scala 165:17]
    inst sink_valid_0 of AsyncValidSync @[AsyncQueue.scala 168:33]
    inst sink_valid_1 of AsyncValidSync_1 @[AsyncQueue.scala 169:33]
    inst source_extend of AsyncValidSync_2 @[AsyncQueue.scala 171:31]
    inst source_valid of AsyncValidSync_3 @[AsyncQueue.scala 172:31]
    node _sink_valid_0_reset_T = asUInt(reset) @[AsyncQueue.scala 173:35]
    node _sink_valid_0_reset_T_1 = eq(io.async.safe.source_reset_n, UInt<1>("h00")) @[AsyncQueue.scala 173:45]
    node _sink_valid_0_reset_T_2 = or(_sink_valid_0_reset_T, _sink_valid_0_reset_T_1) @[AsyncQueue.scala 173:42]
    node _sink_valid_0_reset_T_3 = asAsyncReset(_sink_valid_0_reset_T_2) @[AsyncQueue.scala 173:66]
    sink_valid_0.reset <= _sink_valid_0_reset_T_3 @[AsyncQueue.scala 173:25]
    node _sink_valid_1_reset_T = asUInt(reset) @[AsyncQueue.scala 174:35]
    node _sink_valid_1_reset_T_1 = eq(io.async.safe.source_reset_n, UInt<1>("h00")) @[AsyncQueue.scala 174:45]
    node _sink_valid_1_reset_T_2 = or(_sink_valid_1_reset_T, _sink_valid_1_reset_T_1) @[AsyncQueue.scala 174:42]
    node _sink_valid_1_reset_T_3 = asAsyncReset(_sink_valid_1_reset_T_2) @[AsyncQueue.scala 174:66]
    sink_valid_1.reset <= _sink_valid_1_reset_T_3 @[AsyncQueue.scala 174:25]
    node _source_extend_reset_T = asUInt(reset) @[AsyncQueue.scala 175:35]
    node _source_extend_reset_T_1 = eq(io.async.safe.source_reset_n, UInt<1>("h00")) @[AsyncQueue.scala 175:45]
    node _source_extend_reset_T_2 = or(_source_extend_reset_T, _source_extend_reset_T_1) @[AsyncQueue.scala 175:42]
    node _source_extend_reset_T_3 = asAsyncReset(_source_extend_reset_T_2) @[AsyncQueue.scala 175:66]
    source_extend.reset <= _source_extend_reset_T_3 @[AsyncQueue.scala 175:25]
    node _source_valid_reset_T = asAsyncReset(reset) @[AsyncQueue.scala 176:34]
    source_valid.reset <= _source_valid_reset_T @[AsyncQueue.scala 176:25]
    sink_valid_0.clock <= clock @[AsyncQueue.scala 178:25]
    sink_valid_1.clock <= clock @[AsyncQueue.scala 179:25]
    source_extend.clock <= clock @[AsyncQueue.scala 180:25]
    source_valid.clock <= clock @[AsyncQueue.scala 181:25]
    sink_valid_0.io.in <= UInt<1>("h01") @[AsyncQueue.scala 183:24]
    sink_valid_1.io.in <= sink_valid_0.io.out @[AsyncQueue.scala 184:24]
    io.async.safe.ridx_valid <= sink_valid_1.io.out @[AsyncQueue.scala 185:20]
    source_extend.io.in <= io.async.safe.widx_valid @[AsyncQueue.scala 186:25]
    source_valid.io.in <= source_extend.io.out @[AsyncQueue.scala 187:24]
    source_ready <= source_valid.io.out @[AsyncQueue.scala 188:18]
    node _io_async_safe_sink_reset_n_T = asUInt(reset) @[AsyncQueue.scala 189:32]
    node _io_async_safe_sink_reset_n_T_1 = eq(_io_async_safe_sink_reset_n_T, UInt<1>("h00")) @[AsyncQueue.scala 189:25]
    io.async.safe.sink_reset_n <= _io_async_safe_sink_reset_n_T_1 @[AsyncQueue.scala 189:22]