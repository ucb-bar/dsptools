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

/** Math operations from IEEE.1364-2005 **/
module BBFLn(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($ln($bitstoreal(in)));
  end
endmodule

module BBFLog10(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($log10($bitstoreal(in)));
  end
endmodule

module BBFExp(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($exp($bitstoreal(in)));
  end
endmodule

module BBFSqrt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($sqrt($bitstoreal(in)));
  end
endmodule

module BBFPow(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($pow($bitstoreal(in1), $bitstoreal(in2)));
  end
endmodule

module BBFFloor(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($floor($bitstoreal(in)));
  end
endmodule

module BBFCeil(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($ceil($bitstoreal(in)));
  end
endmodule

/*
module BBFSin(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($sin($bitstoreal(in)));
  end
endmodule

module BBFCos(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($cos($bitstoreal(in)));
  end
endmodule

module BBFTan(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($tan($bitstoreal(in)));
  end
endmodule

module BBFASin(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($asin($bitstoreal(in)));
  end
endmodule

module BBFACos(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($acos($bitstoreal(in)));
  end
endmodule

module BBFATan(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($atan($bitstoreal(in)));
  end
endmodule

module BBFATan2(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($atan2($bitstoreal(in1), $bitstoreal(in2)));
  end
endmodule

module BBFHypot(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($hypot($bitstoreal(in1), $bitstoreal(in2)));
  end
endmodule

module BBFSinh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($sinh($bitstoreal(in)));
  end
endmodule

module BBFCosh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($cosh($bitstoreal(in)));
  end
endmodule

module BBFTanh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($tanh($bitstoreal(in)));
  end
endmodule

module BBFASinh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($asinh($bitstoreal(in)));
  end
endmodule

module BBFACosh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($acosh($bitstoreal(in)));
  end
endmodule

module BBFATanh(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($atanh($bitstoreal(in)));
  end
endmodule
*/