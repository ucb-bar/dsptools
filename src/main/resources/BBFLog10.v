// SPDX-License-Identifier: Apache-2.0

module BBFLog10(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($log10($bitstoreal(in)));
  end
endmodule

