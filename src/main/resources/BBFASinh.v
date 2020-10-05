// SPDX-License-Identifier: Apache-2.0

module BBFASinh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($asinh($bitstoreal(in)));
  end
endmodule

