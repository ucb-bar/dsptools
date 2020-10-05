// SPDX-License-Identifier: Apache-2.0

module BBFCos(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($cos($bitstoreal(in)));
  end
endmodule

