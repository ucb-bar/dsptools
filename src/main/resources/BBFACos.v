// SPDX-License-Identifier: Apache-2.0

module BBFACos(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($acos($bitstoreal(in)));
  end
endmodule

