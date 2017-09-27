// WARNING! May cause overflow!
module BBFToInt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $rtoi($bitstoreal(in));
  end
endmodule

