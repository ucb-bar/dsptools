module BBFCeil(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($ceil($bitstoreal(in)));
  end
endmodule

