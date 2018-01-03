
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Yung Long Li 
 *  @builder scalation.linalgebra.bld.BldSparseMatrix
 *  @version 1.4
 *  @date    Sat Nov 10 19:05:18 EST 2012
 *  @see     LICENSE (MIT style license file).
 */

package scalation.linalgebra

import scala.collection.mutable.LinkedEntry
import scala.io.Source.fromFile

import scalation.math.Rational.{abs => ABS, _}

import scalation.math.{Rational, oneIf}
import scalation.util.{Error, SortedLinkedHashMap}

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SparseMatrixQ` object is the companion object for the `SparseMatrixQ` class.
 */
object SparseMatrixQ
{
    /** Shorthand type definition for rows in sparse matrix
     */
    type RowMap = SortedLinkedHashMap [Int, Rational]

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a matrix and assign values from the array of vectors 'u'.
     *  @param u           the array of vectors to assign
     *  @param columnwise  whether the vectors are treated as column or row vectors
     */
    def apply (u: Array [VectoQ], columnwise: Boolean = true): SparseMatrixQ =
    {
        var x: SparseMatrixQ = null
        val u_dim = u(0).dim
        if (columnwise) {
            x = new SparseMatrixQ (u_dim, u.length)
            for (j <- 0 until u.length) x.setCol (j, u(j))    // assign column vectors
        } else {
            x = new SparseMatrixQ (u.length, u_dim)
            for (i <- 0 until u_dim) x(i) = u(i)              // assign row vectors
        } // if
        x
    } // apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a matrix and assign values from the Scala `Vector` of vectors 'u'.
     *  Assumes vectors are column-wise.
     *  @param u  the Vector of vectors to assign
     */
    def apply (u: Vector [VectoQ]): SparseMatrixQ =
    {
        val u_dim = u(0).dim
        val x = new SparseMatrixQ (u_dim, u.length)
        for (j <- 0 until u.length) x.setCol (j, u(j))        // assign column vectors
        x
    } // apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a matrix by reading from a text file, e.g., a CSV file.
     *  @param fileName  the name of file holding the data
     */
    def apply (fileName: String): SparseMatrixQ =
    {
        val sp     = ','                                          // character separating the values
        val lines  = fromFile (fileName).getLines.toArray         // get the lines from file
        val (m, n) = (lines.length, lines(0).split (sp).length)
        val x      = new SparseMatrixQ (m, n)
        for (i <- 0 until m) x(i) = VectorQ (lines(i).split (sp))
        x
    } // apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an 'm-by-n' sparse identity matrix I (ones on main diagonal, zeros elsewhere).
     *  If 'n' is <= 0, set it to 'm' for a square identity matrix.
     *  @param m  the row dimension of the matrix
     *  @param n  the column dimension of the matrix (defaults to 0 => square matrix)
     */
    def eye (m: Int, n: Int = 0): SparseMatrixQ =
    {
        val nn = if (n <= 0) m else n             // square matrix, if n <= 0
        val mn = if (m <= nn) m else nn           // length of main diagonal
        val c = new SparseMatrixQ (m, nn)
        for (i <- 0 until mn) c(i, i) = _1
        c
    } // eye

} // SparseMatrixQ object

import SparseMatrixQ.eye

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SparseMatrixQ` class stores and operates on Matrices of `Rational`s.  Rather
 *  than storing the matrix as a 2 dimensional array, it is stored as an array
 *  of sorted-linked-maps, which record all the non-zero values for each particular
 *  row, along with their j-index as (j, v) pairs.
 *  @param d1  the first/row dimension
 *  @param d2  the second/column dimension
 */
class SparseMatrixQ (val d1: Int, 
                     val d2: Int)
      extends MatriQ with Error with Serializable
{
    /** Dimension 1
     */
    lazy val dim1 = d1

    /** Dimension 2
     */
    lazy val dim2 = d2
    
    import SparseMatrixQ.RowMap

    /** Store the matrix as an array of sorted-linked-maps {(j, v)}
     *  where j is the second index and v is value to store
     */
    private val v = new Array [RowMap] (d1)
    for (i <- 0 until d1) v(i) = new RowMap ()
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a 'dim1' by 'dim2' sparse matrix from an array of sorted-linked-maps.
     *  @param dim1  the row dimension
     *  @param dim2  the column dimension
     *  @param u     the array of sorted-linked-maps
     */
    def this (dim1: Int, dim2: Int, u: Array [SparseMatrixQ.RowMap])
    {
        this (dim1, dim2)
        if (u.length != dim1) flaw ("contructor", "dimension is not matched!")
        for (i <- 0 until dim1) v(i) = u(i)
    } // constructor
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a 'dim1' by 'dim1' square sparse matrix.
     *  @param dim1  the row and column dimension
     */
    def this (dim1: Int) { this (dim1, dim1) }

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a 'dim1' by 'dim2' sparse matrix and assign each element the value 'x'.
     *  @param dim1  the row dimension
     *  @param dim2  the column dimension
     *  @param x     the scalar value to assign
     */
    def this (dim1: Int, dim2: Int, x: Rational)
    {
        this (dim1, dim2)
        if (! (x =~ _0)) for (i <- range1; j <- range2.reverse) v(i)(j) = x
    } // constructor

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a matrix from repeated values.
     *  @param dim  the (row, column) dimensions
     *  @param u    the repeated values
     */
    def this (dim: (Int, Int), u: Rational*)
    {
        this (dim._1, dim._2)
        for (i <- range1; j <- range2) v(i)(j) = u(i * dim2 + j)
    } // constructor

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a sparse matrix and assign values from matrix 'b'.
     *  @param b  the matrix of values to assign
     */
    def this (b: SparseMatrixQ)
    {
        this (b.dim1, b.dim2)
        for (i <- range1; e <- b.v(i)) this(i, e._1) = e._2
    } // constructor

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a sparse matrix and assign values from dense matrix `MatrixQ` 'b'.
     *  @param b  the matrix of values to assign
     */
    def this (b: MatrixQ)
    {
        this (b.dim1, b.dim2)
        for (i <- range1; j <- range2) this(i, j) = b(i, j)
    } // constructor

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Construct a sparse matrix and assign values from `SymTriMatrixQ` matrix 'b'.
     *  @param b  the matrix of values to assign
     */
//  def this (b: SymTriMatrixQ)
//  {
//      this (b.d1, b.d1)
//      v(0)(0) = b.dg(0)
//      v(0)(1) = b.sd(0)
//      for (i <- 1 until dim1-1) {
//          v(i)(i-1) = b.sd(i-1)
//          v(i)(i)   = b.dg(i)
//          v(i)(i+1) = b.sd(i)
//      } // for
//      v(dim1-1)(dim1-2) = b.sd(dim1-2)
//      v(dim1-1)(dim1-1) = b.dg(dim1-1)
//  } // constructor

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a clone of 'this' 'm-by-n' sparse matrix.
     */
    def copy (): SparseMatrixQ = new SparseMatrixQ (dim1, dim2, v.clone ())

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an 'm-by-n' sparse matrix with all elements initialized to zero.
     *  @param m  the number of rows
     *  @param n  the number of columns
     */
    def zero (m: Int = dim1, n: Int = dim2): SparseMatrixQ = new SparseMatrixQ (m, n)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get 'this' sparse matrix's element at the 'i,j'-th index position.
     *  @param i  the row index
     *  @param j  the column index
     */
    def apply (i: Int, j: Int): Rational = if (v(i) contains j) v(i)(j) else _0

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get 'this' sparse matrix's vector at the 'i'-th index position ('i'-th row).
     *  @param i  the row index
     */
    def apply (i: Int): VectorQ =
    {
        val a = Array.ofDim [Rational] (dim2)
        for (j <- 0 until dim2) a(j) = this(i, j)
        VectorQ (a)
    } // apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get a slice this matrix row-wise on range 'ir' and column-wise on range 'jr'.
     *  Ex: b = a(2..4, 3..5)
     *  @param ir  the row range
     *  @param jr  the column range
     */
    def apply (ir: Range, jr: Range): SparseMatrixQ = slice (ir.start, ir.end, jr.start, jr.end)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set 'this' sparse matrix's element at the 'i,j'-th index position to the scalar 'x'.
     *  Only store 'x' if it is non-zero.
     *  @param i  the row index
     *  @param j  the column index
     *  @param x  the scalar value to assign
     */
    def update (i: Int, j: Int, x: Rational) { if (! (x =~ _0)) v(i)(j) = x else v(i) -= j }

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set 'this' sparse matrix's row at the i-th index position to the vector 'u'.
     *  @param i  the row index
     *  @param u  the vector value to assign
     */
    def update (i: Int, u: VectoQ)
    {
        for (j <- 0 until u.dim) {
            val x = u(j)
            if (! (x =~ _0)) v(i)(j) = x else v(i) -= j
        } // for
    } // update

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set 'this' sparse matrix's row at the 'i'-th index position to the 
     *  sorted-linked-map 'u'.
     *  @param i  the row index
     *  @param u  the sorted-linked-map of non-zero values to assign
     */
    def update (i: Int, u: RowMap) { v(i) = u }

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set a slice 'this' sparse matrix row-wise on range 'ir' and column-wise on
     *  range 'jr'.
     *  Ex: a(2..4, 3..5) = b
     *  @param ir  the row range
     *  @param jr  the column range
     *  @param b   the matrix to assign
     */
    def update (ir: Range, jr: Range, b: MatriQ)
    {
        if (b.isInstanceOf [SparseMatrixQ]) {
            val bb = b.asInstanceOf [SparseMatrixQ]
            for (i <- ir; j <- jr) this(i, j) = b(i-ir.start, j-jr.start)
        } else {
            flaw ("update", "must convert b to a SparseMatrixQ first")
        } // if
    } // update

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set all the elements in this matrix to the scalar 'x'.
     *  @param x  the scalar value to assign
     */
    def set (x: Rational)
    {
        throw new UnsupportedOperationException ("use a dense matrix instead")
    } // set

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set all the values in this matrix as copies of the values in 2D array 'u'.
     *  @param u  the 2D array of values to assign
     */
    def set (u: Array [Array [Rational]])
    {
        for (i <- range1; j <- range2) this(i, j) = u(i)(j)
    } // set

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set this matrix's 'i'th row starting at column 'j' to the vector 'u'.
     *  @param i  the row index
     *  @param u  the vector value to assign
     *  @param j  the starting column index
     */
    def set (i: Int, u: VectoQ, j: Int = 0)
    {
        for (k <- 0 until u.dim) this(i, k+j) = u(k)
    } // set

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Convert 'this' `SparseMatrixQ` into a `MatrixI`.
     */
    def toInt: MatrixI =
    {
        val c = new MatrixI (dim1, dim2)
        for (i <- range1) c(i) = this(i).toInt
        c
    } // toInt

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Convert this sparse matrix to a dense matrix.
     *  FIX - new builder
     */
    def toDense: MatrixQ = new MatrixQ (dim1, dim2, (for (i <- range1) yield this(i).toDense ().toArray).toArray)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Slice 'this' sparse matrix row-wise 'from' to 'end'.
     *  @param from  the start row of the slice
     *  @param end   the end row of the slice
     */
    def slice (from: Int, end: Int): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (end - from, dim2)
        for (i <- 0 until c.dim1) c(i) = this(i + from)
        c
    } // slice

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Slice 'this' sparse matrix column-wise 'from' to 'end'.
     *  @param from  the start column of the slice (inclusive)
     *  @param end   the end column of the slice (exclusive)
     */
    def sliceCol (from: Int, end: Int): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, end - from)
        for (j <- 0 until c.dim2) c.setCol (j, col(j + from))
        c
    } // sliceCol

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Slice 'this' sparse matrix row-wise 'r_from' to 'r_end' and column-wise
     *  'c_from' to 'c_end'.
     *  @param r_from  the start of the row slice
     *  @param r_end   the end of the row slice
     *  @param c_from  the start of the column slice
     *  @param c_end   the end of the column slice
     */
    def slice (r_from: Int, r_end: Int, c_from: Int, c_end: Int): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (r_end - r_from, c_end - c_from)
        for (i <- 0 until c.dim1; e <- v(i+r_from)) { 
            if (c_from <= e._1 && e._1 < c_end) c.v(i)(e._1-c_from) = e._2
        } // for
        c
    } // slice

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Slice 'this' sparse matrix excluding the given row and column.
     *  @param row  the row to exclude
     *  @param col  the column to exclude
     */
    def sliceExclude (row: Int, col: Int): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1 - 1, dim2 - 1)
        for (i <- range1 if i != row) for (j <- range2 if j != col) {
            if (v(i) contains j) c.v(i - oneIf (i > row))(j - oneIf (j > col)) = this(i, j)
        } // for
        c
    } // sliceExclude

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Select rows from this matrix according to the given index/basis.
     *  @param rowIndex  the row index positions (e.g., (0, 2, 5))
     */
    def selectRows (rowIndex: Array [Int]): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (rowIndex.length, dim2)
        for (i <- c.range1) c(i) = this(rowIndex(i))
        c
    } // selectRows

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get column 'col' from the matrix, returning it as a vector.
     *  @param col   the column to extract from the matrix
     *  @param from  the position to start extracting from
     */
    def col (col: Int, from: Int = 0): VectorQ =
    {
        val u = new VectorQ (dim1 - from)
        for (i <- from until dim1) u(i-from) = this(i, col)
        u
    } // col

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set column 'col' of the matrix to a vector.
     *  @param col  the column to set
     *  @param u    the vector to assign to the column
     */
    def setCol (col: Int, u: VectoQ) { for (i <- range1) this(i, col) = u(i) }

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Select columns from this matrix according to the given index/basis.
     *  Ex: Can be used to divide a matrix into a basis and a non-basis.
     *  @param colIndex  the column index positions (e.g., (0, 2, 5))
     */
    def selectCols (colIndex: Array [Int]): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, colIndex.length)
        for (j <- c.range2) c.setCol (j, col(colIndex(j)))
        c
    } // selectCols

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Transpose 'this' sparse matrix (rows => columns).
     */
    def t: SparseMatrixQ =
    {
        val b = new SparseMatrixQ (dim2, dim1)
        for (i <- b.range2; e <- v(i)) b(e._1, i) = e._2
        b
    } // t

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Concatenate (row) vector 'u' and 'this' matrix, i.e., prepend 'u' to 'this'.
     *  @param u  the vector to be prepended as the new first row in new matrix
     */
    def +: (u: VectoQ): SparseMatrixQ =
    {
        if (u.dim != dim2) flaw ("+:", "vector does not match row dimension")
        val c = new SparseMatrixQ (dim1 + 1, dim2)
        for (i <- c.range1) c(i) = if (i == 0) u else this(i - 1)
        c
    } // +:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Concatenate (column) vector 'u' and 'this' matrix, i.e., prepend 'u' to 'this'.
     *  @param u  the vector to be prepended as the new first column in new matrix
     */
    def +^: (u: VectoQ): SparseMatrixQ =
    {
        if (u.dim != dim1) flaw ("+^:", "vector does not match column dimension")
        val c = new SparseMatrixQ (dim1, dim2 + 1)
        for (j <- c.range2) c.setCol (j, if (j == 0) u else col (j - 1))
        c
    } // +^:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Concatenate 'this' matrix and (row) vector 'u', i.e., append 'u' to 'this'.
     *  @param u  the vector to be appended as the new last row in new matrix
     */
    def :+ (u: VectoQ): SparseMatrixQ =
    {
        if (u.dim != dim2) flaw (":+", "vector does not match row dimension")
        val c = new SparseMatrixQ (dim1 + 1, dim2)
        for (i <- c.range1) c(i) = if (i < dim1) this(i) else u
        c
    } // :+

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Concatenate 'this' matrix and (column) vector 'u', i.e., append 'u' to 'this'.
     *  @param u  the vector to be appended as the new last column in new matrix
     */
    def :^+ (u: VectoQ): SparseMatrixQ =
    {
        if (u.dim != dim1) flaw (":^+", "vector does not match column dimension")
        val c = new SparseMatrixQ (dim1, dim2 + 1)
        for (j <- c.range2) c.setCol (j, if (j < dim2) col (j) else u)
        c
    } // :^+

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Concatenate (row-wise) 'this' matrix and matrix 'b'.
     *  @param b  the matrix to be concatenated as the new last rows in new matrix
     */
    def ++ (b: MatriQ): SparseMatrixQ =
    {
        if (b.dim2 != dim2) flaw ("++", "matrix b does not match row dimension")
        val c = new SparseMatrixQ (dim1 + b.dim1, dim2)
        for (i <- c.range1) c(i) = if (i < dim1) this(i) else b(i - dim1)
        c
    } // ++

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Concatenate (column-wise) 'this' matrix and matrix 'b'.
     *  @param b  the matrix to be concatenated as the new last columns in new matrix
     */
    def ++^ (b: MatriQ): SparseMatrixQ =
    {
        if (b.dim1 != dim1) flaw ("++^", "matrix b does not match column dimension")
        val c = new SparseMatrixQ (dim1, dim2 + b.dim2)
        for (j <- c.range2) c.setCol (j, if (j < dim2) col (j) else b.col (j - dim2))
        c
    } // ++^

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add 'this' sparse matrix and sparse matrix 'b'.
     *  @param b  the matrix to add (requires 'sameCrossDimensions')
     */
    def + (b: SparseMatrixQ): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (this)
        for (i <- range1; e <- b.v(i)) c(i, e._1) += e._2
        c
    } // +

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add 'this' sparse matrix and matrix 'b'. 'b' may be any subtype of `MatriQ`.
     *  Note, subtypes of `MatriQ` should also implement a more efficient version,
     *  e.g., `def + (b: SparseMatrixQ): SparseMatrixQ`.
     *  @param b  the matrix to add (requires 'sameCrossDimensions')
     */
    def + (b: MatriQ): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, dim2)
        for (i <- c.range1; j <- c.range2) c(i, j) = this(i, j) + b(i, j)
        c
    } // +

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add 'this' sparse matrix and (row) vector 'u'.
     *  @param u  the vector to add
     */
    def + (u: VectoQ): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, dim2)
        for (i <- range1; j <- range2) c(i, j) = this(i, j) + u(j)
        c
    } // +

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add 'this' sparse matrix and scalar 'x'.  Note: every element will be likely 
     *  filled, hence the return type is a dense matrix.
     *  @param x  the scalar to add
     */
    def + (x: Rational): MatrixQ =
    {
        val c = new MatrixQ (dim1, dim2)
        for (i <- range1; j <- range2) c(i, j) = this(i, j) + x
        c
    } // +

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add in-place 'this' sparse matrix and sparse matrix 'b'.
     *  @param b  the matrix to add (requires 'sameCrossDimensions')
     */
    def += (b: SparseMatrixQ): SparseMatrixQ =
    {
        for (i <- range1; e <- b.v(i)) this(i, e._1) += e._2
        this
    } // +=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add in-place 'this' sparse matrix and matrix 'b'.
     *  @param b  the matrix to add (requires 'sameCrossDimensions')
     */
    def += (b: MatriQ): SparseMatrixQ =
    {
        for (i <- range1; j <- range2) this(i, j) += b(i, j)
        this
    } // +=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add in-place this matrix and (row) vector 'u'.
     *  @param u  the vector to add
     */
    def += (u: VectoQ): SparseMatrixQ =
    {
        for (i <- range1; j <- range2) this(i, j) += u(j)
        this
    } // +=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Add in-place 'this' sparse matrix and scalar 'x'.
     *  @param x  the scalar to add
     */
    def += (x: Rational): SparseMatrixQ =
    {
        for (i <- range1; j <- range2) this(i, j) += x
        this
    } // +=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From 'this' sparse matrix subtract matrix 'b'.
     *  @param b  the sparse matrix to subtract (requires 'sameCrossDimensions')
     */
    def - (b: SparseMatrixQ): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (this)
        for (i <- range1; e <- b.v(i)) c(i, e._1) -= e._2
        c
    } // -

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From 'this' sparse matrix subtract matrix 'b'.
     *  @param b  the matrix to subtract (requires 'sameCrossDimensions')
     */
    def - (b: MatriQ): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, dim2)
        for (i <- c.range1; j <- c.range2) c(i, j) = this(i, j) - b(i, j)
        c
    } // -

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From `this` sparse matrix subtract (row) vector 'u'.
     *  @param u  the vector to subtract
     */
    def - (u: VectoQ): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, dim2)
        for (i <- range1; j <- range2) c(i, j) = this(i, j) - u(j)
        c
    } // -

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From 'this' sparse matrix subtract scalar 'x'.  Note: every element will be
     *  likely filled, hence the return type is a dense matrix.
     *  @param x  the scalar to subtract
     */
    def - (x: Rational): MatrixQ =
    {
        val c = new MatrixQ (dim1, dim2)
        for (i <- range1; j <- range2) c(i, j) = this(i, j) - x
        c
    } // -
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From 'this' sparse matrix subtract in-place sparse matrix 'b'.
     *  @param b  the sparse matrix to subtract (requires 'sameCrossDimensions')
     */
    def -= (b: SparseMatrixQ): SparseMatrixQ =
    {
        for (i <- range1; e <- b.v(i)) this(i, e._1) -= - e._2
        this
    } // -=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From 'this' sparse matrix subtract in-place matrix 'b'.
     *  @param b  the matrix to subtract (requires 'sameCrossDimensions')
     */
    def -= (b: MatriQ): SparseMatrixQ =
    {
        for (i <- range1; j <- range2) this(i, j) -= b(i, j)
        this
    } // -=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From `this` sparse matrix subtract in-place (row) vector 'u'.
     *  @param u  the vector to subtract
     */
    def -= (u: VectoQ): SparseMatrixQ =
    {
        for (i <- range1; j <- range2) this(i, j) -= u(j)
        this
    } // -=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** From 'this' sparse matrix subtract in-place scalar 'x'.
     *  @param x  the scalar to subtract
     */
    def -= (x: Rational): SparseMatrixQ =
    {
        for (i <- range1; j <- range2) this(i, j) -= x
        this
    } // -=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply 'this' sparse matrix by sparse matrix 'b', by performing a merge
     *  operation on the rows on 'this' sparse matrix and the transpose of the
     *  'b' matrix.
     *  @param b  the matrix to multiply by (requires 'sameCrossDimensions')
     */
    def * (b: SparseMatrixQ): SparseMatrixQ =
    {
        if (dim2 != b.dim1) flaw ("*", "matrix * matrix - incompatible cross dimensions")

        val c  = new SparseMatrixQ (dim1, b.dim2)
        val bt = b.t                            // transpose the b matrix (for row access)
        for (i <- c.range1) {
            var ea: LinkedEntry [Int, Rational] = null
            var eb: LinkedEntry [Int, Rational] = null
            for (j <- c.range2) {
                ea = v(i).getFirstEntry()
                eb = bt.v(j).getFirstEntry()
                var cont = false
                var itaNext = false               // more elements in row of this matrix?
                var itbNext = false               // more elements in row of bt matrix?
                var sum = _0
                if (ea != null && eb != null) cont = true
                while (cont) {
                    if (itaNext) ea = ea.later
                    if (itbNext) eb = eb.later
                    if (ea.key == eb.key) {            // matching indexes
                        sum += ea.value * eb.value
                        itaNext = true; itbNext = true
                    } else if (ea.key > eb.key) {
                        itaNext = false; itbNext = true
                    } else if (ea.key < eb.key) {
                        itaNext = true; itbNext = false
                    } // if
                    if (itaNext && ea.later == null) cont = false
                    if (itbNext && eb.later == null) cont = false
                } // while
                if (! (sum =~ _0)) c(i, j) = sum      // assign if non-zero
            } // for
        } // for
        c
    } // *
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply 'this' sparse matrix by dense matrix 'b'.
     *  @param b  the matrix to multiply by (requires 'sameCrossDimensions')
     */
    def * (b: MatriQ): SparseMatrixQ =
    {
        if (dim2 != b.dim1) flaw ("*", "matrix * matrix - incompatible cross dimensions")

        val c = new SparseMatrixQ (dim1, b.dim2)
        for (i <- c.range1; j <- c.range2) {
            var sum = _0
            for (e <- v(i)) sum += e._2 * b(e._1, j)
            c(i, j) = sum
        } // for
        c
    } // *

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply 'this' sparse matrix by vector 'u' (vector elements beyond 'dim2' ignored).
     *  @param u  the vector to multiply by
     */
    def * (u: VectoQ): VectorQ =
    {
        if (dim2 > u.dim) flaw ("*", "matrix * vector - vector dimension too small")

        val c = new VectorQ (dim1)
        for (i <- range1) {
            var sum = _0
            for (e <- v(i)) sum += e._2 * u(e._1)
            c(i) = sum
        } // for
        c
    } // *

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply 'this' sparse matrix by scalar 'x'.
     *  @param x  the scalar to multiply by
     */
    def * (x: Rational): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, dim2)
        for (i <- range1; e <- v(i)) c(i, e._1) = x * e._2
        c
    } // *
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply in-place 'this' sparse matrix by sparse matrix 'b', by performing a
     *  merge operation on the rows on 'this' sparse matrix and the transpose of
     *  the 'b' matrix.
     *  @param b  the matrix to multiply by (requires square and 'sameCrossDimensions')
     */
    def *= (b: SparseMatrixQ): SparseMatrixQ =
    {
        if (! b.isSquare)   flaw ("*=", "matrix 'b' must be square")
        if (dim2 != b.dim1) flaw ("*=", "matrix *= matrix - incompatible cross dimensions")

        val bt = b.t                              // transpose the b matrix (for row access)
        for (i <- range1) {
            var ea: LinkedEntry[Int, Rational] = null
            var eb: LinkedEntry[Int, Rational] = null
            val temp = new RowMap ()  
            for (e <- v(i)) temp(e._1) = e._2     // copy a new `SortedLinkedHashMap`
            for (j <- range2) {
                ea = temp.getFirstEntry ()
                eb = bt.v(j).getFirstEntry ()
                var cont = false
                var itaNext = false               // more elements in row of this matrix?
                var itbNext = false               // more elements in row of bt matrix?
                var sum = _0
                if (ea != null && eb != null) cont = true
                while (cont) {
                    if (itaNext) ea = ea.later
                    if (itbNext) eb = eb.later
                    if (ea.key == eb.key) {            // matching indexes
                        sum += ea.value * eb.value
                        itaNext = true; itbNext = true
                    } else if (ea.key > eb.key) {
                        itaNext = false; itbNext = true
                    } else if (ea.key < eb.key) {
                        itaNext = true; itbNext = false
                    } // if
                    if (itaNext && ea.later == null) cont = false
                    if (itbNext && eb.later == null) cont = false
                } // while
                this(i, j) = sum         // assign if non-zero
            } // for
        } // for
        this
    } // *=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply in-place 'this' sparse matrix by dense matrix 'b'.
     *  @param b  the matrix to multiply by (requires square and 'sameCrossDimensions')
     */
    def *= (b: MatriQ): SparseMatrixQ =
    {
        if (! b.isSquare)   flaw ("*=", "matrix 'b' must be square")
        if (dim2 != b.dim1) flaw ("*=", "matrix *= matrix - incompatible cross dimensions")

        for (i <- range1) {
            val temp = new RowMap ()   // save so not overwritten
            for (e <- v(i)) temp(e._1) = e._2
            for (j <- range2) {
                var sum = _0
                for (e <- temp) sum += e._2 * b(e._1, j)
                this(i, j) = sum
            } // for
        } // for
        this
    } // *=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply in-place 'this' sparse matrix by scalar 'x'.
     *  @param x  the scalar to multiply by
     */
    def *= (x: Rational): SparseMatrixQ =
    {
        for (i <- range1; e <- v(i)) this(i, e._1) = x * e._2
        this
    } // *=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the dot product of 'this' matrix and vector 'u', by first transposing
     *  'this' matrix and then multiplying by 'u' (i.e., 'a dot u = a.t * u').
     *  @param u  the vector to multiply by (requires same first dimensions)
     */
    def dot (u: VectoQ): VectorQ =
    {
        if (dim1 != u.dim) flaw ("dot", "matrix dot vector - incompatible first dimensions")

        val c = new VectorQ (dim2)
        val at = this.t                         // transpose the this matrix
        for (i <- range2) {
            var sum: Rational = _0
            for (k <- range1) sum += at(i)(k) * u(k)
            c(i) = sum
        } // for
        c
    } // dot

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the dot product of 'this' matrix with matrix 'b' to produce a vector.
     *  @param b  the second matrix of the dot product
     */
    def dot (b: MatriQ): VectorQ =
    {
        throw new UnsupportedOperationException ("matrix dot matrix - dot not implemented")
    } // dot

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the matrix dot product of 'this' matrix and matrix 'b', by first transposing
     *  'this' matrix and then multiplying by 'b' (i.e., 'a dot b = a.t * b').
     *  @param b  the matrix to multiply by (requires same first dimensions)
     */
    def mdot (b: MatriQ): SparseMatrixQ =
    {
        if (dim1 != b.dim1) flaw ("mdot", "matrix mdot matrix - incompatible first dimensions")

        val c = new SparseMatrixQ (dim2, b.dim2)
        val at = this.t                         // transpose the 'this' matrix
        for (i <- range2) {
            val at_i = at(i)                    // ith row of 'at' (column of 'a')
            for (j <- b.range2) {
                var sum = _0
                for (k <- range1) sum += at_i(k) * b(k, j)
                c(i, j) = sum
            } // for
        } // for
        c
    } // mdot

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply 'this' sparse matrix by sparse matrix 'b' using the Strassen matrix
     *  multiplication algorithm.  Both matrices ('this' and 'b') must be square.
     *  Although the algorithm is faster than the traditional cubic algorithm,
     *  its requires more memory and is often less stable (due to round-off errors).
     *  FIX:  could be make more efficient using a virtual slice 'vslice' method.
     *  @see http://en.wikipedia.org/wiki/Strassen_algorithm
     *  @param b  the matrix to multiply by (it has to be a square matrix)
     */
    def times_s (b: SparseMatrixQ): SparseMatrixQ =
    {
        if (dim2 != b.dim1) flaw ("*", "matrix * matrix - incompatible cross dimensions")

        val c = new SparseMatrixQ (dim1, dim1)  // allocate result matrix
        var d = dim1 / 2                        // half dim1
        if (d + d < dim1) d += 1                // if not even, increment by 1
        val evenDim = d + d                     // equals dim1 if even, else dim1 + 1

        // decompose to blocks (use vslice method if available)
        val a11 = slice (0, d, 0, d)
        val a12 = slice (0, d, d, evenDim)
        val a21 = slice (d, evenDim, 0, d)
        val a22 = slice (d, evenDim, d, evenDim)
        val b11 = b.slice (0, d, 0, d)
        val b12 = b.slice (0, d, d, evenDim)
        val b21 = b.slice (d, evenDim, 0, d)
        val b22 = b.slice (d, evenDim, d, evenDim)

        // compute intermediate sub-matrices
        val p1 = (a11 + a22) * (b11 + b22)
        val p2 = (a21 + a22) * b11
        val p3 = a11 * (b12 - b22)
        val p4 = a22 * (b21 - b11)
        val p5 = (a11 + a12) * b22
        val p6 = (a21 - a11) * (b11 + b12)
        val p7 = (a12 - a22) * (b21 + b22)

        for (i <- c.range1; j <- c.range2) {
            c(i, j) = if (i < d && j < d)    p1(i, j) + p4(i, j)- p5(i, j) + p7(i, j)
                   else if (i < d)           p3(i, j-d) + p5(i, j-d)
                   else if (i >= d && j < d) p2(i-d, j) + p4(i-d, j)
                   else                      p1(i-d, j-d) - p2(i-d, j-d) + p3(i-d, j-d) + p6(i-d, j-d)
        } // for
        c                                    // return result matrix
    } // times_s

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply 'this' sparse matrix by vector 'u' to produce another matrix 'a_ij * u_j'.
     *  @param u  the vector to multiply by
     */
    def ** (u: VectoQ): SparseMatrixQ =
    {
        val dm = math.min (dim2, u.dim)
        val c  = new SparseMatrixQ (dim1, dm)
        for (i <- c.range1; e <- v(i)) { val j = e._1; if (j < dm) c(i, j) = e._2 * u(j) }
        c
    } // **

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply in-place 'this' sparse matrix by vector 'u' to produce another matrix
     *  'a_ij * u_j'.
     *  @param u  the vector to multiply by
     */
    def **= (u: VectoQ): SparseMatrixQ =
    {
        if (dim2 > u.dim) flaw ("**=", "vector u not large enough")
        for (i <- range1; e <- v(i)) { val j = e._1; this(i, j) = e._2 * u(j) }
        this
    } // **=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Multiply vector 'u' by 'this' matrix to produce another matrix 'u_i * a_ij'.
     *  E.g., multiply a diagonal matrix represented as a vector by a matrix.
     *  This operator is right associative.
     *  @param u  the vector to multiply by
     */
    def **: (u: VectoQ): SparseMatrixQ =
    {
        val dm = math.min (dim2, u.dim)
        val c  = new SparseMatrixQ (dim1, dm)
        for (i <- range1; e <- v(i)) { val j = e._1; if (j < dm) c(i, j) = u(i) * e._2 }
        c
    } // **:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Divide 'this' sparse matrix by scalar 'x'.
     *  @param x  the scalar to divide by
     */
    def / (x: Rational): SparseMatrixQ =
    {
        val c = new SparseMatrixQ (dim1, dim2)
        for (i <- range1; e <- v(i)) c(i, e._1) = e._2 / x
        c
    } // /

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Divide in-place 'this' sparse matrix by scalar 'x'.
     *  @param x  the scalar to divide by
     */
    def /= (x: Rational): SparseMatrixQ =
    {
        for (i <- range1; e <- v(i)) this(i, e._1) = e._2 / x
        this
    } // /=

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Raise 'this' sparse matrix to the 'p'th power (for some integer 'p' >= 2).
     *  Caveat: should be replace by a divide and conquer algorithm.
     *  @param p  the power to raise this matrix to
     */
    def ~^ (p: Int): SparseMatrixQ =
    {
        if (p < 2)      flaw ("~^", "p must be an integer >= 2")
        if (! isSquare) flaw ("~^", "only defined on square matrices")

        var c = new SparseMatrixQ (this)
        for (i <- 0 until p-1) c *= c
        c  
    } // ~^

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Find the maximum element in 'this' sparse matrix.
     *  @param e  the ending row index (exclusive) for the search
     */
    def max (e: Int = dim1): Rational =
    {
        var x = getMaxVal(v(0))
        for (i <- 1 until e) {
            val max = getMaxVal (v(i))
            if (max > x) x = max
        } // for
        x
    } // max

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Find the maximum element in `SortedLinkHashMap` 'u' `RowMap`.
     *  @param u  the `SortedLinkHashMap` to be searched
     */
    private def getMaxVal (u: RowMap): Rational =
    {
        var x = if (u contains 0) u(0) else _0
        for (e <- u) if (e._2 > x) x = e._2
        x
    } // getMaxVal

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Find the minimum element in 'this' sparse matrix.
     *  @param e  the ending row index (exclusive) for the search
     */
    def min (e: Int = dim1): Rational =
    {
        var x = getMinVal(v(0))
        for (i <- 1 until e) {
            val min = getMinVal (v(i))
            if (min < x) x = min
        } // for
        x
    } // min

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Find the minimum element in `SortedLinkHashMap` 'u' `RowMap`.
     *  @param u  the `SortedLinkHashMap` to be searched
     */
    private def getMinVal (u: RowMap): Rational =
    {
        var x = if (u contains 0) u(0) else _0
        for (e <- u) if (e._2 < x) x = e._2
        x
    } // getMinVal

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the lower triangular of 'this' matrix (rest are zero).
     */
    def lowerT: SparseMatrixQ =
    {
        val lo = new SparseMatrixQ (dim1, dim2)
        for (i <- range1; j <- 0 to i) lo(i, j) = this(i, j)
        lo
    } // lowerT

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the upper triangular of 'this' matrix (rest are zero).
     */
    def upperT: SparseMatrixQ =
    {
        val up = new SparseMatrixQ (dim1, dim2)
        for (i <- range1; j <- i until dim2) up(i, j) = this(i, j)
        up
    } // upperT

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Factor 'this' sparse matrix into the product of lower and upper triangular
     *  matrices '(l, u)' using the 'LU' Decomposition algorithm.
     */
    def lud_npp: (SparseMatrixQ, SparseMatrixQ) =
    {
        if (! isSquare) throw new IllegalArgumentException ("lud_npp: requires a square matrix")

        val l = new SparseMatrixQ (dim1)         // lower triangular matrix
        val u = new SparseMatrixQ (this)         // upper triangular matrix (a copy of this)
        for (i <- u.range1) {
            val pivot = u(i, i)
            if (pivot =~ _0) flaw ("lud_npp", s"use Fac_LU since there is a zero pivot at row 4")
            l(i, i) = _1
            for (j <- i + 1 until u.dim2) l(i, j) = _0
            for (k <- i + 1 until u.dim1) {
                val mul = u(k, i) / pivot
                l(k, i) = mul
                for (j <- u.range2) u(k, j) -= mul * u(i, j)
            } // for
        } // for
        (l, u)
    } // lud_npp

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Factor in-place 'this' sparse matrix into the product of lower and upper
     *  triangular matrices '(l, u)' using the 'LU' Decomposition algorithm.
     */
    def lud_ip (): (SparseMatrixQ, SparseMatrixQ) =
    {
        if (! isSquare) throw new IllegalArgumentException ("lud_ip: requires a square matrix")

        val l = new SparseMatrixQ (dim1)         // lower triangular matrix
        val u = this                             // upper triangular matrix (this)
        for (i <- u.range1) {
            val pivot = u(i, i)
            if (pivot =~ _0) flaw ("lud_ip", s"use Fac_LU since there is a zero pivot at row 4")
            l(i, i) = _1
            for (j <- i + 1 until u.dim2) l(i, j) = _0
            for (k <- i + 1 until u.dim1) {
                val mul = u(k, i) / pivot
                l(k, i) = mul
                for (j <- u.range2) u(k, j) -= mul * u(i, j)
            } // for
        } // for
        (l, u)
    } // lud_ip

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Use partial pivoting to find a maximal non-zero pivot and return its row
     *  index, i.e., find the maximum element '(k, i)' below the pivot '(i, i)'.
     *  @param a  the matrix to perform partial pivoting on
     *  @param i  the row and column index for the current pivot
     */
    private def partialPivoting (a: SparseMatrixQ, i: Int): Int =
    {
        var max  = a(i, i)        // initially set to the pivot
        var kMax = i              // initially the pivot row
        for (k <- i + 1 until a.dim1 if ABS (a(k, i)) > max) {
            max  = ABS (a(k, i))
            kMax = k
        } // for
        if (kMax == i) flaw ("partialPivoting", "unable to find a non-zero pivot")
        kMax
    } // partialPivoting

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Swap the elements in rows 'i' and 'k' starting from column 'col'.
     *  @param a    the matrix containing the rows to swap
     *  @param i    the higher row  (e.g., contains a zero pivot)
     *  @param k    the lower row (e.g., contains max element below pivot)
     *  @param col  the starting column for the swap
     */
    private def swap (a: SparseMatrixQ, i: Int, k: Int, col: Int)
    {
        for (j <- col until a.dim2) {
            val tmp = a(k, j); a.v(k)(j) = a(i, j); a(i, j) = tmp
        } // for
    } // swap

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Solve for 'x' using back substitution in the equation 'u*x = y' where
     *  'this' matrix ('u') is upper triangular (see 'lud_npp' above).
     *  @param y  the constant vector
     */
    def bsolve (y: VectoQ): VectorQ =
    {
        val x = new VectorQ (dim2)                   // vector to solve for
        for (k <- x.dim - 1 to 0 by -1) {            // solve for x in u*x = y
            var sum = _0
            for (j <- k + 1 until dim2) sum += this(k, j) * x(j)
            x(k) = (y(k) - sum) / this(k, k)
        } // for
        x
    } // bsolve

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Solve for 'x' in the equation 'l*u*x = b' (see 'lud_npp' above).
     *  @param l  the lower triangular matrix
     *  @param u  the upper triangular matrix
     *  @param b  the constant vector
     */
    def solve (l: MatriQ, u: MatriQ, b: VectoQ): VectoQ =
    {
        val y = new VectorQ (l.dim2)       
        for (k <- 0 until y.dim) {                   // solve for y in l*y = b
            var sum = _0
            for (j <- 0 until k) sum += l(k, j) * y(j)
            y(k) = b(k) - sum
        } // for
        u.bsolve (y)
    } // solve

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Solve for 'x' in the equation 'a*x = b' where 'a' is 'this' matrix.
     *  @param b  the constant vector.
     */
    def solve (b: VectoQ): VectoQ = solve (lud_npp, b)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Combine 'this' sparse matrix with matrix 'b', placing them along the diagonal and
     *  filling in the bottom left and top right regions with zeros: '[this, b]'.
     *  @param b  the matrix to combine with this matrix
     */
    def diag (b: MatriQ): SparseMatrixQ =
    {
        val m = dim1 + b.dim1
        val n = dim2 + b.dim2
        val c = new SparseMatrixQ (m, n)
        for (i <- 0 until m; j <- 0 until n) {
            c(i, j) = if (i <  dim1 && j <  dim2) this(i, j)
                 else if (i >= dim1 && j >= dim2) b(i-dim1, j-dim2)
                    else                          _0
        } // for
        c
    } // diag
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Form a matrix '[Ip, this, Iq]' where 'Ir' is a 'r-by-r' identity matrix, by
     *  positioning the three matrices 'Ip', 'this' and 'Iq' along the diagonal.
     *  @param p  the size of identity matrix Ip
     *  @param q  the size of identity matrix Iq
     */
    def diag (p: Int, q: Int): SparseMatrixQ =
    {
        if (! isSymmetric) flaw ("diag", "this matrix must be symmetric")

        val n  = dim1 + p + q
        val c  = new SparseMatrixQ (n, n)
        for (i <- 0 until n; j <- 0 until n) {
            c(i, j) = if (i < p || i > p + dim1) if (i == j) _1 else _0
                    else                         this(i-p, j-p)
        } // for
        c
    } // diag

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get the 'k'th diagonal of this matrix.  Assumes 'dim2 >= dim1'.
     *  @param k  how far above the main diagonal, e.g., (-1, 0, 1) for (sub, main, super)
     */
    def getDiag (k: Int = 0): VectorQ =
    {
        val c  = new VectorQ (dim1 - math.abs (k))
        val (j, l) = (math.max (-k, 0), math.min (dim1-k, dim1))
        for (i <- j until l) c(i-j) = this(i, i+k)
        c
    } // getDiag

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set the 'k'th diagonal of this matrix to the vector 'u'.  Assumes 'dim2 >= dim1'.
     *  @param u  the vector to set the diagonal to
     *  @param k  how far above the main diagonal, e.g., (-1, 0, 1) for (sub, main, super)
     */
    def setDiag (u: VectoQ, k: Int = 0)
    {
        val (j, l) = (math.max (-k, 0), math.min (dim1-k, dim1))
        for (i <- j until l) this(i, i+k) = u(i-j)
    } // setDiag

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Set the main diagonal of this matrix to the scalar 'x'.  Assumes 'dim2 >= dim1'.
     *  @param x  the scalar to set the diagonal to
     */
    def setDiag (x: Rational) { for (i <- range1) this(i, i) = x }
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Invert 'this' sparse matrix (requires a 'squareMatrix') not using partial pivoting.
     */
    def inverse_npp: SparseMatrixQ =
    {
        throw new UnsupportedOperationException ("inverse_npp: not appropriate for sparse matrices")
/*
        val b = new SparseMatrixQ (this)           // copy this matrix into b
        val c = eye (dim1)                         // let c represent the augmentation
        for (i <- b.range1) {
            val pivot = b(i, i)
            if (pivot =~ _0) flaw ("inverse_npp", "use inverse since you have a zero pivot")
            for (j <- b.range2) {
                b(i, j) = b(i, j) / pivot
                c(i, j) = c(i, j) / pivot
            } // for
            for (k <- 0 until dim1 if k != i) {
                val mul = b(k, i)
                if (! (mul =~ _0)) {
                    for (j <- b.range2) {
                        val bval = b(i, j)
                        val cval = c(i, j)
                        if (! (bval =~ _0)) b(k, j) -= mul * bval
                        if (! (cval =~ _0)) c(k, j) -= mul * cval
                    } // for
                } // if
            } // for
        } // for
        c
*/
    } // inverse_npp
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Invert 'this' sparse matrix (requires a 'squareMatrix') using partial pivoting.
     */
    def inverse: SparseMatrixQ =
    {
        val b = new SparseMatrixQ (this)           // copy this matrix into b
        val c = eye (dim1)                         // let c represent the augmentation
        for (i <- b.range1) {
            var pivot = b(i, i)
            if (pivot =~ _0) {
                val k = partialPivoting (b, i)     // find the maximum element below pivot
                swap (b, i, k, i)                  // in b, swap rows i and k from column i
                swap (c, i, k, 0)                  // in c, swap rows i and k from column 0
                pivot = b(i, i)                    // reset the pivot
            } // if
            for (j <- b.range2) {
                b(i, j) = b(i, j) / pivot
                c(i, j) = c(i, j) / pivot
            } // for
            for (k <- 0 until dim1 if k != i) {
                val mul = b(k, i)
                if (! (mul =~ _0)) {
                    for (j <- b.range2) {
                        val bval = b(i, j)
                        val cval = c(i, j)
                        if (! (bval =~ _0)) b(k, j) -= mul * bval
                        if (! (cval =~ _0)) c(k, j) -= mul * cval
                    } // for
                } // if
            } // for
        } // for
        c
    } // inverse
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Invert in-place 'this' sparse matrix (requires a 'squareMatrix').  This version
     *  uses partial pivoting.
     */
    def inverse_ip (): SparseMatrixQ =
    {
        val b = this                               // use this matrix for b
        val c = eye (dim1)                         // let c represent the augmentation
        for (i <- b.range1) {
            var pivot = b(i, i)
            if (pivot =~ _0) {
                val k = partialPivoting (b, i)     // find the maximum element below pivot
                swap (b, i, k, i)                  // in b, swap rows i and k from column i
                swap (c, i, k, 0)                  // in c, swap rows i and k from column 0
                pivot = b(i, i)                    // reset the pivot
            } // if
            for (j <- b.range2) {
                b(i, j) = b(i, j) / pivot
                c(i, j) = c(i, j) / pivot
            } // for
            for (k <- 0 until dim1 if k != i) {
                val mul = b(k, i)
                if (! (mul =~ _0)) {
                    for (j <- b.range2) {
                        b(k, j) = b(k, j) - mul * b(i, j)
                        c(k, j) = c(k, j) - mul * c(i, j)
                    } // for
                } // if
            } // for
        } // for
        c
    } // inverse_ip

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Use Gauss-Jordan reduction on 'this' sparse matrix to make the left part embed
     *  an identity matrix.  A constraint on this m by n matrix is that n >= m.
     *  It can be used to solve 'a * x = b': augment 'a' with 'b' and call reduce.
     *  Takes '[a | b]' to '[I | x]'.
     */
    def reduce: SparseMatrixQ =
    {
        if (dim2 < dim1) flaw ("reduce", "requires n (columns) >= m (rows)")

        val b = new SparseMatrixQ (this)        // copy this matrix into b
        for (i <- b.range1) {
            var pivot = b(i, i)
            if (pivot =~ _0) {
                val k = partialPivoting (b, i)  // find the maximum element below pivot
                swap (b, i, k, i)               // in b, swap rows i and k from column i
                pivot = b(i, i)                 // reset the pivot
            } // if
            for (j <- b.range2) b(i, j) = b(i, j) / pivot
            for (k <- 0 until dim1 if k != i) {
                val mul = b(k, i)
                if (mul != 0) {
                    for (j <- b.range2) b(k, j) = b(k, j) - mul * b(i, j)
                } // if
            } // for
        } // for
        b
    } // reduce

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Use Gauss-Jordan reduction in-place on 'this' sparse matrix to make the left part
     *  embed an identity matrix.  A constraint on this m by n matrix is that n >= m.
     *  It can be used to solve 'a * x = b': augment 'a' with 'b' and call reduce.
     *  Takes '[a | b]' to '[I | x]'.
     */
    def reduce_ip ()
    {
        if (dim2 < dim1) flaw ("reduce", "requires n (columns) >= m (rows)")

        val b = this                            // use this matrix for b
        for (i <- b.range1) {
            var pivot = b(i, i)
            if (pivot =~ _0) {
                val k = partialPivoting (b, i)  // find the maximum element below pivot
                swap (b, i, k, i)               // in b, swap rows i and k from column i
                pivot = b(i, i)                 // reset the pivot
            } // if
            for (j <- b.range2) b(i, j) = b(i, j) / pivot
            for (k <- 0 until dim1 if k != i) {
                val mul = b(k, i)
                if (! (mul =~ _0)) {
                    for (j <- b.range2) b(k, j) = b(k, j) - mul * b(i, j)
                } // if
            } // for
        } // for
    } // reduce_ip

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Clean values in matrix at or below the threshold by setting them to zero.
     *  Iterative algorithms give approximate values and if very close to zero,
     *  may throw off other calculations, e.g., in computing eigenvectors.
     *  @param thres     the cutoff threshold (a small value)
     *  @param relative  whether to use relative or absolute cutoff
     */
    def clean (thres: Double, relative: Boolean = true): SparseMatrixQ =
    {
        val s = if (relative) mag else _1            // use matrix magnitude or 1
        for (i <- range1; j <- range2) if (ABS (this(i, j)) <= thres * s) this(i, j) = _0
        this
    } // clean

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the (right) nullspace of 'this' 'm-by-n' matrix (requires 'n = m+1')
     *  by performing Gauss-Jordan reduction and extracting the negation of the
     *  last column augmented by 1.
     *  <p>
     *      nullspace (a) = set of orthogonal vectors v s.t. a * v = 0
     *  <p>
     *  The left nullspace of matrix 'a' is the same as the right nullspace of 'a.t'.
     *  FIX: need a more robust algorithm for computing nullspace (@see Fac_QR.scala).
     *  FIX: remove the 'n = m+1' restriction.
     *  @see http://ocw.mit.edu/courses/mathematics/18-06sc-linear-algebra-fall-2011/ax-b-and-the-four-subspaces
     *  @see /solving-ax-0-pivot-variables-special-solutions/MIT18_06SCF11_Ses1.7sum.pdf
     */
    def nullspace: VectorQ =
    {
        if (dim2 != dim1 + 1) flaw ("nullspace", "requires n (columns) = m (rows) + 1")

        //reduce.col(dim2 - 1) * -_1 ++ _1
        var r = reduce.col(dim2 - 1) 
        r = r * -_1 
        r ++ _1
    } // nullspace
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute in-place the (right) nullspace of 'this' 'm-by-n' matrix (requires 'n = m+1')
     *  by performing Gauss-Jordan reduction and extracting the negation of the
     *  last column augmented by 1.
     *  <p>
     *      nullspace (a) = set of orthogonal vectors v s.t. a * v = 0
     *  <p>
     *  The left nullspace of matrix 'a' is the same as the right nullspace of 'a.t'.
     *  FIX: need a more robust algorithm for computing nullspace (@see Fac_QR.scala).
     *  FIX: remove the 'n = m+1' restriction.
     *  @see http://ocw.mit.edu/courses/mathematics/18-06sc-linear-algebra-fall-2011/ax-b-and-the-four-subspaces
     *  @see /solving-ax-0-pivot-variables-special-solutions/MIT18_06SCF11_Ses1.7sum.pdf
     */
    def nullspace_ip (): VectorQ =
    {
        if (dim2 != dim1 + 1) flaw ("nullspace", "requires n (columns) = m (rows) + 1")

        reduce_ip ()
        var c = col(dim2 - 1) 
        c = c * -_1 
        c ++ _1
    } // nullspace_ip

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the trace of 'this' sparse matrix, i.e., the sum of the elements on the
     *  main diagonal.  Should also equal the sum of the eigenvalues.
     *  @see Eigen.scala
     */
    def trace: Rational =
    {
        if ( ! isSquare) flaw ("trace", "trace only works on square matrices")

        var sum = _0
        for (i <- range1) sum += this(i, i)
        sum
    } // trace
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the sum of 'this' sparse matrix, i.e., the sum of its elements.
     */
    def sum: Rational =
    {
        var sum = _0
        for (i <- range1; j <- range2) sum += this(i, j)
        sum
    } // sum
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the 'abs' sum of this matrix, i.e., the sum of the absolute value
     *  of its elements.  This is useful for comparing matrices '(a - b).sumAbs'.
     */
    def sumAbs: Rational =
    {
        var sum = _0
        for (i <- range1; j <- range2) sum += ABS (this(i, j))
        sum
    } // sumAbs

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the sum of the lower triangular region of 'this' sparse matrix.
     */
    def sumLower: Rational =
    {
        var sum = _0
        for (i <- range1; j <- 0 until i) sum += this(i, j)
        sum
    } // sumLower

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Compute the determinant of 'this' sparse matrix.
     */
    def det: Rational =
    {
        if ( ! isSquare) flaw ("det", "determinant only works on square matrices")

        var sum = _0
        for (j <- range2) {
            val b = sliceExclude (0, j)   // the submatrix that excludes row 0 and column j
            sum += (if (j % 2 == 0) this(0, j) * (if (b.dim1 == 1) b(0, 0) else b.det)
                    else          - this(0, j) * (if (b.dim1 == 1) b(0, 0) else b.det))
        } // for
        sum
    } // det

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Check whether 'this' sparse matrix is nonnegative (has no negative elements).
     */
    override def isNonnegative: Boolean =
    {
        for (i <- range1; e <- v(i) if e._2 < _0) return false
        true
    } // isNonegative
    
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Check whether 'this' sparse matrix is rectangular (all rows have the same
     *  number of columns).
     */
    def isRectangular: Boolean = true

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Show the non-zero elements in 'this' sparse matrix.
     */
    override def toString: String =
    {
        var s = new StringBuilder ("\nSparseMatrixQ(\t")
        for (i <- range1) {
            s ++= v(i).toString
            s ++= (if (i < dim1 - 1) ",\n\t\t" else ")")
        } // for
        s.toString
    } // toString

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Show all elements in 'this' sparse matrix.
     */
    def showAll ()
    {
        print ("SparseMatrixQ(")
        for (i <- range1) {
            if (i > 0) print ("\t")
            print ("\t(")
            for (j <- range2) print (this(i, j).toString + (if (j < dim2 - 1) ", " else ")\n"))
        } // for
        println (")")
    } // showAll

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Write 'this' matrix to a CSV-formatted text file with name 'fileName'.
     *  @param fileName  the name of file to hold the data
     */
    def write (fileName: String)
    {
        // FIX - implement write method
    } // write
    
} // SparseMatrixQ class


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SparseMatrixQTest` object is used to test the `SparseMatrixQ` class.
 *  > runMain scalation.linalgebra.SparseMatrixQTest
 */
object SparseMatrixQTest extends App
{
    import SparseMatrixQ.RowMap

    println ("\n\tTest SparseMatrixQ operations")

    val z  = new SparseMatrixQ ((2, 2), 1, 2,
                                        3, 2)
    val b  = VectorQ (8, 7)
    val lu = z.lud_npp

    println ("z         = " + z)
    println ("z.t       = " + z.t)
    println ("z.lud_npp = " + lu)
    println ("z.solve   = " + z.solve (lu._1, lu._2, b))
    println ("z.inverse = " + z.inverse)
    println ("z.inv * b = " + z.inverse * b)
    println ("z.det     = " + z.det)
    println ("z         = " + z)

    val w = new SparseMatrixQ ((2, 3), 2, 3, 5, 
                                      -4, 2, 3)
    val v = new MatrixQ ((3, 2), 2, -4, 
                                 3,  2, 
                                 5,  3)
    
    println ("w         = " + w)
    println ("v         = " + v)
    println ("w.reduce  = " + w.reduce)

    println ("right:    w.nullspace = " + w.nullspace)
    println ("check right nullspace = " + w * w.nullspace)

    println ("left:   v.t.nullspace = " + v.t.nullspace)
    println ("check left  nullspace = " + v.t.nullspace *: v)

    for (row <- z) println ("row = " + row.deep)

    val sp = new SparseMatrixQ (3, 3, Array (new RowMap ((1,   2), (2, 100)),
                                             new RowMap ((0, 100), (2,   3)),
                                             new RowMap ((0,   4), (1, 100)) ))
    println ("sp = " + sp)

    for (i <- 0 until 3) {
        for (j <- 0 until 3) print (sp(i, j) + "\t")
        println ()
    } // for
     
} // SparseMatrixQTest object

