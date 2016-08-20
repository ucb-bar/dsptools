module BBFFromInt(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($itor(in));
  end
endmodule

// WARNING! May cause overflow!
module BBFToInt(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $rtoi($bitstoreal(in));
  end
endmodule

module BBFAdd(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) + $bitstoreal(in2));
  end
endmodule

module BBFSubtract(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) - $bitstoreal(in2));
  end
endmodule

module BBFMultiply(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) * $bitstoreal(in2));
  end
endmodule

module BBFDivide(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) / $bitstoreal(in2));
  end
endmodule

module BBFGreaterThan(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) > $bitstoreal(in2);
  end
endmodule

module BBFGreaterThanEquals(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) >= $bitstoreal(in2);
  end
endmodule

module BBFLessThan(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) < $bitstoreal(in2);
  end
endmodule

module BBFLessThanEquals(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) <= $bitstoreal(in2);
  end
endmodule

module BBFEquals(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) == $bitstoreal(in2);
  end
endmodule

module BBFNotEquals(
    input  [0:0] clk,
    input  [0:0] reset,
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) != $bitstoreal(in2);
  end
endmodule