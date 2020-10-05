// SPDX-License-Identifier: Apache-2.0

module BBFGreaterThan(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg out
);
  always @* begin
  out = $bitstoreal(in1) > $bitstoreal(in2);
  end
endmodule

