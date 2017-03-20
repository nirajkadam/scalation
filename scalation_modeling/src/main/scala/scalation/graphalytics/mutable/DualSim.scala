
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Matthew Saltz
 *  @version 1.3
 *  @date    Wed May 13 14:58:25 EDT 2015
 *  @see     LICENSE (MIT style license file).
 *
 *  Dual Simulation Using Mutable Sets
 */

package scalation.graphalytics.mutable

import scala.collection.mutable.{Set => SET}
import scala.reflect.ClassTag

import scalation.graphalytics.mutable.{ExampleGraphD => EX_GRAPH}

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `DualSim` class provides an implementation for Dual Graph Simulation
 *  using `Graph` and is based on `DualSim2`, which differs from `DualSim`
 *  by not using inverse adjacency sets ('pa') in order to save space.
 *  @param g  the data graph  G(V, E, l)
 *  @param q  the query graph Q(U, D, k)
 */
class DualSim [TLabel: ClassTag] (g: Graph [TLabel], q: Graph [TLabel])
      extends GraphMatcher (g, q)
{
     private val DEBUG = true                                     // debug flag

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Apply the Dual Graph Simulation pattern matching algorithm to find the mappings
     *  from the query graph 'q' to the data graph 'g'.  These are represented by a
     *  multi-valued function 'phi' that maps each query graph vertex 'u' to a
     *  set of data graph vertices '{v}'.
     */
    def mappings (): Array [SET [Int]] =
    {
        val phi = saltzDualSim (feasibleMates ())
        println (s"DualSim: phi = ${phi.deep}")
        phi
    } // mappings

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Given the mappings 'phi' produced by the 'feasibleMates' method,
     *  eliminate mappings 'u -> v' when (1) v's children fail to match u's
     *  or (2) v's parents fail to match u's.
     *  @param phi  array of mappings from a query vertex u to { graph vertices v }
     */
    def saltzDualSim (phi: Array [SET [Int]]): Array [SET [Int]] =
    {
        var alter = true
        while (alter) {                                           // check for matching children/parents
            alter = false

            for (u <- qRange; u_c <- q.ch(u)) {                   // for each u in q and its children u_c
                if (DEBUG) { println (s"for u = $u, u_c = $u_c"); showMappings (phi) }
                val newPhi = SET [Int] ()                         // subset of phi(u_c) having a parent in phi(u)

                for (v <- phi(u)) {                               // for each v in g image of u
                    val phiInt = g.ch(v) & phi(u_c)               // children of v contained in phi(u_c)
                    if (phiInt.isEmpty) {
                        phi(u) -= v                               // remove vertex v from phi(u)
                        if (phi(u).isEmpty) return phi            // no match for vertex u => no overall match
                        alter = true
                    } // if
                    // build newPhi to contain only those vertices in phi(u_c) which also have a parent in phi(u)
                    newPhi ++= phiInt
                } // for

                if (newPhi.isEmpty) return phi                    // empty newPhi => no match
                if (newPhi.size < phi(u_c).size) alter = true     // since newPhi is smaller than phi(u_c)

                if (SELF_LOOPS && u_c == u) phi(u_c) &= newPhi else phi(u_c) = newPhi
            } // for

        } // while
        phi
    } // saltzDualSim

} // DualSim class


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `DualSimTest` object is used to test the `DualSim` class.
 *  > run-main scalation.graphalytics.mutable.DualSimTest
 */
object DualSimTest extends App
{
    val g = EX_GRAPH.g1
    val q = EX_GRAPH.q1

    println (s"g.checkEdges = ${g.checkEdges}")
    g.printG ()
    println (s"q.checkEdges = ${q.checkEdges}")
    q.printG ()

    (new DualSim (g, q)).test ("DualSim")    // Dual Graph Simulation Pattern Matcher

} // DualSimTest object


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `DualSimTest2` object is used to test the `DualSim` class.
 *  > run-main scalation.graphalytics.mutable.DualSimTest2
 */
object DualSimTest2 extends App
{
    val g = EX_GRAPH.g2
    val q = EX_GRAPH.q2

    println (s"g.checkEdges = ${g.checkEdges}")
    g.printG ()
    println (s"q.checkEdges = ${q.checkEdges}")
    q.printG ()

    (new DualSim (g, q)).test ("DualSim")    // Dual Graph Simulation Pattern Matcher

} // DualSimTest2 object


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `DualSimTest3` object is used to test the 'DualSim' class.
 *  > run-main scalation.graphalytics.mutable.DualSimTest3
 */
object DualSimTest3 extends App
{
    val gGen = new GraphGen [Double] (0.0)

    val gSize     = 1000           // size of the data graph
    val qSize     =   10           // size of the query graph
    val nLabels   =  100           // number of distinct labels
    val gAvDegree =    5           // average vertex out degree for data graph
    val qAvDegree =    2           // average vertex out degree for query graph

    val g = gGen.genRandomGraph (gSize, nLabels, gAvDegree, false, "g")
    val q = gGen.genBFSQuery (qSize, qAvDegree, g, false, "q")

    println (s"q.checkEdges = ${q.checkEdges}")
    q.printG ()

    (new DualSim (g, q)).test ("DualSim")    // Dual Graph Simulation Pattern Matcher

} // DualSimTest3 object
 
