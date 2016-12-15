module BBFFromInt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($itor(in));
  end
endmodule

// WARNING! May cause overflow!
module BBFToInt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $rtoi($bitstoreal(in));
  end
endmodule

module BBFAdd(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) + $bitstoreal(in2));
  end
endmodule

module BBFSubtract(
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) - $bitstoreal(in2));
  end
endmodule

module BBFMultiply(
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) * $bitstoreal(in2));
  end
endmodule

module BBFDivide(
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) / $bitstoreal(in2));
  end
endmodule

module BBFGreaterThan(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) > $bitstoreal(in2);
  end
endmodule

module BBFGreaterThanEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) >= $bitstoreal(in2);
  end
endmodule

module BBFLessThan(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) < $bitstoreal(in2);
  end
endmodule

module BBFLessThanEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) <= $bitstoreal(in2);
  end
endmodule

module BBFEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) == $bitstoreal(in2);
  end
endmodule

module BBFNotEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) != $bitstoreal(in2);
  end
endmodule
