// SPDX-License-Identifier: Apache-2.0

module BBFTanh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($tanh($bitstoreal(in)));
  end
endmodule

