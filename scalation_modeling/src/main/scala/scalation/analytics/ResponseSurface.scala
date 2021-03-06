
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Mustafa Nural
 *  @version 1.5
 *  @date    Sat Jan 20 16:05:52 EST 2018
 *  @see     LICENSE (MIT style license file).
 */

package scalation.analytics

import scala.collection.mutable.Set

import scalation.linalgebra.{MatriD, MatrixD, VectoD, VectorD}
import scalation.linalgebra.VectorD.one
import scalation.util.banner

import RegTechnique._

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `ResponseSurface` class uses multiple regression to fit a quadratic/cubic
 *  surface to the data.  For example in 2D, the quadratic regression equation is
 *  <p>
 *      y  =  b dot x + e  =  [b_0, ... b_k] dot [1, x_0, x_0^2, x_1, x_0*x_1, x_1^2] + e
 *  <p>
 *  @see scalation.metamodel.QuadraticFit
 *  @param x_         the input vectors/points
 *  @param y          the response vector
 *  @param cubic      the order of the surface (defaults to quadratic, else cubic)
 *  @param technique  the technique used to solve for b in x.t*x*b = x.t*y
 */
class ResponseSurface (x_ : MatriD, y: VectoD, cubic: Boolean = false, technique: RegTechnique = QR)
      extends Regression (ResponseSurface.allForms (x_, cubic), y, technique)
{
    private val n = x_.dim2                     // the dimensionality (2D, 3D, etc.) of points in matrix x_

    if (x.dim1 <= x.dim2) throw new IllegalArgumentException ("not enough data rows in matrix to use regression")

    if (y != null && x.dim1 != y.dim) flaw ("constructor", "dimensions of x and y are incompatible")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given a point 'z', use the quadratic 'rsm' regression equation to predict a
     *  value for the function at 'z'.
     *  for 1D:  b_0 + b_1*z_0 + b_2*z_0^2
     *  for 2D:  b_0 + b_1*z_0 + b_2*z_0^2 + b_3*z_1 + b_4*z_1*z_0 + b_5*z_1^2
     *  @param z  the point/vector whose functional value is to be predicted
     */
    override def predict (z: VectoD): Double =
    {
        val q   = one(1) ++ z    // augmented vector: [ 1., x(0), ..., x(n-1) ]
        var l   = 0
        var sum = 0.0
        if (cubic) for (i <- 0 to n; j <- i to n; k <- j to n) { sum += b(l) * q(i) * q(j) * q(k); l += 1 }
        else       for (i <- 0 to n; j <- i to n)              { sum += b(l) * q(i) * q(j);        l += 1 }
        sum
    } // predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Perform 'k'-fold cross-validation.
     *  @param k      the number of folds
     *  @param rando  whether to use randomized cross-validation
     */
    override def crossVal (k: Int = 10, rando: Boolean = true)
    {
        crossValidate ((x: MatriD, y: VectoD) => new ResponseSurface (x, y), k, rando)
    } // crossVa

} // ResponseSurface class


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `ResponseSurface` companion object provides methods for creating
 *  functional forms.
 */
object ResponseSurface
{
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** The number of quadratic, linear and constant forms/terms (3, 6, 10, 15, ...)
     *  of cubic, quadratic, linear and constant forms/terms (4, 10, 20, 35, ...)
     *  @param n        number of predictors
     *  @param cubic    the order of the surface (2 for quadratic, 3 for cubic)
     */
    def numTerms (n: Int, cubic: Boolean = false) =
    {
        if (cubic) (n + 1) * (n + 2) * (n + 3) / 6
        else       (n + 1) * (n + 2) / 2
    } // numTerms

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create all forms/terms for each point placing them in a new matrix.
     *  @param x      the input data matrix
     *  @param cubic  the order of the surface (2 for quadratic, 3 for cubic)
     */
    def allForms (x: MatriD, cubic: Boolean): MatriD =
    {
        val n  = x.dim2
        val nt = numTerms(n, cubic)
        if (x.dim1 < nt) throw new IllegalArgumentException ("not enough data rows in matrix to use regression")
        val xa = new MatrixD (x.dim1, nt)
        for (i <- 0 until x.dim1)
            xa(i) = if (cubic) cForms (x(i), nt, n)    // vector values for all cubic forms
                    else       qForms (x(i), nt, n)    // vector values for all quadratic forms
        xa
    } // allForms

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given a vector/point 'p', compute the values for all of its quadratic,
     *  linear and constant forms/terms, returning them as a vector.
     *  for 1D: p = (x_0)      => 'VectorD (1, x_0, x_0^2)'
     *  for 2D: p = (x_0, x_1) => 'VectorD (1, x_0, x_0^2, x_0*x_1, x_1, x_1^2)'
     *  @param p    the source vector/point for creating forms/terms
     *  @param nt   the number of terms
     *  @param n    the number of predictors
     */
    def qForms (p: VectoD, nt: Int, n: Int): VectoD =
    {
        val q = one (1) ++ p          // augmented vector: [ 1., p(0), ..., p(n-1) ]
        val z = new VectorD (nt)      // vector of all forms/terms
        var l = 0
        for (i <- 0 to n; j <- i to n) { z(l) = q(i) * q(j); l += 1 }
        z
    } // qForms

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given a vector/point 'p', compute the values for all of its cubic, quadratic,
     *  linear and constant forms/terms, returning them as a vector.
     *  for 1D: p = (x_0)      => 'VectorD (1, x_0, x_0^2, x_0^3)'
     *  for 2D: p = (x_0, x_1) => 'VectorD (1, x_0, x_0^2, x_0^3,
     *                                        x_0*x_1, x_0^2*x_1, x_0*x_1^2,
     *                                        x_1, x_1^2, x_1^3)'
     *  @param p    the source vector/point for creating forms/terms
     *  @param nt   the number of terms
     *  @param n    the number of predictors
     */
    def cForms (p: VectoD, nt: Int, n: Int): VectoD =
    {
        val q = one (1) ++ p          // augmented vector: [ 1., p(0), ..., p(n-1) ]
        val z = new VectorD (nt)      // vector of all forms/terms
        var l = 0
        for (i <- 0 to n; j <- i to n; k <- j to n) { z(l) = q(i) * q(j) * q(k); l += 1 }
        z
    } // cForms

} // ResponseSurface object


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `ResponseSurfaceTest` object is used to test the `ResponseSurface` class.
 *  > runMain scalation.analytics.ResponseSurfaceTest
 */
object ResponseSurfaceTest extends App
{
    //                            x1      x2
    val x = new MatrixD ((20, 2), 47.0,   85.4,
                                  49.0,   94.2,
                                  49.0,   95.3,
                                  50.0,   94.7,
                                  51.0,   89.4,
                                  48.0,   99.5,
                                  49.0,   99.8,
                                  47.0,   90.9,
                                  49.0,   89.2,
                                  48.0,   92.7,
                                  47.0,   94.4,
                                  49.0,   94.1,
                                  50.0,   91.6,
                                  45.0,   87.1,
                                  52.0,  101.3,
                                  46.0,   94.5,
                                  46.0,   87.0,
                                  46.0,   94.5,
                                  48.0,   90.5,
                                  56.0,   95.7)
    //  response BP
    val y = VectorD (105.0, 115.0, 116.0, 117.0, 112.0, 121.0, 121.0, 110.0, 110.0, 114.0,
                     114.0, 115.0, 114.0, 106.0, 125.0, 114.0, 106.0, 113.0, 110.0, 122.0)

    val rsr = new ResponseSurface (x, y)
    rsr.train ().eval ()
    val nTerms = ResponseSurface.numTerms (2)
    println ("nTerms      = " + nTerms)
    println ("coefficient = " + rsr.coefficient)
    println ("fitMap      = " + rsr.fitMap)

    banner ("Forward Selection Test")
    val fcols = Set (0)
    for (l <- 1 until nTerms) {
        val (x_j, b_j, fit_j) = rsr.forwardSel (fcols)        // add most predictive variable
        println (s"forward model: add x_j = $x_j with b = $b_j \n fit = $fit_j")
        fcols += x_j
    } // for

    banner ("Backward Elimination Test")
    val bcols = Set (0) ++ Array.range (1, nTerms)
    for (l <- 1 until nTerms) {
        val (x_j, b_j, fit_j) = rsr.backwardElim (bcols)     // eliminate least predictive variable
        println (s"backward model: remove x_j = $x_j with b = $b_j \n fit = $fit_j")
        bcols -= x_j
    } // for

} // ResponseSurfaceTest object

