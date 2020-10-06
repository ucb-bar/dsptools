// SPDX-License-Identifier: Apache-2.0

module BBFATanh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($atanh($bitstoreal(in)));
  end
endmodule

