// SPDX-License-Identifier: Apache-2.0

module BBFSinh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($sinh($bitstoreal(in)));
  end
endmodule

