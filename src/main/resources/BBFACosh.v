// SPDX-License-Identifier: Apache-2.0

module BBFACosh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($acosh($bitstoreal(in)));
  end
endmodule

