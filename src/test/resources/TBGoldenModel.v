// SPDX-License-Identifier: Apache-2.0
// Example VCS Command: $VCS_HOME/bin/vcs -debug_pp -full64 +define+UNIT_DELAY +rad +v2k +vcs+lic+wait +vc+list +vcs+initreg+random +vcs+dumpvars+out.vcd tb.v SimpleIOModule.v ...
`timescale 100ps / 10ps

`define CLK_PERIOD 1

`define HALF_CLK_PERIOD 0.5
`define RESET_TIME 5
`define INIT_TIME 5.5
`define expect(nodeName, nodeVal, expVal, cycle) if (nodeVal !== expVal) begin \
  $display("\t ASSERTION ON %s FAILED @ CYCLE = %d, 0x%h != EXPECTED 0x%h", \
  nodeName,cycle,nodeVal,expVal); $stop; end

module testbench_v;

  integer cycle = 0;

  reg clock = 1;
  reg reset = 1;
  reg signed [7:0] io_i_vF_0 = 0;
  reg signed [7:0] io_i_vF_1 = 0;
  reg signed [7:0] io_i_vF_2 = 0;
  reg signed [7:0] io_i_vF_3 = 0;
  reg signed [7:0] io_i_vF_4 = 0;
  reg signed [7:0] io_i_vF_5 = 0;
  reg signed [7:0] io_i_vF_6 = 0;
  reg signed [7:0] io_i_vF_7 = 0;
  reg signed [7:0] io_i_vF_8 = 0;
  reg signed [7:0] io_i_vF_9 = 0;
  reg signed [7:0] io_i_vS_0 = 0;
  reg signed [7:0] io_i_vS_1 = 0;
  reg signed [7:0] io_i_vS_2 = 0;
  reg signed [7:0] io_i_vS_3 = 0;
  reg signed [7:0] io_i_vS_4 = 0;
  reg signed [7:0] io_i_vS_5 = 0;
  reg signed [7:0] io_i_vS_6 = 0;
  reg signed [7:0] io_i_vS_7 = 0;
  reg signed [7:0] io_i_vS_8 = 0;
  reg signed [7:0] io_i_vS_9 = 0;
  reg[7:0] io_i_vU_0 = 0;
  reg[7:0] io_i_vU_1 = 0;
  reg[7:0] io_i_vU_2 = 0;
  reg[7:0] io_i_vU_3 = 0;
  reg[7:0] io_i_vU_4 = 0;
  reg[7:0] io_i_vU_5 = 0;
  reg[7:0] io_i_vU_6 = 0;
  reg[7:0] io_i_vU_7 = 0;
  reg[7:0] io_i_vU_8 = 0;
  reg[7:0] io_i_vU_9 = 0;
  reg[15:0] io_i_long_u = 0;
  reg signed [15:0] io_i_long_f = 0;
  reg signed [15:0] io_i_long_s = 0;
  reg signed [15:0] io_i_long_gen = 0;
  reg[7:0] io_i_short_u = 0;
  reg signed [7:0] io_i_short_f = 0;
  reg signed [7:0] io_i_short_s = 0;
  reg signed [7:0] io_i_short_gen = 0;
  reg signed [7:0] io_i_cFS_imag = 0;
  reg signed [7:0] io_i_cFS_real = 0;
  reg signed [15:0] io_i_cGenL_imag = 0;
  reg signed [15:0] io_i_cGenL_real = 0;
  reg[0:0] io_i_b = 0;
  wire signed [7:0] io_o_vF_0;
  wire signed [7:0] io_o_vF_1;
  wire signed [7:0] io_o_vF_2;
  wire signed [7:0] io_o_vF_3;
  wire signed [7:0] io_o_vF_4;
  wire signed [7:0] io_o_vF_5;
  wire signed [7:0] io_o_vF_6;
  wire signed [7:0] io_o_vF_7;
  wire signed [7:0] io_o_vF_8;
  wire signed [7:0] io_o_vF_9;
  wire signed [7:0] io_o_vS_0;
  wire signed [7:0] io_o_vS_1;
  wire signed [7:0] io_o_vS_2;
  wire signed [7:0] io_o_vS_3;
  wire signed [7:0] io_o_vS_4;
  wire signed [7:0] io_o_vS_5;
  wire signed [7:0] io_o_vS_6;
  wire signed [7:0] io_o_vS_7;
  wire signed [7:0] io_o_vS_8;
  wire signed [7:0] io_o_vS_9;
  wire[7:0] io_o_vU_0;
  wire[7:0] io_o_vU_1;
  wire[7:0] io_o_vU_2;
  wire[7:0] io_o_vU_3;
  wire[7:0] io_o_vU_4;
  wire[7:0] io_o_vU_5;
  wire[7:0] io_o_vU_6;
  wire[7:0] io_o_vU_7;
  wire[7:0] io_o_vU_8;
  wire[7:0] io_o_vU_9;
  wire[15:0] io_o_long_u;
  wire signed [15:0] io_o_long_f;
  wire signed [15:0] io_o_long_s;
  wire signed [15:0] io_o_long_gen;
  wire[7:0] io_o_short_u;
  wire signed [7:0] io_o_short_f;
  wire signed [7:0] io_o_short_s;
  wire signed [7:0] io_o_short_gen;
  wire signed [7:0] io_o_cFS_imag;
  wire signed [7:0] io_o_cFS_real;
  wire signed [15:0] io_o_cGenL_imag;
  wire signed [15:0] io_o_cGenL_real;
  wire[0:0] io_o_b;

  always #`HALF_CLK_PERIOD clock = ~clock;

  initial begin
    #`RESET_TIME
    forever #`CLK_PERIOD cycle = cycle + 1;
  end

  SimpleIOModule SimpleIOModule(
    .clock(clock),
    .reset(reset),
    .io_i_vF_0(io_i_vF_0),
    .io_i_vF_1(io_i_vF_1),
    .io_i_vF_2(io_i_vF_2),
    .io_i_vF_3(io_i_vF_3),
    .io_i_vF_4(io_i_vF_4),
    .io_i_vF_5(io_i_vF_5),
    .io_i_vF_6(io_i_vF_6),
    .io_i_vF_7(io_i_vF_7),
    .io_i_vF_8(io_i_vF_8),
    .io_i_vF_9(io_i_vF_9),
    .io_i_vS_0(io_i_vS_0),
    .io_i_vS_1(io_i_vS_1),
    .io_i_vS_2(io_i_vS_2),
    .io_i_vS_3(io_i_vS_3),
    .io_i_vS_4(io_i_vS_4),
    .io_i_vS_5(io_i_vS_5),
    .io_i_vS_6(io_i_vS_6),
    .io_i_vS_7(io_i_vS_7),
    .io_i_vS_8(io_i_vS_8),
    .io_i_vS_9(io_i_vS_9),
    .io_i_vU_0(io_i_vU_0),
    .io_i_vU_1(io_i_vU_1),
    .io_i_vU_2(io_i_vU_2),
    .io_i_vU_3(io_i_vU_3),
    .io_i_vU_4(io_i_vU_4),
    .io_i_vU_5(io_i_vU_5),
    .io_i_vU_6(io_i_vU_6),
    .io_i_vU_7(io_i_vU_7),
    .io_i_vU_8(io_i_vU_8),
    .io_i_vU_9(io_i_vU_9),
    .io_i_long_u(io_i_long_u),
    .io_i_long_f(io_i_long_f),
    .io_i_long_s(io_i_long_s),
    .io_i_long_gen(io_i_long_gen),
    .io_i_short_u(io_i_short_u),
    .io_i_short_f(io_i_short_f),
    .io_i_short_s(io_i_short_s),
    .io_i_short_gen(io_i_short_gen),
    .io_i_cFS_imag(io_i_cFS_imag),
    .io_i_cFS_real(io_i_cFS_real),
    .io_i_cGenL_imag(io_i_cGenL_imag),
    .io_i_cGenL_real(io_i_cGenL_real),
    .io_i_b(io_i_b),
    .io_o_vF_0(io_o_vF_0),
    .io_o_vF_1(io_o_vF_1),
    .io_o_vF_2(io_o_vF_2),
    .io_o_vF_3(io_o_vF_3),
    .io_o_vF_4(io_o_vF_4),
    .io_o_vF_5(io_o_vF_5),
    .io_o_vF_6(io_o_vF_6),
    .io_o_vF_7(io_o_vF_7),
    .io_o_vF_8(io_o_vF_8),
    .io_o_vF_9(io_o_vF_9),
    .io_o_vS_0(io_o_vS_0),
    .io_o_vS_1(io_o_vS_1),
    .io_o_vS_2(io_o_vS_2),
    .io_o_vS_3(io_o_vS_3),
    .io_o_vS_4(io_o_vS_4),
    .io_o_vS_5(io_o_vS_5),
    .io_o_vS_6(io_o_vS_6),
    .io_o_vS_7(io_o_vS_7),
    .io_o_vS_8(io_o_vS_8),
    .io_o_vS_9(io_o_vS_9),
    .io_o_vU_0(io_o_vU_0),
    .io_o_vU_1(io_o_vU_1),
    .io_o_vU_2(io_o_vU_2),
    .io_o_vU_3(io_o_vU_3),
    .io_o_vU_4(io_o_vU_4),
    .io_o_vU_5(io_o_vU_5),
    .io_o_vU_6(io_o_vU_6),
    .io_o_vU_7(io_o_vU_7),
    .io_o_vU_8(io_o_vU_8),
    .io_o_vU_9(io_o_vU_9),
    .io_o_long_u(io_o_long_u),
    .io_o_long_f(io_o_long_f),
    .io_o_long_s(io_o_long_s),
    .io_o_long_gen(io_o_long_gen),
    .io_o_short_u(io_o_short_u),
    .io_o_short_f(io_o_short_f),
    .io_o_short_s(io_o_short_s),
    .io_o_short_gen(io_o_short_gen),
    .io_o_cFS_imag(io_o_cFS_imag),
    .io_o_cFS_real(io_o_cFS_real),
    .io_o_cGenL_imag(io_o_cGenL_imag),
    .io_o_cGenL_real(io_o_cGenL_real),
    .io_o_b(io_o_b));

  initial begin
    #`INIT_TIME reset = 0;
    io_i_short_u = 2'd3;
    io_i_short_f = -53;
    io_i_short_s = -3;
    io_i_short_gen = -53;
    io_i_long_u = 2'd3;
    io_i_long_f = -845;
    io_i_long_s = -3;
    io_i_long_gen = -845;
    io_i_b = 1'd1;
    io_i_cGenL_real = -845;
    io_i_cGenL_imag = 10'd845;
    io_i_cFS_real = -53;
    io_i_cFS_imag = 6'd53;
    #(1*`CLK_PERIOD)     io_i_short_u = 2'd2;
    io_i_short_f = -35;
    io_i_short_s = -2;
    io_i_short_gen = -35;
    io_i_long_u = 2'd2;
    io_i_long_f = -563;
    io_i_long_s = -2;
    io_i_long_gen = -563;
    io_i_b = 1'd1;
    io_i_cGenL_real = -563;
    io_i_cGenL_imag = 10'd563;
    io_i_cFS_real = -35;
    io_i_cFS_imag = 6'd35;
    `expect("io_o_short_u",io_o_short_u,3,cycle)
    `expect("io_o_short_u",io_o_short_u,3,cycle)
    `expect("io_o_short_f",io_o_short_f,-53,cycle)
    `expect("io_o_short_f",io_o_short_f,-53,cycle)
    `expect("io_o_short_s",io_o_short_s,-3,cycle)
    `expect("io_o_short_s",io_o_short_s,-3,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-53,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-53,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-845,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,845,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-845,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,845,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-53,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,53,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-53,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,53,cycle)
    `expect("io_o_long_u",io_o_long_u,3,cycle)
    `expect("io_o_long_u",io_o_long_u,3,cycle)
    `expect("io_o_long_f",io_o_long_f,-848,cycle)
    `expect("io_o_long_f",io_o_long_f,-848,cycle)
    `expect("io_o_long_s",io_o_long_s,-3,cycle)
    `expect("io_o_long_s",io_o_long_s,-3,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-848,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-848,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 1'd1;
    io_i_short_f = -18;
    io_i_short_s = -1;
    io_i_short_gen = -18;
    io_i_long_u = 1'd1;
    io_i_long_f = -282;
    io_i_long_s = -1;
    io_i_long_gen = -282;
    io_i_b = 1'd1;
    io_i_cGenL_real = -282;
    io_i_cGenL_imag = 9'd282;
    io_i_cFS_real = -18;
    io_i_cFS_imag = 5'd18;
    `expect("io_o_short_u",io_o_short_u,2,cycle)
    `expect("io_o_short_u",io_o_short_u,2,cycle)
    `expect("io_o_short_f",io_o_short_f,-36,cycle)
    `expect("io_o_short_f",io_o_short_f,-36,cycle)
    `expect("io_o_short_s",io_o_short_s,-2,cycle)
    `expect("io_o_short_s",io_o_short_s,-2,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-36,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-36,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-563,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,563,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-563,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,563,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-35,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,35,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-35,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,35,cycle)
    `expect("io_o_long_u",io_o_long_u,2,cycle)
    `expect("io_o_long_u",io_o_long_u,2,cycle)
    `expect("io_o_long_f",io_o_long_f,-560,cycle)
    `expect("io_o_long_f",io_o_long_f,-560,cycle)
    `expect("io_o_long_s",io_o_long_s,-2,cycle)
    `expect("io_o_long_s",io_o_long_s,-2,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-560,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-560,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 1'd1;
    io_i_short_f = -9;
    io_i_short_s = -1;
    io_i_short_gen = -9;
    io_i_long_u = 1'd1;
    io_i_long_f = -141;
    io_i_long_s = -1;
    io_i_long_gen = -141;
    io_i_b = 1'd1;
    io_i_cGenL_real = -141;
    io_i_cGenL_imag = 8'd141;
    io_i_cFS_real = -9;
    io_i_cFS_imag = 4'd9;
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_f",io_o_short_f,-18,cycle)
    `expect("io_o_short_f",io_o_short_f,-18,cycle)
    `expect("io_o_short_s",io_o_short_s,-1,cycle)
    `expect("io_o_short_s",io_o_short_s,-1,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-18,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-18,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-282,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,282,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-282,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,282,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-18,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,18,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-18,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,18,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_f",io_o_long_f,-288,cycle)
    `expect("io_o_long_f",io_o_long_f,-288,cycle)
    `expect("io_o_long_s",io_o_long_s,-1,cycle)
    `expect("io_o_long_s",io_o_long_s,-1,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-288,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-288,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 1'd0;
    io_i_short_f = -6;
    io_i_short_s = 1'd0;
    io_i_short_gen = -6;
    io_i_long_u = 1'd0;
    io_i_long_f = -102;
    io_i_long_s = 1'd0;
    io_i_long_gen = -102;
    io_i_b = 1'd1;
    io_i_cGenL_real = -102;
    io_i_cGenL_imag = 7'd102;
    io_i_cFS_real = -6;
    io_i_cFS_imag = 3'd6;
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_f",io_o_short_f,-9,cycle)
    `expect("io_o_short_f",io_o_short_f,-9,cycle)
    `expect("io_o_short_s",io_o_short_s,-1,cycle)
    `expect("io_o_short_s",io_o_short_s,-1,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-9,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-9,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-141,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,141,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-141,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,141,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-9,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,9,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-9,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,9,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_f",io_o_long_f,-144,cycle)
    `expect("io_o_long_f",io_o_long_f,-144,cycle)
    `expect("io_o_long_s",io_o_long_s,-1,cycle)
    `expect("io_o_long_s",io_o_long_s,-1,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-144,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-144,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 1'd0;
    io_i_short_f = 3'd6;
    io_i_short_s = 1'd0;
    io_i_short_gen = 3'd6;
    io_i_long_u = 1'd0;
    io_i_long_f = 7'd102;
    io_i_long_s = 1'd0;
    io_i_long_gen = 7'd102;
    io_i_b = 1'd1;
    io_i_cGenL_real = 7'd102;
    io_i_cGenL_imag = -102;
    io_i_cFS_real = 3'd6;
    io_i_cFS_imag = -6;
    `expect("io_o_short_u",io_o_short_u,0,cycle)
    `expect("io_o_short_u",io_o_short_u,0,cycle)
    `expect("io_o_short_f",io_o_short_f,-7,cycle)
    `expect("io_o_short_f",io_o_short_f,-7,cycle)
    `expect("io_o_short_s",io_o_short_s,0,cycle)
    `expect("io_o_short_s",io_o_short_s,0,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-7,cycle)
    `expect("io_o_short_gen",io_o_short_gen,-7,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-102,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,102,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,-102,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,102,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-6,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,6,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,-6,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,6,cycle)
    `expect("io_o_long_u",io_o_long_u,0,cycle)
    `expect("io_o_long_u",io_o_long_u,0,cycle)
    `expect("io_o_long_f",io_o_long_f,-96,cycle)
    `expect("io_o_long_f",io_o_long_f,-96,cycle)
    `expect("io_o_long_s",io_o_long_s,0,cycle)
    `expect("io_o_long_s",io_o_long_s,0,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-96,cycle)
    `expect("io_o_long_gen",io_o_long_gen,-96,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 1'd1;
    io_i_short_f = 4'd9;
    io_i_short_s = 1'd1;
    io_i_short_gen = 4'd9;
    io_i_long_u = 1'd1;
    io_i_long_f = 8'd141;
    io_i_long_s = 1'd1;
    io_i_long_gen = 8'd141;
    io_i_b = 1'd1;
    io_i_cGenL_real = 8'd141;
    io_i_cGenL_imag = -141;
    io_i_cFS_real = 4'd9;
    io_i_cFS_imag = -9;
    `expect("io_o_short_u",io_o_short_u,0,cycle)
    `expect("io_o_short_u",io_o_short_u,0,cycle)
    `expect("io_o_short_f",io_o_short_f,6,cycle)
    `expect("io_o_short_f",io_o_short_f,6,cycle)
    `expect("io_o_short_s",io_o_short_s,0,cycle)
    `expect("io_o_short_s",io_o_short_s,0,cycle)
    `expect("io_o_short_gen",io_o_short_gen,6,cycle)
    `expect("io_o_short_gen",io_o_short_gen,6,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,102,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-102,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,102,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-102,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,6,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-6,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,6,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-6,cycle)
    `expect("io_o_long_u",io_o_long_u,0,cycle)
    `expect("io_o_long_u",io_o_long_u,0,cycle)
    `expect("io_o_long_f",io_o_long_f,96,cycle)
    `expect("io_o_long_f",io_o_long_f,96,cycle)
    `expect("io_o_long_s",io_o_long_s,0,cycle)
    `expect("io_o_long_s",io_o_long_s,0,cycle)
    `expect("io_o_long_gen",io_o_long_gen,96,cycle)
    `expect("io_o_long_gen",io_o_long_gen,96,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 1'd1;
    io_i_short_f = 5'd18;
    io_i_short_s = 1'd1;
    io_i_short_gen = 5'd18;
    io_i_long_u = 1'd1;
    io_i_long_f = 9'd282;
    io_i_long_s = 1'd1;
    io_i_long_gen = 9'd282;
    io_i_b = 1'd1;
    io_i_cGenL_real = 9'd282;
    io_i_cGenL_imag = -282;
    io_i_cFS_real = 5'd18;
    io_i_cFS_imag = -18;
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_f",io_o_short_f,8,cycle)
    `expect("io_o_short_f",io_o_short_f,8,cycle)
    `expect("io_o_short_s",io_o_short_s,1,cycle)
    `expect("io_o_short_s",io_o_short_s,1,cycle)
    `expect("io_o_short_gen",io_o_short_gen,8,cycle)
    `expect("io_o_short_gen",io_o_short_gen,8,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,141,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-141,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,141,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-141,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,9,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-9,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,9,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-9,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_f",io_o_long_f,144,cycle)
    `expect("io_o_long_f",io_o_long_f,144,cycle)
    `expect("io_o_long_s",io_o_long_s,1,cycle)
    `expect("io_o_long_s",io_o_long_s,1,cycle)
    `expect("io_o_long_gen",io_o_long_gen,144,cycle)
    `expect("io_o_long_gen",io_o_long_gen,144,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 2'd2;
    io_i_short_f = 6'd35;
    io_i_short_s = 2'd2;
    io_i_short_gen = 6'd35;
    io_i_long_u = 2'd2;
    io_i_long_f = 10'd563;
    io_i_long_s = 2'd2;
    io_i_long_gen = 10'd563;
    io_i_b = 1'd1;
    io_i_cGenL_real = 10'd563;
    io_i_cGenL_imag = -563;
    io_i_cFS_real = 6'd35;
    io_i_cFS_imag = -35;
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_u",io_o_short_u,1,cycle)
    `expect("io_o_short_f",io_o_short_f,17,cycle)
    `expect("io_o_short_f",io_o_short_f,17,cycle)
    `expect("io_o_short_s",io_o_short_s,1,cycle)
    `expect("io_o_short_s",io_o_short_s,1,cycle)
    `expect("io_o_short_gen",io_o_short_gen,17,cycle)
    `expect("io_o_short_gen",io_o_short_gen,17,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,282,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-282,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,282,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-282,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,18,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-18,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,18,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-18,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_u",io_o_long_u,1,cycle)
    `expect("io_o_long_f",io_o_long_f,288,cycle)
    `expect("io_o_long_f",io_o_long_f,288,cycle)
    `expect("io_o_long_s",io_o_long_s,1,cycle)
    `expect("io_o_long_s",io_o_long_s,1,cycle)
    `expect("io_o_long_gen",io_o_long_gen,288,cycle)
    `expect("io_o_long_gen",io_o_long_gen,288,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 2'd3;
    io_i_short_f = 6'd53;
    io_i_short_s = 2'd3;
    io_i_short_gen = 6'd53;
    io_i_long_u = 2'd3;
    io_i_long_f = 10'd845;
    io_i_long_s = 2'd3;
    io_i_long_gen = 10'd845;
    io_i_b = 1'd1;
    io_i_cGenL_real = 10'd845;
    io_i_cGenL_imag = -845;
    io_i_cFS_real = 6'd53;
    io_i_cFS_imag = -53;
    `expect("io_o_short_u",io_o_short_u,2,cycle)
    `expect("io_o_short_u",io_o_short_u,2,cycle)
    `expect("io_o_short_f",io_o_short_f,35,cycle)
    `expect("io_o_short_f",io_o_short_f,35,cycle)
    `expect("io_o_short_s",io_o_short_s,2,cycle)
    `expect("io_o_short_s",io_o_short_s,2,cycle)
    `expect("io_o_short_gen",io_o_short_gen,35,cycle)
    `expect("io_o_short_gen",io_o_short_gen,35,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,563,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-563,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,563,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-563,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,35,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-35,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,35,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-35,cycle)
    `expect("io_o_long_u",io_o_long_u,2,cycle)
    `expect("io_o_long_u",io_o_long_u,2,cycle)
    `expect("io_o_long_f",io_o_long_f,560,cycle)
    `expect("io_o_long_f",io_o_long_f,560,cycle)
    `expect("io_o_long_s",io_o_long_s,2,cycle)
    `expect("io_o_long_s",io_o_long_s,2,cycle)
    `expect("io_o_long_gen",io_o_long_gen,560,cycle)
    `expect("io_o_long_gen",io_o_long_gen,560,cycle)
    #(1*`CLK_PERIOD)     io_i_short_u = 2'd3;
    io_i_short_f = -53;
    io_i_short_s = -3;
    io_i_short_gen = -53;
    io_i_long_u = 2'd3;
    io_i_long_f = -845;
    io_i_long_s = -3;
    io_i_long_gen = -845;
    io_i_b = 1'd1;
    io_i_cGenL_real = -845;
    io_i_cGenL_imag = 10'd845;
    io_i_cFS_real = -53;
    io_i_cFS_imag = 6'd53;
    `expect("io_o_short_u",io_o_short_u,3,cycle)
    `expect("io_o_short_u",io_o_short_u,3,cycle)
    `expect("io_o_short_f",io_o_short_f,52,cycle)
    `expect("io_o_short_f",io_o_short_f,52,cycle)
    `expect("io_o_short_s",io_o_short_s,3,cycle)
    `expect("io_o_short_s",io_o_short_s,3,cycle)
    `expect("io_o_short_gen",io_o_short_gen,52,cycle)
    `expect("io_o_short_gen",io_o_short_gen,52,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_b",io_o_b,1,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,845,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-845,cycle)
    `expect("io_o_cGenL_real",io_o_cGenL_real,845,cycle)
    `expect("io_o_cGenL_imag",io_o_cGenL_imag,-845,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,53,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-53,cycle)
    `expect("io_o_cFS_real",io_o_cFS_real,53,cycle)
    `expect("io_o_cFS_imag",io_o_cFS_imag,-53,cycle)
    `expect("io_o_long_u",io_o_long_u,3,cycle)
    `expect("io_o_long_u",io_o_long_u,3,cycle)
    `expect("io_o_long_f",io_o_long_f,848,cycle)
    `expect("io_o_long_f",io_o_long_f,848,cycle)
    `expect("io_o_long_s",io_o_long_s,3,cycle)
    `expect("io_o_long_s",io_o_long_s,3,cycle)
    `expect("io_o_long_gen",io_o_long_gen,848,cycle)
    `expect("io_o_long_gen",io_o_long_gen,848,cycle)
    #(1*`CLK_PERIOD)     io_i_vU_0 = 2'd3;
    io_i_vS_0 = -3;
    io_i_vF_0 = -53;
    io_i_vU_1 = 2'd2;
    io_i_vS_1 = -2;
    io_i_vF_1 = -35;
    io_i_vU_2 = 1'd1;
    io_i_vS_2 = -1;
    io_i_vF_2 = -18;
    io_i_vU_3 = 1'd1;
    io_i_vS_3 = -1;
    io_i_vF_3 = -9;
    io_i_vU_4 = 1'd0;
    io_i_vS_4 = 1'd0;
    io_i_vF_4 = -6;
    io_i_vU_5 = 1'd0;
    io_i_vS_5 = 1'd0;
    io_i_vF_5 = 3'd6;
    io_i_vU_6 = 1'd1;
    io_i_vS_6 = 1'd1;
    io_i_vF_6 = 4'd9;
    io_i_vU_7 = 1'd1;
    io_i_vS_7 = 1'd1;
    io_i_vF_7 = 5'd18;
    io_i_vU_8 = 2'd2;
    io_i_vS_8 = 2'd2;
    io_i_vF_8 = 6'd35;
    io_i_vU_9 = 2'd3;
    io_i_vS_9 = 2'd3;
    io_i_vF_9 = 6'd53;
    #(5*`CLK_PERIOD)     io_i_vU_0 = 2'd3;
    io_i_vS_0 = 2'd3;
    io_i_vF_0 = 6'd53;
    `expect("io_o_vU_0",io_o_vU_0,3,cycle)
    `expect("io_o_vU_0",io_o_vU_0,3,cycle)
    `expect("io_o_vS_0",io_o_vS_0,-3,cycle)
    `expect("io_o_vS_0",io_o_vS_0,-3,cycle)
    `expect("io_o_vF_0",io_o_vF_0,-53,cycle)
    `expect("io_o_vF_0",io_o_vF_0,-53,cycle)
    io_i_vU_1 = 2'd2;
    io_i_vS_1 = 2'd2;
    io_i_vF_1 = 6'd35;
    `expect("io_o_vU_1",io_o_vU_1,2,cycle)
    `expect("io_o_vU_1",io_o_vU_1,2,cycle)
    `expect("io_o_vS_1",io_o_vS_1,-2,cycle)
    `expect("io_o_vS_1",io_o_vS_1,-2,cycle)
    `expect("io_o_vF_1",io_o_vF_1,-35,cycle)
    `expect("io_o_vF_1",io_o_vF_1,-35,cycle)
    io_i_vU_2 = 1'd1;
    io_i_vS_2 = 1'd1;
    io_i_vF_2 = 5'd18;
    `expect("io_o_vU_2",io_o_vU_2,1,cycle)
    `expect("io_o_vU_2",io_o_vU_2,1,cycle)
    `expect("io_o_vS_2",io_o_vS_2,-1,cycle)
    `expect("io_o_vS_2",io_o_vS_2,-1,cycle)
    `expect("io_o_vF_2",io_o_vF_2,-18,cycle)
    `expect("io_o_vF_2",io_o_vF_2,-18,cycle)
    io_i_vU_3 = 1'd1;
    io_i_vS_3 = 1'd1;
    io_i_vF_3 = 4'd9;
    `expect("io_o_vU_3",io_o_vU_3,1,cycle)
    `expect("io_o_vU_3",io_o_vU_3,1,cycle)
    `expect("io_o_vS_3",io_o_vS_3,-1,cycle)
    `expect("io_o_vS_3",io_o_vS_3,-1,cycle)
    `expect("io_o_vF_3",io_o_vF_3,-9,cycle)
    `expect("io_o_vF_3",io_o_vF_3,-9,cycle)
    io_i_vU_4 = 1'd0;
    io_i_vS_4 = 1'd0;
    io_i_vF_4 = 3'd6;
    `expect("io_o_vU_4",io_o_vU_4,0,cycle)
    `expect("io_o_vU_4",io_o_vU_4,0,cycle)
    `expect("io_o_vS_4",io_o_vS_4,0,cycle)
    `expect("io_o_vS_4",io_o_vS_4,0,cycle)
    `expect("io_o_vF_4",io_o_vF_4,-6,cycle)
    `expect("io_o_vF_4",io_o_vF_4,-6,cycle)
    io_i_vU_5 = 1'd0;
    io_i_vS_5 = 1'd0;
    io_i_vF_5 = -6;
    `expect("io_o_vU_5",io_o_vU_5,0,cycle)
    `expect("io_o_vU_5",io_o_vU_5,0,cycle)
    `expect("io_o_vS_5",io_o_vS_5,0,cycle)
    `expect("io_o_vS_5",io_o_vS_5,0,cycle)
    `expect("io_o_vF_5",io_o_vF_5,6,cycle)
    `expect("io_o_vF_5",io_o_vF_5,6,cycle)
    io_i_vU_6 = 1'd1;
    io_i_vS_6 = -1;
    io_i_vF_6 = -9;
    `expect("io_o_vU_6",io_o_vU_6,1,cycle)
    `expect("io_o_vU_6",io_o_vU_6,1,cycle)
    `expect("io_o_vS_6",io_o_vS_6,1,cycle)
    `expect("io_o_vS_6",io_o_vS_6,1,cycle)
    `expect("io_o_vF_6",io_o_vF_6,9,cycle)
    `expect("io_o_vF_6",io_o_vF_6,9,cycle)
    io_i_vU_7 = 1'd1;
    io_i_vS_7 = -1;
    io_i_vF_7 = -18;
    `expect("io_o_vU_7",io_o_vU_7,1,cycle)
    `expect("io_o_vU_7",io_o_vU_7,1,cycle)
    `expect("io_o_vS_7",io_o_vS_7,1,cycle)
    `expect("io_o_vS_7",io_o_vS_7,1,cycle)
    `expect("io_o_vF_7",io_o_vF_7,18,cycle)
    `expect("io_o_vF_7",io_o_vF_7,18,cycle)
    io_i_vU_8 = 2'd2;
    io_i_vS_8 = -2;
    io_i_vF_8 = -35;
    `expect("io_o_vU_8",io_o_vU_8,2,cycle)
    `expect("io_o_vU_8",io_o_vU_8,2,cycle)
    `expect("io_o_vS_8",io_o_vS_8,2,cycle)
    `expect("io_o_vS_8",io_o_vS_8,2,cycle)
    `expect("io_o_vF_8",io_o_vF_8,35,cycle)
    `expect("io_o_vF_8",io_o_vF_8,35,cycle)
    io_i_vU_9 = 2'd3;
    io_i_vS_9 = -3;
    io_i_vF_9 = -53;
    `expect("io_o_vU_9",io_o_vU_9,3,cycle)
    `expect("io_o_vU_9",io_o_vU_9,3,cycle)
    `expect("io_o_vS_9",io_o_vS_9,3,cycle)
    `expect("io_o_vS_9",io_o_vS_9,3,cycle)
    `expect("io_o_vF_9",io_o_vF_9,53,cycle)
    `expect("io_o_vF_9",io_o_vF_9,53,cycle)
    #(5*`CLK_PERIOD)     io_i_vU_0 = 1'd0;
    io_i_vS_0 = 1'd0;
    io_i_vF_0 = 1'd0;
    `expect("io_o_vU_0",io_o_vU_0,3,cycle)
    `expect("io_o_vU_0",io_o_vU_0,3,cycle)
    `expect("io_o_vS_0",io_o_vS_0,3,cycle)
    `expect("io_o_vS_0",io_o_vS_0,3,cycle)
    `expect("io_o_vF_0",io_o_vF_0,53,cycle)
    `expect("io_o_vF_0",io_o_vF_0,53,cycle)
    io_i_vU_1 = 1'd0;
    io_i_vS_1 = 1'd0;
    io_i_vF_1 = 1'd0;
    `expect("io_o_vU_1",io_o_vU_1,2,cycle)
    `expect("io_o_vU_1",io_o_vU_1,2,cycle)
    `expect("io_o_vS_1",io_o_vS_1,2,cycle)
    `expect("io_o_vS_1",io_o_vS_1,2,cycle)
    `expect("io_o_vF_1",io_o_vF_1,35,cycle)
    `expect("io_o_vF_1",io_o_vF_1,35,cycle)
    io_i_vU_2 = 1'd0;
    io_i_vS_2 = 1'd0;
    io_i_vF_2 = 1'd0;
    `expect("io_o_vU_2",io_o_vU_2,1,cycle)
    `expect("io_o_vU_2",io_o_vU_2,1,cycle)
    `expect("io_o_vS_2",io_o_vS_2,1,cycle)
    `expect("io_o_vS_2",io_o_vS_2,1,cycle)
    `expect("io_o_vF_2",io_o_vF_2,18,cycle)
    `expect("io_o_vF_2",io_o_vF_2,18,cycle)
    io_i_vU_3 = 1'd0;
    io_i_vS_3 = 1'd0;
    io_i_vF_3 = 1'd0;
    `expect("io_o_vU_3",io_o_vU_3,1,cycle)
    `expect("io_o_vU_3",io_o_vU_3,1,cycle)
    `expect("io_o_vS_3",io_o_vS_3,1,cycle)
    `expect("io_o_vS_3",io_o_vS_3,1,cycle)
    `expect("io_o_vF_3",io_o_vF_3,9,cycle)
    `expect("io_o_vF_3",io_o_vF_3,9,cycle)
    io_i_vU_4 = 1'd0;
    io_i_vS_4 = 1'd0;
    io_i_vF_4 = 1'd0;
    `expect("io_o_vU_4",io_o_vU_4,0,cycle)
    `expect("io_o_vU_4",io_o_vU_4,0,cycle)
    `expect("io_o_vS_4",io_o_vS_4,0,cycle)
    `expect("io_o_vS_4",io_o_vS_4,0,cycle)
    `expect("io_o_vF_4",io_o_vF_4,6,cycle)
    `expect("io_o_vF_4",io_o_vF_4,6,cycle)
    io_i_vU_5 = 1'd0;
    io_i_vS_5 = 1'd0;
    io_i_vF_5 = 1'd0;
    `expect("io_o_vU_5",io_o_vU_5,0,cycle)
    `expect("io_o_vU_5",io_o_vU_5,0,cycle)
    `expect("io_o_vS_5",io_o_vS_5,0,cycle)
    `expect("io_o_vS_5",io_o_vS_5,0,cycle)
    `expect("io_o_vF_5",io_o_vF_5,-6,cycle)
    `expect("io_o_vF_5",io_o_vF_5,-6,cycle)
    io_i_vU_6 = 1'd0;
    io_i_vS_6 = 1'd0;
    io_i_vF_6 = 1'd0;
    `expect("io_o_vU_6",io_o_vU_6,1,cycle)
    `expect("io_o_vU_6",io_o_vU_6,1,cycle)
    `expect("io_o_vS_6",io_o_vS_6,-1,cycle)
    `expect("io_o_vS_6",io_o_vS_6,-1,cycle)
    `expect("io_o_vF_6",io_o_vF_6,-9,cycle)
    `expect("io_o_vF_6",io_o_vF_6,-9,cycle)
    io_i_vU_7 = 1'd0;
    io_i_vS_7 = 1'd0;
    io_i_vF_7 = 1'd0;
    `expect("io_o_vU_7",io_o_vU_7,1,cycle)
    `expect("io_o_vU_7",io_o_vU_7,1,cycle)
    `expect("io_o_vS_7",io_o_vS_7,-1,cycle)
    `expect("io_o_vS_7",io_o_vS_7,-1,cycle)
    `expect("io_o_vF_7",io_o_vF_7,-18,cycle)
    `expect("io_o_vF_7",io_o_vF_7,-18,cycle)
    io_i_vU_8 = 1'd0;
    io_i_vS_8 = 1'd0;
    io_i_vF_8 = 1'd0;
    `expect("io_o_vU_8",io_o_vU_8,2,cycle)
    `expect("io_o_vU_8",io_o_vU_8,2,cycle)
    `expect("io_o_vS_8",io_o_vS_8,-2,cycle)
    `expect("io_o_vS_8",io_o_vS_8,-2,cycle)
    `expect("io_o_vF_8",io_o_vF_8,-35,cycle)
    `expect("io_o_vF_8",io_o_vF_8,-35,cycle)
    io_i_vU_9 = 1'd0;
    io_i_vS_9 = 1'd0;
    io_i_vF_9 = 1'd0;
    `expect("io_o_vU_9",io_o_vU_9,3,cycle)
    `expect("io_o_vU_9",io_o_vU_9,3,cycle)
    `expect("io_o_vS_9",io_o_vS_9,-3,cycle)
    `expect("io_o_vS_9",io_o_vS_9,-3,cycle)
    `expect("io_o_vF_9",io_o_vF_9,-53,cycle)
    `expect("io_o_vF_9",io_o_vF_9,-53,cycle)
    `expect("io_o_vU_0",io_o_vU_0,3,cycle)
    `expect("io_o_vU_1",io_o_vU_1,2,cycle)
    `expect("io_o_vU_2",io_o_vU_2,1,cycle)
    `expect("io_o_vU_3",io_o_vU_3,1,cycle)
    `expect("io_o_vU_4",io_o_vU_4,0,cycle)
    `expect("io_o_vU_5",io_o_vU_5,0,cycle)
    `expect("io_o_vU_6",io_o_vU_6,1,cycle)
    `expect("io_o_vU_7",io_o_vU_7,1,cycle)
    `expect("io_o_vU_8",io_o_vU_8,2,cycle)
    `expect("io_o_vU_9",io_o_vU_9,3,cycle)
    `expect("io_o_vS_0",io_o_vS_0,3,cycle)
    `expect("io_o_vS_1",io_o_vS_1,2,cycle)
    `expect("io_o_vS_2",io_o_vS_2,1,cycle)
    `expect("io_o_vS_3",io_o_vS_3,1,cycle)
    `expect("io_o_vS_4",io_o_vS_4,0,cycle)
    `expect("io_o_vS_5",io_o_vS_5,0,cycle)
    `expect("io_o_vS_6",io_o_vS_6,-1,cycle)
    `expect("io_o_vS_7",io_o_vS_7,-1,cycle)
    `expect("io_o_vS_8",io_o_vS_8,-2,cycle)
    `expect("io_o_vS_9",io_o_vS_9,-3,cycle)

    #`CLK_PERIOD $display("\t **Ran through all test vectors**"); $finish;

  end
endmodule