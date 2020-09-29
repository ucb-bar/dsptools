// SPDX-License-Identifier: Apache-2.0

module BBFExp(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($exp($bitstoreal(in)));
  end
endmodule

