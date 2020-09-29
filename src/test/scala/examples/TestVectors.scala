//// SPDX-License-Identifier: Apache-2.0
//
//// TODO: IFFT
//
//package examples
//
//import dsptools.numbers.DspComplex
//
//import scala.math._
//import scala.util.Random
//import dsptools._
//import breeze.math.Complex
//
//object TestVectors{
//
//  // Config for random tests vs. mostly tones (only for non base radix FFTs)
//  var randomTests = true
//
//  // Minimum absolute value of expected outputs
//  var outAbsMin: List[Double] = List()
//
//  private var in:List[List[Complex]] = List(List())
//  private var out:List[List[Complex]] = List(List())
//  def getIn(idx: Int) = in(idx)
//  def getOut(idx: Int) = out(idx)
//
//  /** Fake twiddles for full butterfly test */
//  val twiddles = List(
//    DspComplex(-0.25,-0.1),
//    DspComplex(-0.025,0.04),
//    DspComplex(-0.15,0.13),
//    DspComplex(-0.05,-0.2),
//    DspComplex(-0.005,-0.01),
//    DspComplex(-0.125,0.1)
//  )
//
//  /** Fake inputs for full butterfly test */
//  val inputs = Array(
//    DspComplex(-0.0016156958292784854,-0.0038205920103660867),
//    DspComplex(0.08396018021512272,-0.0013820177961438253),
//    DspComplex(-0.013933768021206223,0.013053573473671093),
//    DspComplex(-0.033684289651395055,0.028591395636659137),
//    DspComplex(-0.015584356598410773,0.00337343167302713),
//    DspComplex(0.015103657363909739,-0.012791286461996752),
//    DspComplex(0.01926837435299409,-0.02547371574646024)
//  )
//
//  /** Tones @ fraction of fs */
//  val realf = List(0.2,0.3,0.4,0.25)
//  val reala = List(0.25,0.15,0.02,0.03)
//
//  /** Init test vectors */
//  def apply(sizes: List[Int], frames: Int) : Tuple2[List[List[Complex]],List[List[Complex]]] = {
//    val (i,o) = (for (e <- sizes) yield {apply(e,frames)}).unzip
//    in = i
//    out = o
//    outAbsMin = o.map(
//      _.map(x => math.abs(x.real).min(math.abs(x.imag))).min
//    )
//    (i,o)
//  }
//
//  /** Create list of inputs */
//  def populateIn(FFTN: Int): List[Complex] = {
//    var inProto = Array.empty[Complex]
//    // Butterfly tests known set
//    if (FFTN <= WFTA.getValidRad.max){
//      inputs.zipWithIndex.foreach {
//        case (e, idx) => {
//          if (idx < 2 | FFTN > idx) inProto = inProto :+ e
//        }
//      }
//    }
//    // Larger tonal FFT sizes
//    else if (!randomTests){
//      for (i <- 0 until FFTN){
//        val r1 = (reala,realf).zipped.map( (a,f) => a*sin(2*Pi*f*i))
//        val r2 = r1.foldLeft(0.0001+i.toDouble/FFTN/100)(_ + _)
//        inProto = inProto :+ DspComplex(r2 + 0.001*Random.nextGaussian,0.04*Random.nextGaussian)
//      }
//    }
//    // Larger random tests
//    else {
//      // Assume FFT input has fewer significant bits than FFT output
//      val outRange = DSPFixed.toRange(DSPFixed.paramsToWidth(DspComplex.getFixedParams))
//      val std = DSPFixed.toDouble((outRange._1.abs).max(outRange._2.abs),DspComplex.getFrac)
//      inProto = Array.fill(FFTN)(DspComplex(
//        clamp(std/FFTN*Random.nextGaussian,std/FFTN),
//        clamp(std/FFTN*Random.nextGaussian,std/FFTN)
//      ))
//    }
//    inProto.toList
//  }
//
//  /** Restrict range, min absolute value */
//  def clamp(in:Double, absMax:Double): Double = {
//    val min = -1*absMax
//    val temp = if (in > absMax) absMax else if (in < min) min else in
//    // Restricts underflow (IMPORTANT! when comparing w/ double-precision calculated FFT --
//    // that input needs to be rounded)
//    val sigFrac = 1 << (DspComplex.getFrac/1.5).toInt
//    Math.round(temp * sigFrac).toDouble / sigFrac
//  }
//
//  /** Create list of outputs */
//  def populateOut(inProto: List[Complex], FFTN: Int) : List[DspComplex] = {
//
//    import breeze.signal._
//    import breeze.linalg.DenseVector
//    import breeze.math.Complex
//
//    // Using Breeze FFT Cooley Tukey instead of slow DFT == WIN!
//
//    val breezeIn = DenseVector(inProto.map(x => breeze.math.Complex(x.real,x.imag)).toArray)
//    val breezeOut = fourierTr(breezeIn)
//    breezeOut.map(x => DspComplex(x.real,x.imag)).toArray.toList
//
//    /*
//        var outProto = Array.fill(FFTN){DspComplex(0.0,0.0)}
//        // Direct (inefficient) FFT calculation
//        // exp(ix) = cos(x) + i*sin(x)
//        // exp(-j*2*pi*n*k/FFTN) = cos(-2*pi*n*k/FFTN) + i*sin(-2*pi*n*k/FFTN)
//        for (k <- 0 until FFTN;
//             n <- 0 until FFTN){
//          val s = sin(-2*Pi*n*k/FFTN)
//          val c = cos(-2*Pi*n*k/FFTN)
//          val ir = inProto(n).real
//          val ii = inProto(n).imag
//          outProto(k).real = outProto(k).real + ir * c - ii * s
//          outProto(k).imag = outProto(k).imag + ii * c + ir * s
//        }
//        outProto.toList
//    */
//
//  }
//
//  /** Create test vectors for particular FFTN */
//  def apply(FFTN : Int, frames: Int) : Tuple2[List[Complex],List[Complex]] = {
//    // Each frame is different; consists of random symbols
//    if (randomTests){
//      var inArray = Array.empty[List[Complex]]
//      var outArray = Array.empty[List[Complex]]
//      for (i <- 0 until frames){
//        val inProto = populateIn(FFTN)
//        val outProto = populateOut(inProto, FFTN)
//        inArray = inArray :+ inProto
//        outArray = outArray :+ outProto
//      }
//      (inArray.toList.flatten,outArray.toList.flatten)
//    }
//    // Each frame is the same; consists of tones
//    else {
//      val inProto = populateIn(FFTN)
//      val outProto = populateOut(inProto, FFTN)
//      // Repeat for specified # of frames
//      val inN = List.fill(frames)(inProto).flatten
//      val outN = List.fill(frames)(outProto).flatten
//      (inN, outN)
//    }
//  }
//
//}