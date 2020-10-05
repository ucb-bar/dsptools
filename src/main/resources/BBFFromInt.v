// SPDX-License-Identifier: Apache-2.0

module BBFFromInt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($itor($signed(in)));
  end
endmodule

