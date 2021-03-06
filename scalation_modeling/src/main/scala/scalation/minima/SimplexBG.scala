
//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 1.5
 *  @date    Sat Aug 20 23:20:26 EDT 2011
 *  @see     LICENSE (MIT style license file).
 *
 *  @see www.cise.ufl.edu/research/sparse/Morgan/bg.htm
 *  @see www.cise.ufl.edu/research/sparse/Morgan/chapter2.htm
 *  @see www.optimization-online.org/DB_FILE/2013/05/3897.pdf
 */

// U N D E R   D E V E L O P M E N T 

package scalation.minima

import scala.util.control.Breaks.{breakable, break}

import scalation.linalgebra.{Fac_LU, MatrixD, VectoD, VectorD}
import scalation.random.Randi

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SimplexBG` class solves Linear Programming (LP) problems using the Bartels-Golub
 *  (BG) Simplex Algorithm.  Given a constraint matrix 'a', constant vector 'b'
 *  and cost vector 'c', find values for the solution/decision vector 'x' that
 *  minimize the objective function 'f(x)', while satisfying all of the constraints,
 *  i.e.,
 *
 *  minimize    f(x) = c x
 *  subject to  a x <= b, x >= 0
 *
 *  The BG Simplex Algorithm performs LU Factorization/Decomposition of the
 *  basis-matrix ('ba' = 'B') rather than computing inverses ('b_inv').  It has
 *  benefits over the (Revised) Simplex Algorithm (less run-time, less memory,
 *  and much reduced chance of round off errors).
 *
 *  @param a    the constraint matrix
 *  @param b    the constant/limit vector
 *  @param c    the cost/revenue vector
 *  @param x_B  the initial basis (set of indices where x_i is in the basis)
 */
class SimplexBG (a: MatrixD, b: VectorD, c: VectorD, var x_B: Array [Int] = null)
      extends MinimizerLP
{
    private val DEBUG    = false                       // debug flag
    private val CHECK    = true                        // CHECK mode => check feasibility for each pivot
    private val M        = a.dim1                      // number of constraints (rows in a)
    private val N        = a.dim2                      // number of original variables (columns in a)
    private val MAX_ITER = 200 * N                     // maximum number of iterations

    if (b.dim != M) flaw ("constructor", "b.dim = " + b.dim + " != " + M)
    if (c.dim != N) flaw ("constructor", "c.dim = " + c.dim + " != " + N)

    if (x_B == null) x_B = setBasis ()
    private val ba       = a.selectCols (x_B)          // basis-matrix (selected columns from matrix-a)
    private val lud      = new Fac_LU (ba)             // perform an LU Decomposition on the basis-matrix
    lud.factor ()
    private val lu       = lud.factors
//  private var l_inv    = lu._1.inverse               // L-inverted
//  private var u_inv    = lu._2.inverse               // U-inverted  (b_inv = u_inv * l_inv)

    private val c_B      = c.select (x_B)              // cost for basic variables
//  private val c_       = c_B * (u_inv * l_inv)       // adjusted cost via inverse
    private val c_       = c_B                         // adjusted cost via back-substitution - FIX
//  private val b_       = (u_inv * l_inv) * b         // adjusted constants via inverse
    private val b_       = ba.solve (lu, b)            // adjusted constants via back-substitution

    private var u: VectoD = null                       // vector used for leaving
    private var z: VectoD = null                       // vector used for entering

    val checker = new CheckLP (a, b, c)

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** There are M+N variables, N decision and M slack variables, of which,
     *  for each iteration, M are chosen for a Basic Feasible Solution (BFS).
     *  The the variables not in the basis are set to zero.  Setting j to N
     *  will start with the slack variables in the basis (only works if b >= 0).
     *  @param j  the offset to start the basis
     *  @param l  the size of the basis
     */
    def setBasis (j: Int = N-M, l: Int = M): Array [Int] =
    {
        val idx = Array.ofDim [Int] (l)
        for (i <- 0 until l) idx(i) = i + j
        idx
    } // setBasis

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Find the best variable x_l to enter the basis.  Use Dantiz's Rule: index of
     *  max positive (cycling possible) z value.  Return -1 to indicate no such column.
     */
    def entering (): Int = 
    {
        z = c_ *: a - c
        z.argmaxPos ()
    } // entering 

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Find the best variable x_k to leave the basis given that x_l is entering.
     *  Determine the index of the leaving variable corresponding to ROW k using
     *  the Min-Ratio Rule.  Return -1 to indicate no such row.
     *  @param l  the variable chosen to enter the basis
     */
    def leaving (l: Int): Int =
    {
//      u = (u_inv * l_inv) * a.col(l)
        u = ba.solve (lu._1, lu._2, a.col(l))
        if (unbounded (u)) return -1
        var k = 0
        var r_min = Double.PositiveInfinity
        for (i <- 0 until M if u(i) > 0) {
            val r = b_(i) / u(i)
            if (r < r_min) { r_min = r; k = i}
        } // for
        k
    } // leaving

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Check if u <= 0., the solution is unbounded.
     *  @param u  the vector for leaving
     */
    def unbounded (u: VectoD): Boolean =
    {
        for (i <- 0 until u.dim if u(i) > 0.0) return false
        flaw ("unbounded", "the solution is UNBOUNDED")
        true
    } // unbounded

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Pivot by replacing x_k with x_l in the basis.  Update b_inv (actually lu),
     *  b_ and c_.
     *  @param k  the leaving variable
     *  @param l  the entering variable
     */
    def pivot (k: Int, l: Int)
    {
        println ("pivot: entering = " + l + " leaving = " + k)
        x_B(k) = l                                           // update basis (l replaces k)
//      b_inv(k) /= u(k)                                     // FIX
        b_(k)    /= u(k)
        for (i <- 0 until M if i != k) {
//          b_inv(i) -= b_inv(k) * u(i)                      // FIX
            b_ (i)   -= b_(k) * u(i)
        } // for
//      c_ -= b_inv(k) * z(l)                                // FIX
    } // pivot

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Solve a Linear Programming (LP) problem using the BG Simplex Algorithm.
     *  Iteratively pivot until there an optimal solution is found or it is
     *  determined that the solution is unbounded.  Return the optimal vector x.
     */
    def solve (): VectoD =
    {
        if (DEBUG) showTableau (0)                   // for iter = 0
        var k    = -1                                // the leaving variable (row)
        var l    = -1                                // the entering variable (column)

        breakable { for (it <- 1 to MAX_ITER) { 
            l = entering (); if (l == -1) break      // -1 => optimal solution found
            k = leaving (l); if (k == -1) break      // -1 => solution is unbounded
            pivot (k, l)                             // pivot: k leaves and l enters
            if (CHECK && infeasible) break           // quit if infeasible
            if (DEBUG) showTableau (it)
        }} // for

        primal                                       // return the optimal vector x
    } // solve

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Determine whether the current solution (x = primal) is still primal feasible.
     */
    def infeasible: Boolean =
    {
        if ( ! checker.isPrimalFeasible (primal)) {
            flaw ("infeasible", "solution x is no longer PRIMAL FEASIBLE")
            true
        } else {
            false
        } // if
    } // infeasible

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the primal (basis only) solution vector (x).
     */
    def primal: VectoD = ba.solve (lu._1, lu._2, b)            // (u_inv * l_inv)  * b

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the full primal solution vector (xx).
     */
    def primalFull (x: VectoD): VectorD = 
    {
        val xx = new VectorD (N)
        for (i <- 0 until x_B.length) xx(x_B(i)) = x(i)
        xx
    } // primalFull

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the dual solution vector (y).
     */
    def dual: VectoD = z.slice (N - M, N)

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the optimal objective function value (f(x) = c x).
     *  @param x  the primal solution vector
     */
    def objF (x: VectoD): Double = c.select (x_B) dot x

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Show the current BG tableau.
     *  @param iter  the number of iterations do far
     */
    def showTableau (iter: Int)
    {
        println ("showTableau: --------------------------------------------------------")
        println (this)
        println ("showTableau: after " + iter + " iterations, with limit of " + MAX_ITER)
     } // showTableau

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Convert the current BG tableau (basis, b_inv, b_, and c_) to a string.
     */
    override def toString: String =
    {
        val b_inv = a.selectCols (x_B).inverse        // compute b_inv to show tableau
        var s = new StringBuilder ()
        for (i <- 0 until M) {
            s ++= "x" + x_B(i) + " | " + b_inv(i) + " | " + b_(i) + "\n"
        } // for
        s ++= "c_ | " + c_ + "\n"
        s.toString
    } // toString

} // SimplexBG class


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SimplexBGTest` object is used to test the `SimplexBG` class.
 */
object SimplexBGTest extends App
{
    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test the BG Simplex Algorithm for solving Linear Programming problems.
     *  @param a    the constraint matrix
     *  @param b    the limit/RHS vector
     *  @param c    the cost vector
     *  @param x_B  the indices of the initial basis
     */
    def test (a: MatrixD, b: VectorD, c: VectorD, x_B: Array [Int] = null)
    {
//      val lp = new SimplexBG (a, b, c, x_B)    // test with user specified basis
        val lp = new SimplexBG (a, b, c)         // test with default basis
        val x  = lp.solve ()                          // the primal solution vector x
        val xx = lp.primalFull (x)                    // the full primal solution vector xx
        val y  = lp.dual                              // the dual solution vector y
        val f  = lp.objF (x)                          // the minimum value of the objective function

        println ("primal x = " + x)
        println ("dual   y = " + y)
        println ("objF   f = " + f)
        println ("optimal? = " + lp.check (xx, y, f))
    } // test

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test case 1:  Initialize matrix 'a', vectors 'b' and 'c', and optionally
     *  the basis 'x_B'.  For BG Simplex, matrix 'a' must be augmented with
     *  an identity matrix and vector 'c' augmented with zeros.
     *-------------------------------------------------------------------------
     *  Minimize    z = -1x_0 - 2x_1 + 1x_2 - 1x_3 - 4x_4 + 2x_5 
     *  Subject to       1x_0 + 1x_1 + 1x_2 + 1y_3 + 1y_4 + 1x_5 <= 6
     *                   2x_0 - 1x_1 - 2x_2 + 1y_3 + 0y_4 + 0x_5 <= 4
     *                   0x_0 + 0x_1 + 1x_2 + 1y_3 + 2y_4 + 1x_5 <= 4
     *  where z is the objective variable and x is the decision vector.
     *-------------------------------------------------------------------------
     *  Solution:  primal  x_1 =  4, x_7 = 8, x_4 =  2
     *             dual    y_1 = -2, y_2 = 0, y_3 = -1
     *             objF    f = -16
     *  i.e., x = (4, 8, 2), x_B = (1, 7, 4), y = (-2, 0, -1), f = -16
     *  @see Linear Programming and Network Flows, Example 5.1
     */
    def test1 ()
    {
        val a = new MatrixD ((3, 9), 1.0,  1.0,  1.0,  1.0,  1.0,  1.0,  1.0, 0.0, 0.0,    // constraint matrix
                                     2.0, -1.0, -2.0,  1.0,  0.0,  0.0,  0.0, 1.0, 0.0,
                                     0.0,  0.0,  1.0,  1.0,  2.0,  1.0,  0.0, 0.0, 1.0)
        val c   = VectorD          (-1.0, -2.0,  1.0, -1.0, -4.0,  2.0,  0.0, 0.0, 0.0)    // cost vector
        val b   = VectorD (6.0, 4.0, 4.0)                                                  // constant vector
        val x_B = Array (6, 7, 8)                                                          // starting basis
        test (a, b, c)                                                                     // x_B is optional
    } // test1

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test case 2:
     *  Solution:  x = (2/3, 10/3, 0), x_B = (0, 1, 5), f = -22/3
     *  @see Linear Programming and Network Flows, Example 5.2
     */
    def test2 ()
    {
        val a = new MatrixD ((3, 6), 1.0,  1.0,  1.0,  1.0, 0.0, 0.0,      // constraint matrix
                                    -1.0,  2.0, -2.0,  0.0, 1.0, 0.0,
                                     2.0,  1.0,  0.0,  0.0, 0.0, 1.0)
        val c   = VectorD          (-1.0, -2.0,  1.0,  0.0, 0.0, 0.0)      // cost vector
        val b   = VectorD (4.0, 6.0, 5.0)                                  // constant vector
        val x_B = Array (3, 4, 5)                                          // starting basis
        test (a, b, c)
    } // test2

   //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test case 3:
     *  Solution:  x = (1/3, 0, 13/3), x_B = (0, 2, 4), f = -17
     *  @see Linear Programming and Network Flows, Example 3.9
     */
    def test3 ()
    {
        val a = new MatrixD ((3, 6), 1.0, 1.0,  2.0,  1.0, 0.0, 0.0,      // constraint matrix
                                     1.0, 1.0, -1.0,  0.0, 1.0, 0.0,
                                    -1.0, 1.0,  1.0,  0.0, 0.0, 1.0)
        val c   = VectorD           (1.0, 1.0, -4.0,  0.0, 0.0, 0.0)      // cost vector
        val b   = VectorD (9.0, 2.0, 4.0)                                 // constant vector
        val x_B = Array (3, 4, 5)                                         // starting basis
        test (a, b, c, x_B)
    } // test3


    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test case 4:  randomly generated LP problem.
     */
    def test4 ()
    {
        val rn = Randi (0, 8)
        val (m, n) = (10, 10)
        val a = new MatrixD (m, m+n)
        val b = new VectorD (m)
        val c = new VectorD (m+n)
        for (i <- 0 until m) {
            for (j <- 0 until n) a(i, j) = rn.igen
            for (j <- n until m+n) a(i, j) = if (j-n == i) 1.0 else 0.0
        } // for
        for (i <- 0 until m) b(i) = 100.0 * (rn.igen + 1)
        for (j <- 0 until n) c(j) = -10.0 * (rn.igen + 1)
        test (a, b, c)
    } // test4

    println ("\ntest1 ========================================================")
    test1 ()
    println ("\ntest2 ========================================================")
    test2 ()
    println ("\ntest3 ========================================================")
    test3 ()
    println ("\ntest4 ========================================================")
    test4 ()

} // SimplexBGTest object

