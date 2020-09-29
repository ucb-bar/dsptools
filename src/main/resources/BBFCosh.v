// SPDX-License-Identifier: Apache-2.0

module BBFCosh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($cosh($bitstoreal(in)));
  end
endmodule

