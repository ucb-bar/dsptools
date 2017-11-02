// See LICENSE for license details.

package dsptools.resizer

import dsptools.numbers.resizer.BitReducer
import org.scalatest.{FreeSpec, Matchers}

// scalastyle:off magic.number line.size.limit
class BitReducerSpec extends FreeSpec with Matchers {
  "BitReducer should pass a basic test" in {
    val angiesFirrtl =
      """key,                  ,                        type,       n,     min,             max,            mean,          stddev,    bin0,    bin1,    bin2,    bin3
        |dut._T_15             ,                      sint<5>,  227136,       4,               6,         5.00000,         0.00000,       0,       0,  227136,       0
        |dut._T_16             ,                     sint<21>,  227136,  -35923,            3757,    -15555.00000,         0.03879,       0,  221648,    5488,       0
        |dut._T_18             ,                     sint<10>,  227136,      -6,              32,        12.50000,         0.00004,       0,   18928,  208208,       0
        |dut._T_20             ,                     sint<18>,  227135,  -36947,           36525,     -2755.03318,         0.05751,       0,  132818,   94317,       0
        |dut._T_22             ,                      uint<1>,  227149,       0,               1,         0.57145,         0.00000,   97345,  129804,       0,       0
        |dut._T_24             ,                     sint<26>,  227137,-2364608,         2337600,   -165003.07643,         2.78309,       0,  173241,   53896,       0
        |dut._T_26             ,                      uint<1>,  227149,       0,               1,         0.57145,         0.00000,   97345,  129804,       0,       0
        |dut._T_28             ,                     sint<23>,  227137, -295576,          292200,    -14349.66914,         0.35000,       0,  173241,   53896,       0
        |dut._T_30             ,                      uint<1>,  227149,       0,               1,         0.14285,         0.00000,  194701,   32448,       0,       0
        |dut._T_32             ,                     sint<23>,  227149, -295576,          292200,      3873.39003,         0.18002,       0,   18974,  208175,       0
        |dut._T_34             ,                      uint<1>,  227149,       0,               1,         0.42855,         0.00000,  129804,   97345,       0,       0
        |dut._T_37             ,                     sint<33>,  227147,-302669824,       299212800, -21656368.74038,       308.44167,       0,  186726,   40421,       0
        |dut._T_39             ,                      uint<1>,  227149,       0,               1,         0.28570,         0.00000,  162252,   64897,       0,       0
        |dut._T_42             ,                     sint<33>,  227147,-302669824,       299212800,   1041885.37558,       260.26485,       0,   37948,  189199,       0
        |dut._T_44             ,                      uint<1>,  227149,       0,               1,         0.85715,         0.00000,   32448,  194701,       0,       0
        |dut._T_47             ,                     sint<23>,  227135, -295576,          292200,    -18160.24835,         0.42804,       0,  113844,  113291,       0
        |dut.io_a              ,                      sint<8>,  227136,     -95,              73,       -11.00000,         0.00021,   41664,   86016,   86016,   13440
        |dut.io_b              ,                      sint<5>,  227136,       5,               8,         6.50000,         0.00000,       0,       0,  170352,   56784
        |dut.io_c              ,                      sint<4>,  227136,      -6,              -4,        -5.00000,         0.00000,  151424,   75712,       0,       0
        |dut.io_d              ,                      sint<3>,  227136,      -4,               3,        -0.50000,         0.00001,   56784,   56784,   56784,   56784
        |dut.io_m1             ,                     sint<15>,  227137,   -9237,            9131,      -644.85688,         0.01087,     352,  172889,   53736,     160
        |dut.io_m2             ,                     sint<15>,  227137,   -9237,            9131,      -448.57002,         0.01094,     352,  172889,   53736,     160
        |dut.io_m3             ,                     sint<15>,  227149,   -9237,            9131,       121.00773,         0.00563,      88,   18886,  208135,      40
        |dut.io_m4             ,                     sint<15>,  227147,   -9237,            9131,      -661.00706,         0.00941,     264,  186462,   40301,     120
        |dut.io_m5             ,                     sint<15>,  227147,   -9237,            9131,        31.72439,         0.00794,     176,   37772,  189119,      80
        |dut.io_m6             ,                     sint<15>,  227135,   -9237,            9131,      -567.72205,         0.01338,     528,  113316,  113051,     240
        |dut.io_sel            ,                      sint<4>,  227136,      -2,               4,         1.00000,         0.00001,       0,   64896,  129792,   32448
        |dut.sel               ,                      sint<4>,  227136,      -2,               4,         1.00000,         0.00001,       0,   64896,  129792,   32448
        |dut.t1                ,                     sint<13>,  227136,    -760,             584,       -71.50000,         0.00142,       0,  127680,   99456,       0
        |dut.t2                ,                      sint<7>,  227136,     -18,              24,         2.50000,         0.00005,       0,   85176,  141960,       0
        |dut.t3                ,                     sint<14>,  227136,    -665,             511,       -60.50000,         0.00121,       0,  127680,   99456,       0
        |dut.t4                ,                      sint<8>,  227136,     -13,              32,         9.00000,         0.00005,       0,   56784,  170352,       0
        |dut.t5                ,                     sint<15>,  227136,    -537,             703,        99.50000,         0.00121,       0,   87248,  139888,       0
        |dut.t6                ,                      sint<9>,  227136,     -10,              28,         8.50000,         0.00004,       0,   47320,  179816,       0
        |dut.t7                ,                     sint<17>,  227136,  -35923,            3757,    -15555.00000,         0.03879,    3472,  218176,    5488,       0
        |dut.t8                ,                      sint<7>,  227136,      -6,              32,        12.50000,         0.00004,       0,   18928,  205842,    2366
        |dut.t9                ,                     sint<23>,  227135, -295576,          292200,    -22040.26543,         0.46012,       0,  132818,   94317,       0
        |io_a              ,                          sint<8>,  113568,     -95,              73,       -11.00000,         0.00043,   20832,   43008,   43008,    6720
        |io_b              ,                          uint<4>,  113568,       5,               8,         6.50000,         0.00001,       0,   85176,   28392,       0
        |io_c              ,                          sint<4>,  113568,      -6,              -4,        -5.00000,         0.00001,   75712,   37856,       0,       0
        |io_d              ,                          sint<3>,  113568,      -4,               3,        -0.50000,         0.00002,   28392,   28392,   28392,   28392
        |io_m1             ,                         sint<15>,  227137,   -9237,            9131,      -644.85688,         0.01087,     352,  172889,   53736,     160
        |io_m2             ,                         sint<15>,  227137,   -9237,            9131,      -448.57002,         0.01094,     352,  172889,   53736,     160
        |io_m3             ,                         sint<15>,  227149,   -9237,            9131,       121.00773,         0.00563,      88,   18886,  208135,      40
        |io_m4             ,                         sint<15>,  227147,   -9237,            9131,      -661.00706,         0.00941,     264,  186462,   40301,     120
        |io_m5             ,                         sint<15>,  227147,   -9237,            9131,        31.72439,         0.00794,     176,   37772,  189119,      80
        |io_m6             ,                         sint<15>,  227135,   -9237,            9131,      -567.72205,         0.01338,     528,  113316,  113051,     240
        |io_sel            ,                          sint<4>,  113568,      -2,               4,         1.00000,         0.00002,       0,   32448,   64896,   16224
        |reset             ,                          uint<1>,       4,       0,               1,         0.50000,         0.12500,       2,       2,       0,       0
        |
      """.stripMargin


    val data = angiesFirrtl.split("""\n""").toList.drop(1)

    val bitReducer = new BitReducer(data)
    bitReducer.run()
    val report = bitReducer.getReportString

    bitReducer.annotations.exists { a => a.value.contains("dut.t1=11")} should be (true)

    //noinspection ScalaStyle
    println(report)
  }

  "bit reduction should be limited " in {
    val instrumentedCsv  =
      """key,                  ,                        type,       n,     min,             max,            mean,          stddev,    bin0,    bin1,    bin2,    bin3
        |reduceMe              ,                      sint<16>,  227136,       -4,             65500,         4.00000,         8.00000,       0,       0,  227136,       0
      """.stripMargin

    val data = instrumentedCsv.split("\n").toList.drop(1)

    for((sigma, expected) <- Seq((1.0, 5), (2.0, 6), (8.0, 8))) {

      val bitReducer = new BitReducer(data, trimBySigma = sigma)
      bitReducer.run()
      val report = bitReducer.getReportString
      //noinspection ScalaStyle
      println(report)
      println(bitReducer.annotations.mkString("\n"))

      bitReducer.annotations.exists { a => a.value.contains(s"reduceMe=$expected") } should be(true)
    }

    val bitReducer2 = new BitReducer(data, trimBySigma = 0.0)
    bitReducer2.run()
    val report2 = bitReducer2.getReportString
    //noinspection ScalaStyle
    println(report2)
    println(bitReducer2.annotations.mkString("\n"))

    bitReducer2.annotations.length should be (0)
  }
}
