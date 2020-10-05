// SPDX-License-Identifier: Apache-2.0

module BBFSqrt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($sqrt($bitstoreal(in)));
  end
endmodule

