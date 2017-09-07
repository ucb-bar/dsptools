module BBFTan(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out = $realtobits($tan($bitstoreal(in)));
  end
endmodule

