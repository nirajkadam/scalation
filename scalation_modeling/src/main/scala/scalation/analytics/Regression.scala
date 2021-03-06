
//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 1.5
 *  @date    Wed Feb 20 17:39:57 EST 2013
 *  @see     LICENSE (MIT style license file).
 */

package scalation.analytics

import scala.collection.immutable.ListMap
import scala.collection.mutable.Set
import scala.math.{abs, log, pow, sqrt}

import scalation.linalgebra._
import scalation.plot.Plot
import scalation.stat.StatVector.corr
import scalation.random.CDF.studentTCDF
import scalation.util.banner
import scalation.util.Unicode.sub

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RegTechnique` object defines the implementation techniques available.
 */
object RegTechnique extends Enumeration
{
    type RegTechnique = Value
    val QR, Cholesky, SVD, LU, Inverse = Value
    val techniques = Array (QR, Cholesky, SVD, LU, Inverse)
   
} // RegTechnique

import RegTechnique._

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Regression` class supports multiple linear regression.  In this case,
 *  'x' is multi-dimensional [1, x_1, ... x_k].  Fit the parameter vector 'b' in
 *  the regression equation
 *  <p>
 *      y  =  b dot x + e  =  b_0 + b_1 * x_1 + ... b_k * x_k + e
 *  <p>
 *  where 'e' represents the residuals (the part not explained by the model).
 *  Use Least-Squares (minimizing the residuals) to solve the parameter vector 'b'
 *  using the Normal Equations:
 *  <p>
 *      x.t * x * b  =  x.t * y 
 *      b  =  fac.solve (.)
 *  <p>
 *  Five factorization techniques are provided:
 *  <p>
 *      'QR'         // QR Factorization: slower, more stable (default)
 *      'Cholesky'   // Cholesky Factorization: faster, less stable (reasonable choice)
 *      'SVD'        // Singular Value Decomposition: slowest, most robust
 *      'LU'         // LU Factorization: better than Inverse
 *      'Inverse'    // Inverse/Gaussian Elimination, classical textbook technique
 *  <p>
 *  @see see.stanford.edu/materials/lsoeldsee263/05-ls.pdf
 *  Note, not intended for use when the number of degrees of freedom 'df' is negative.
 *  @see en.wikipedia.org/wiki/Degrees_of_freedom_(statistics)
 *  @param x          the input/data m-by-n matrix
 *                        (augment with a first column of ones to include intercept in model)
 *  @param y          the response m-vector
 *  @param technique  the technique used to solve for b in x.t*x*b = x.t*y
 */
class Regression (x: MatriD, y: VectoD, technique: RegTechnique = QR)
      extends PredictorMat (x, y)
{
    private val DEBUG = true                                   // debug flag

    type Fac_QR = Fac_QR_H [MatriD]                            // change as needed

    protected val fac: Factorization = technique match {       // select the factorization technique
        case QR       => new Fac_QR (x, false)                 // QR Factorization
        case Cholesky => new Fac_Cholesky (x.t * x)            // Cholesky Factorization
        case SVD      => new SVD (x)                           // Singular Value Decomposition
        case LU       => new Fac_LU (x.t * x)                  // LU Factorization
        case _        => new Fac_Inv (x.t * x)                 // Inverse Factorization
    } // match
    fac.factor ()                                              // factor the matrix, either X or X.t * X

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train the predictor by fitting the parameter vector (b-vector) in the
     *  multiple regression equation
     *  <p>
     *      yy  =  b dot x + e  =  [b_0, ... b_k] dot [1, x_1 , ... x_k] + e
     *  <p>
     *  using the ordinary least squares 'OLS' method.
     *  @param yy  the response vector to work with (defaults to y)
     */
    def train (yy: VectoD = y): Regression =
    {
        b = technique match {                                  // solve for coefficient vector b
            case QR       => fac.solve (yy)                    // R * b = Q.t * yy
            case Cholesky => fac.solve (x.t * yy)              // L * L.t * b = X.t * yy
            case SVD      => fac.solve (yy)                    // b = V * Σ^-1 * U.t * yy
            case LU       => fac.solve (x.t * yy)              // b = (X.t * X) \ X.t * yy
            case _        => fac.solve (x.t * yy)              // b = (X.t * X)^-1 * X.t * yy
        } // match
        this
    } // train

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Perform forward selection to add the most predictive variable to the existing
     *  model, returning the variable to add, the new parameter vector and the new
     *  quality of fit.  May be called repeatedly.
     *  @param cols  the columns of matrix x included in the existing model
     */
    def forwardSel (cols: Set [Int]): (Int, VectoD, VectoD) =
    {
        val ir    =  index_rSq                                       // fit(ir) is rSq
        var j_max = -1                                               // index of variable to eliminate
        var b_max =  b                                               // parameter values for best solution
        var ft_max: VectoD = VectorD.fill (fitLabel.size)(-1.0)      // optimize on quality of fit

        for (j <- 1 to k if ! (cols contains j)) {
            val cols_j = cols + j
            if (DEBUG) println ("forewardElim: cols_j = " + cols_j)
            val rg_j = new Regression (x.selectCols (cols_j.toArray), y)  // regress with x_j added
            rg_j.train (y).eval ()
            val bb = rg_j.coefficient
            val ft = rg_j.fit
            if (ft(ir) > ft_max(ir)) { j_max = j; b_max = bb; ft_max = ft }
        } // for
        (j_max, b_max, ft_max)
    } // forwardSel

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Perform backward elimination to remove the least predictive variable from
     *  the existing model, returning the variable to eliminate, the new parameter
     *  vector and the new quality of fit.  May be called repeatedly.
     *  @param cols  the columns of matrix x  included in the existing model
     */
    def backwardElim (cols: Set [Int]): (Int, VectoD, VectoD) =
    {
        val ir    =  index_rSq                                       // fit(ir) is rSq
        var j_max = -1                                               // index of variable to eliminate
        var b_max =  b                                               // parameter values for best solution
        var ft_max: VectoD = VectorD.fill (fitLabel.size)(-1.0)      // optimize on quality of fit
        val keep = m.toInt                                           // i-value large enough to not exclude any rows in slice

        for (j <- 1 to k if cols contains j) {
            val cols_j = cols - j
            if (DEBUG) println ("backwardElim: cols_j = " + cols_j)
            val rg_j = new Regression (x.selectCols (cols_j.toArray), y)  // regress with x_j removed
            rg_j.train (y).eval ()
            val bb = rg_j.coefficient
            val ft = rg_j.fit
            if (ft(ir) > ft_max(ir)) { j_max = j; b_max = bb; ft_max = ft }
        } // for
        (j_max, b_max, ft_max)
    } // backwardElim

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the Variance Inflation Factor 'VIF' for each variable to test
     *  for multi-collinearity by regressing 'xj' against the rest of the variables.
     *  A VIF over 10 indicates that over 90% of the variance of 'xj' can be predicted
     *  from the other variables, so 'xj' is a candidate for removal from the model.
     */
    def vif: VectoD =
    {
        val ir   = index_rSq                                         // fit(ir) is rSq
        val vifV = new VectorD (k)                                   // VIF vector
        val keep = m.toInt                                           // i-value large enough to not exclude any rows in slice

        for (j <- 1 to k) {
            val x_j  = x.col(j)                                      // x_j is jth column in x
            val rg_j = new Regression (x.sliceEx (keep, j), x_j  )   // regress with x_j removed
            rg_j.train (y).eval ()
            vifV(j-1) =  1.0 / (1.0 - rg_j.fit(ir))                  // store vif for x_1 in vifV(0)
        } // for
        vifV
    } // vif

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Perform 'k'-fold cross-validation.
     *  @param k      the number of folds
     *  @param rando  whether to use randomized cross-validation.
     */
    def crossVal (k: Int = 10, rando: Boolean = true)
    {
        crossValidate ((x: MatriD, y: VectoD) => new Regression (x, y), k, rando) 
    } // crossVal

} // Regression class


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Regression` companion object provides a testing method.
 */
object Regression
{
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test the various regression techniques.
     *  @param x  the data matrix
     *  @param y  the response vector
     *  @param z  a vector to predict
     */
    def test (x: MatriD, y: VectoD, z: VectoD)
    {
        for (tec <- techniques) {                              // use 'tec' Factorization
            banner (s"Fit the parameter vector b using $tec")
            val rg = new Regression (x, y, tec)
            rg.train (y).eval ()
            println ("b      = " + rg.coefficient)
            println ("fitMap = " + rg.fitMap)

            val yp = rg.predict (z)                            // predict y for one point
            println ("predict (" + z + ") = " + yp)

            val yyp = rg.predict (x)                           // predict y for several points
            println ("predict (" + x + ") = " + yyp)

            new Plot (y, yyp, null, tec.toString)
        } // for
    } // test

} // Regression object

import Regression._

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RegressionTest` object tests `Regression` class using the following
 *  regression equation.
 *  <p>
 *      y  =  b dot x  =  b_0 + b_1*x_1 + b_2*x_2.
 *  <p>
 *  @see statmaster.sdu.dk/courses/st111/module03/index.html
 *  > runMain scalation.analytics.RegressionTest
 */
object RegressionTest extends App
{
    // 5 data points: constant term, x_1 coordinate, x_2 coordinate
    val x = new MatrixD ((5, 3), 1.0, 36.0,  66.0,               // 5-by-3 matrix
                                 1.0, 37.0,  68.0,
                                 1.0, 47.0,  64.0,
                                 1.0, 32.0,  53.0,
                                 1.0,  1.0, 101.0)
    val y = VectorD (745.0, 895.0, 442.0, 440.0, 1598.0)
    val z = VectorD (1.0, 20.0, 80.0)

//  println ("model: y = b_0 + b_1*x_1 + b_2*x_2")
    println ("model: y = b₀ + b₁*x₁ + b₂*x₂")
    println ("x = " + x)
    println ("y = " + y)

    test (x, y, z)

} // RegressionTest object


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RegressionTest2` object tests `Regression` class using the following
 *  regression equation.
 *  <p>
 *      y = b dot x = b_0 + b_1*x1 + b_2*x_2.
 *  <p>
 *  > runMain scalation.analytics.RegressionTest2
 */
object RegressionTest2 extends App
{
    // 4 data points: constant term, x_1 coordinate, x_2 coordinate
    val x = new MatrixD ((4, 3), 1.0, 1.0, 1.0,                  // 4-by-3 matrix
                                 1.0, 1.0, 2.0,
                                 1.0, 2.0, 1.0,
                                 1.0, 2.0, 2.0)
    val y = VectorD (6.0, 8.0, 7.0, 9.0)
    val z = VectorD (1.0, 2.0, 3.0)

//  println ("model: y = b_0 + b_1*x1 + b_2*x_2")
    println ("model: y = b₀ + b₁*x₁ + b₂*x₂")
    println ("x = " + x)
    println ("y = " + y)

    test (x, y, z)

} // RegressionTest2 object


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RegressionTest3` object tests the multi-collinearity method in the
 *  `Regression` class using the following regression equation.
 *  <p>
 *      y = b dot x = b_0 + b_1*x_1 + b_2*x_2 + b_3*x_3 + b_4 * x_4
 *  <p>
 *  @see online.stat.psu.edu/online/development/stat501/12multicollinearity/05multico_vif.html
 *  @see online.stat.psu.edu/online/development/stat501/data/bloodpress.txt
 *  > runMain scalation.analytics.RegressionTest3
 */
object RegressionTest3 extends App
{
    // 20 data points:      Constant      x_1     x_2    x_3      x_4
    //                                    Age  Weight    Dur   Stress
    val x = new MatrixD ((20, 5), 1.0,   47.0,   85.4,   5.1,    33.0,     // 1
                                  1.0,   49.0,   94.2,   3.8,    14.0,     // 2
                                  1.0,   49.0,   95.3,   8.2,    10.0,     // 3
                                  1.0,   50.0,   94.7,   5.8,    99.0,     // 4
                                  1.0,   51.0,   89.4,   7.0,    95.0,     // 5
                                  1.0,   48.0,   99.5,   9.3,    10.0,     // 6
                                  1.0,   49.0,   99.8,   2.5,    42.0,     // 7
                                  1.0,   47.0,   90.9,   6.2,     8.0,     // 8
                                  1.0,   49.0,   89.2,   7.1,    62.0,     // 9
                                  1.0,   48.0,   92.7,   5.6,    35.0,     // 10

                                  1.0,   47.0,   94.4,   5.3,    90.0,     // 11
                                  1.0,   49.0,   94.1,   5.6,    21.0,     // 12
                                  1.0,   50.0,   91.6,  10.2,    47.0,     // 13
                                  1.0,   45.0,   87.1,   5.6,    80.0,     // 14
                                  1.0,   52.0,  101.3,  10.0,    98.0,     // 15
                                  1.0,   46.0,   94.5,   7.4,    95.0,     // 16
                                  1.0,   46.0,   87.0,   3.6,    18.0,     // 17
                                  1.0,   46.0,   94.5,   4.3,    12.0,     // 18
                                  1.0,   48.0,   90.5,   9.0,    99.0,     // 19
                                  1.0,   56.0,   95.7,   7.0,    99.0)     // 20
    //  response BP
    val y = VectorD (105.0, 115.0, 116.0, 117.0, 112.0, 121.0, 121.0, 110.0, 110.0, 114.0,
                     114.0, 115.0, 114.0, 106.0, 125.0, 114.0, 106.0, 113.0, 110.0, 122.0)

//  println ("model: y = b_0 + b_1*x1 + b_2*x_ + b3*x3 + b4*x42")
    println ("model: y = b₀ + b₁∙x₁ + b₂∙x₂ + b₃∙x₃ + b₄∙x₄")
    println ("x = " + x)
    println ("y = " + y)

    val z = VectorD (1.0,   46.0,   97.5,   7.0,    95.0)

    test (x, y, z)

    println ("corr (x) = " + corr (x))                   // correlations of column vectors in x

} // RegressionTest3 object


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RegressionTest4` object tests the multi-collinearity method in the
 *  `Regression` class using the following regression equation.
 *  <p>
 *      y = b dot x = b_0 + b_1*x_1 + b_2*x_2 + b_3*x_3 + b_4 * x_4
 *  <p>
 *  @see online.stat.psu.edu/online/development/stat501/12multicollinearity/05multico_vif.html
 *  @see online.stat.psu.edu/online/development/stat501/data/bloodpress.txt
 *  > runMain scalation.analytics.RegressionTest4
 */
object RegressionTest4 extends App
{
    // 20 data points:      Constant      x_1     x_2    x_3      x_4
    //                                    Age  Weight    Dur   Stress
    val x = new MatrixD ((20, 5), 1.0,   47.0,   85.4,   5.1,    33.0,
                                  1.0,   49.0,   94.2,   3.8,    14.0,
                                  1.0,   49.0,   95.3,   8.2,    10.0,
                                  1.0,   50.0,   94.7,   5.8,    99.0,
                                  1.0,   51.0,   89.4,   7.0,    95.0,
                                  1.0,   48.0,   99.5,   9.3,    10.0,
                                  1.0,   49.0,   99.8,   2.5,    42.0,
                                  1.0,   47.0,   90.9,   6.2,     8.0,
                                  1.0,   49.0,   89.2,   7.1,    62.0,
                                  1.0,   48.0,   92.7,   5.6,    35.0,
                                  1.0,   47.0,   94.4,   5.3,    90.0,
                                  1.0,   49.0,   94.1,   5.6,    21.0,
                                  1.0,   50.0,   91.6,  10.2,    47.0,
                                  1.0,   45.0,   87.1,   5.6,    80.0,
                                  1.0,   52.0,  101.3,  10.0,    98.0,
                                  1.0,   46.0,   94.5,   7.4,    95.0,
                                  1.0,   46.0,   87.0,   3.6,    18.0,
                                  1.0,   46.0,   94.5,   4.3,    12.0,
                                  1.0,   48.0,   90.5,   9.0,    99.0,
                                  1.0,   56.0,   95.7,   7.0,    99.0)
    //  response BP
    val y = VectorD (105.0, 115.0, 116.0, 117.0, 112.0, 121.0, 121.0, 110.0, 110.0, 114.0,
                     114.0, 115.0, 114.0, 106.0, 125.0, 114.0, 106.0, 113.0, 110.0, 122.0)

//  println ("model: y = b_0 + b_1*x1 + b_2*x_ + b3*x3 + b4*x42")
    println ("model: y = b₀ + b₁∙x₁ + b₂∙x₂ + b₃∙x₃ + b₄∙x₄")
    println ("x = " + x)
    println ("y = " + y)

    val z = VectorD (1.0,   46.0,   97.5,   7.0,    95.0)

    val rg = new Regression (x, y)
    rg.train ().eval ()
    banner ("Parameter Estimation and Quality of Fit")
    val b = rg.coefficient
    println ("b      = " + b)
    println ("fitMap = " + rg.fitMap)
    banner ("Full Report")
    rg.summary ()

    banner ("Collinearity Diagnostics")
    println ("corr (x) = " + corr (x))                       // correlations of column vectors in x
    println ("vif      = " + rg.vif)                         // test multi-colinearity (VIF)*/

    banner ("Forward Selection Test")
    val fcols = Set (0)
    for (l <- 1 until x.dim2) {
        val (x_j, b_j, fit_j) = rg.forwardSel (fcols)        // add most predictive variable
        println (s"forward model: add x_j = $x_j with b = $b_j \n fit = $fit_j")
        fcols += x_j
    } // for

    banner ("Backward Elimination Test")
    val bcols = Set (0) ++ Array.range (1, x.dim2)
    for (l <- 1 until x.dim2) {
        val (x_j, b_j, fit_j) = rg.backwardElim (bcols)     // eliminate least predictive variable
        println (s"backward model: remove x_j = $x_j with b = $b_j \n fit = $fit_j")
        bcols -= x_j
    } // for

    banner ("Cross-Validation")
    rg.crossVal ()

} // RegressionTest4 object


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RegressionTest5` object tests `Regression` class using the following
 *  regression equation.
 *  <p>
 *      y = b dot x = b_0 + b_1*x1 + b_2*x_2.
 *  <p>
 *  > runMain scalation.analytics.RegressionTest5
 */
object RegressionTest5 extends App
{
    // 4 data points: constant term, x_1 coordinate, x_2 coordinate
    val x = new MatrixD ((7, 3), 1.0, 1.0, 1.0,                  // 4-by-3 matrix
                                 1.0, 1.0, 2.0,
                                 1.0, 2.0, 1.0,
                                 1.0, 2.0, 2.0,
                                 1.0, 2.0, 3.0,
                                 1.0, 3.0, 2.0,
                                 1.0, 3.0, 3.0)
    val y = VectorD (6.0, 8.0, 9.0, 11.0, 13.0, 13.0, 16.0)
    val z = VectorD (1.0, 1.0, 3.0)

//  println ("model: y = b_0 + b_1*x1 + b_2*x_2")
    println ("model: y = b₀ + b₁*x₁ + b₂*x₂")
    println ("x = " + x)
    println ("y = " + y)

    test (x, y, z)

} // RegressionTest5 object

