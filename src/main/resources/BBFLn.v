/** Math operations from IEEE.1364-2005 **/
// SPDX-License-Identifier: Apache-2.0

module BBFLn(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($ln($bitstoreal(in)));
  end
endmodule

