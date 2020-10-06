// SPDX-License-Identifier: Apache-2.0

module BBFFloor(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($floor($bitstoreal(in)));
  end
endmodule

