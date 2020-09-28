// SPDX-License-Identifier: Apache-2.0

module BBFASin(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($asin($bitstoreal(in)));
  end
endmodule

