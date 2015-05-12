package net.mariosantana.neoAsJung;

import edu.uci.ics.jung.graph.ArchetypeVertex;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.ArchetypeEdge;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.ArchetypeGraph;
import edu.uci.ics.jung.utils.UserDataContainer;
import edu.uci.ics.jung.utils.DefaultUserData;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Wraps a Neo4J Node with Jungian trappings.  Attempts to be as thin a proxy as possible.
 */
public class neoAsJungVertex implements Vertex
{

    /**
     * The underlying neo4j Node objects
     */
    protected Node mNeoNode;
    public Node getNode() { return mNeoNode; }

    protected neoAsJungGraph mGraphDb;
    public neoAsJungGraph getGraphDb() { return mGraphDb; }

    protected UserDataContainer mProp;
    public UserDataContainer getProp() { return mProp; }

    /**
     * Constructor
     */
    public neoAsJungVertex()
    {
        mGraphDb = null;
        mProp = new DefaultUserData();
    }
    /**
     * This is normally only called only from neoAsJungGraph
     *      to wrap a Node that already exists in the database.
     */
    public void initialize(Node n, neoAsJungGraph g)
    {
        if (n.getGraphDatabase() != g.getGraphDb())
            throw new IllegalArgumentException("Node's graph is different than the one provided");
        mGraphDb = g;
        mNeoNode = n;
        Iterator<String> is = mNeoNode.getPropertyKeys().iterator();
        while (is.hasNext()) {
            String k = is.next();
            mProp.addUserDatum(k,mNeoNode.getProperty(k),new CopyAction.Shared());
        }
        Iterator<Label> il = mNeoNode.getLabels().iterator();
        for (int j=0; il.hasNext(); j++)
            mProp.addUserDatum("neoLabel"+j,il.next(),new CopyAction.Shared());
        mProp.addUserDatum("neoURL",new String("node/"+mNeoNode.getId()),new CopyAction.Shared());
    }

    
    /*****************************************************
     *****************************************************
     *****************************************************
     ***
     *** Methods for Vertex
     ***
     *****************************************************
     *****************************************************
     *****************************************************/

    /**
     * Returns the set of predecessors of this vertex.  
     * A vertex <code>v</code> is a predecessor of this vertex if and only if
     * <code>v.isPredecessorOf(this)</code> returns <code>true</code>.
     * Each element of the set returned should implement <code>Vertex</code>.
     * 
     * @see ArchetypeVertex#getNeighbors()
     * @see #isPredecessorOf(Vertex)
     * @return  all unfiltered predecessors of this vertex
     */
    public Set getPredecessors() {
        Set<neoAsJungVertex> s = new HashSet<neoAsJungVertex>();
        Iterator<neoAsJungEdge> i = getInEdges().iterator();
        while (i.hasNext())
        	s.add(i.next().getFrom());
        return s;
    }

    /**
     * Returns the set of successors of this vertex.  
     * A vertex <code>v</code> is a successor of this vertex if and only if
     * <code>v.isSuccessorOf(this)</code> returns <code>true</code>.
     * Each element of the set returned should implement <code>Vertex</code>.
     * 
     * @see ArchetypeVertex#getNeighbors()
     * @see #isSuccessorOf(Vertex)
     * @return  all unfiltered successors of this vertex
     */
    public Set getSuccessors() {
        Set<neoAsJungVertex> s = new HashSet<neoAsJungVertex>();
        Iterator<neoAsJungEdge> i = getOutEdges().iterator();
        while (i.hasNext())
        	s.add(i.next().getTo());
        return s;
    }

    /**
     * Returns the set of incoming edges of this vertex.  An edge
     * <code>e</code> is an incoming edge of this vertex if and only if
     * <code>this.isDest(e)</code> returns <code>true</code>.
     * Each element of the set returned should implement <code>Edge</code>.
     * 
     * @see ArchetypeVertex#getIncidentEdges()
     * @return  all unfiltered edges whose destination is this vertex
     */
    public Set getInEdges() {
        Set<neoAsJungEdge> s = new HashSet<neoAsJungEdge>();
        Iterator<neoAsJungEdge> i = this.mGraphDb.getEdges().iterator();
        while (i.hasNext()) {
        	neoAsJungEdge najE = i.next();
        	if (najE.getTo() == this)
        		s.add(najE);
        }
        return s;
    }

    /**
     * Returns the set of outgoing edges of this vertex.  An edge 
     * <code>e</code> is an outgoing edge of this vertex if and only if
     * <code>this.isSource(e)</code> returns <code>true</code>.
     * Each element of the set returned should implement <code>Edge</code>.
     * 
     * @see ArchetypeVertex#getIncidentEdges()
     * @return  all unfiltered edges whose source is this vertex
     */
    public Set getOutEdges() {
        Set<neoAsJungEdge> s = new HashSet<neoAsJungEdge>();
        Iterator<neoAsJungEdge> i = this.mGraphDb.getEdges().iterator();
        while (i.hasNext()) {
        	neoAsJungEdge najE = i.next();
        	if (najE.getFrom() == this)
        		s.add(najE);
        }
        return s;
    }

    /**
     * Returns the number of incoming edges that are incident to this
     * vertex.
     * 
     * @see #getInEdges()
     * @see ArchetypeVertex#degree()
     * @return  the number of incoming edges of this vertex
     */
    public int inDegree()
    {
        return getInEdges().size();
    }

    /**
     * Returns the number of outgoing edges that are incident to this 
     * vertex.
     * 
     * @see #getOutEdges()
     * @see ArchetypeVertex#degree()
     * @return  the number of outgoing edges of this vertex
     */
    public int outDegree()
    {
        return getOutEdges().size();
    }
    
    /**
     * Returns the number of predecessors of this vertex.
     * 
     * @see #getPredecessors()
     * @see ArchetypeVertex#numNeighbors()
     * @since 1.1.1
     */
    public int numPredecessors()
    {
        return inDegree();
    }
    
    /**
     * Returns the number of successors of this vertex.
     * 
     * @see #getSuccessors()
     * @see ArchetypeVertex#numNeighbors()
     * @since 1.1.1
     */
    public int numSuccessors()
    {
        return outDegree();
    }
    
    /**
     * Returns <code>true</code> if this vertex is a successor of
     * the specified vertex <code>v</code>, and <code>false</code> otherwise.
     * This vertex is a successor of <code>v</code> if and only if 
     * there exists an edge <code>e</code> such that 
     * <code>v.isSource(e) == true</code> and 
     * <code>this.isDest(e) == true</code>.
     * 
     * @see ArchetypeVertex#isNeighborOf(ArchetypeVertex)
     * @see #getSuccessors()
     */
    public boolean isSuccessorOf(Vertex v) {
    	return this.getSuccessors().contains(v);
    }

    /**
     * Returns <code>true</code> if this vertex is a predecessor of
     * the specified vertex <code>v</code>, and <code>false</code> otherwise.
     * This vertex is a predecessor of <code>v</code> if and only if 
     * there exists an edge <code>e</code> such that 
     * <code>this.isSource(e) == true</code> and 
     * <code>v.isDest(e) == true</code>.
     * 
     * @see ArchetypeVertex#isNeighborOf(ArchetypeVertex)
     * @see #getPredecessors()
     */
    public boolean isPredecessorOf(Vertex v) {
    	return this.getPredecessors().contains(v);
    }

    /**
     * Returns <code>true</code> if this vertex is a source of
     * the specified edge <code>e</code>, and <code>false</code> otherwise.
     * A vertex <code>v</code> is a source of <code>e</code> if <code>e</code>
     * is an outgoing edge of <code>v</code>.
     * 
     * @see DirectedEdge#getSource()
     * @see ArchetypeVertex#isIncident(ArchetypeEdge)
     */
    public boolean isSource(Edge e) {
        if (!(e instanceof neoAsJungEdge))
            throw new IllegalArgumentException("Argument must be a neoAsJung type");
        return ((neoAsJungEdge)e).getFrom() == this;
    }

    /**
     * Returns <code>true</code> if this vertex is a destination of
     * the specified edge <code>e</code>, and <code>false</code> otherwise.
     * A vertex <code>v</code> is a destination of <code>e</code> 
     * if <code>e</code> is an incoming edge of <code>v</code>.
     * 
     * @see DirectedEdge#getDest()
     * @see ArchetypeVertex#isIncident(ArchetypeEdge)
     */
    public boolean isDest(Edge e) {
        if (!(e instanceof neoAsJungEdge))
            throw new IllegalArgumentException("Argument must be a neoAsJung type");
        return ((neoAsJungEdge)e).getTo() == this;
    }

    /**
     * Returns a directed outgoing edge from this vertex to <code>v</code>,
     * or an undirected edge that connects this vertex to <code>v</code>.  
     * (Note that a directed incoming edge from <code>v</code> to this vertex
     * will <b>not</b> be returned: only elements of the edge set returned by 
     * <code>getOutEdges()</code> will be returned by this method.)
     * If this edge is not uniquely
     * defined (that is, if the graph contains more than one edge connecting 
     * this vertex to <code>v</code>), any of these edges 
     * <code>v</code> may be returned.  <code>findEdgeSet(v)</code> may be 
     * used to return all such edges.
     * If <code>v</code> is not connected to this vertex, returns 
     * <code>null</code>.
     * 
     * @see Vertex#findEdgeSet(Vertex)
     */
    public Edge findEdge(Vertex v) {
        if (!(v instanceof neoAsJungVertex))
            throw new IllegalArgumentException("Argument must be a neoAsJung type");
        Iterator<neoAsJungEdge> i = this.getOutEdges().iterator();
        while (i.hasNext()) {
        	neoAsJungEdge najE = i.next();
        	if (najE.getTo() == this)
        		return najE;
        }
        return null;
    }

    /**
     * Returns the set of all edges that connect this vertex
     * with the specified vertex <code>v</code>.  Each edge in this set
     * will be either a directed outgoing edge from this vertex to 
     * <code>v</code>, or an undirected edge connecting this vertex to 
     * <code>v</code>.  <code>findEdge(v)</code> may be used to return
     * a single (arbitrary) element of this set.
     * If <code>v</code>
     * is not connected to this vertex, returns an empty <code>Set</code>.
     * 
     * @see Vertex#findEdge(Vertex)
     */
    public Set findEdgeSet(Vertex v) {
        if (!(v instanceof neoAsJungVertex))
            throw new IllegalArgumentException("Argument must be a neoAsJung type");
        Set<neoAsJungEdge> s = new HashSet<neoAsJungEdge>();
        Iterator<neoAsJungEdge> i = this.getOutEdges().iterator();
        while (i.hasNext()) {
        	neoAsJungEdge najE = i.next();
        	if (najE.getTo() == this)
        		s.add(najE);
        }
        return s;
    }

    /*****************************************************
     *****************************************************
     *****************************************************
     ***
     *** Methods for ArchetypeVertex
     ***
     *****************************************************
     *****************************************************
     *****************************************************/

    /**
     * Returns the set of vertices which are connected to this vertex 
     * via edges; each of these vertices should implement 
     * <code>ArchetypeVertex</code>.
     * If this vertex is connected to itself with a self-loop, then 
     * this vertex will be included in its own neighbor set.
     */
    public Set getNeighbors() {
        Set<neoAsJungVertex> s = new HashSet<neoAsJungVertex>();
        Iterator<neoAsJungEdge> i = this.getInEdges().iterator();
        while (i.hasNext())
        	s.add(i.next().getFrom());
        i = this.getOutEdges().iterator();
        while (i.hasNext())
        	s.add(i.next().getTo());
        return s;
    }
    
    /**
     * Returns the set of edges which are incident to this vertex.
     * Each of these edges should implement <code>ArchetypeEdge</code>.  
     */
    public Set getIncidentEdges() {
        Set<neoAsJungEdge> s = new HashSet<neoAsJungEdge>();
        s.addAll(this.getInEdges());
        s.addAll(this.getOutEdges());
        return s;
    }

    /**
     * Returns the number of edges incident to this vertex.  
     * Special cases of interest:
     * <ul>
     * <li> If there is only one edge that connects this vertex to
     * each of its neighbors (and vice versa), then the value returned 
     * will also be 
     * equal to the number of neighbors that this vertex has.
     * <li> If the graph is directed, then the value returned will be 
     * the sum of this vertex's indegree (the number of edges whose 
     * destination is this vertex) and its outdegree (the number
     * of edges whose source is this vertex).
     * </ul>
     * 
     * @return int  the degree of this node
     * @see ArchetypeVertex#numNeighbors
     */
    public int degree()
    {
        return getIncidentEdges().size();
    }

    /**
     * Returns the number of neighbors that this vertex has.
     * If the graph is directed, the value returned will be the
     * sum of the number of predecessors and the number of 
     * successors that this vertex has.
     * 
     * @since 1.1.1
     * @see ArchetypeVertex#degree
     */
    public int numNeighbors()
    {
        return degree();
    }

    /**
     * Returns the vertex in graph <code>g</code>, if any, that is 
     * equal to this vertex. Otherwise, returns null.
     * Two vertices are equal if one of them is an ancestor (via 
     * <code>copy()</code>) of the other.
     *  
     * @see #copy(ArchetypeGraph)
     * @see ArchetypeEdge#getEqualEdge(ArchetypeGraph)
     */
    public ArchetypeVertex getEqualVertex(ArchetypeGraph g)
    {
        if (g != mGraphDb)
            throw new IllegalArgumentException("neoAsJung doesn't support multiple active database backends");
        return this;
    }

    /**
     * @deprecated As of version 1.4, renamed to getEqualVertex(g).
     */
    public ArchetypeVertex getEquivalentVertex(ArchetypeGraph g)
    {
        return getEqualVertex(g);
    }
    
    /**
     * Returns <code>true</code> if the specified vertex <code>v</code> and
     * this vertex are each incident
     * to one or more of the same edges, and <code>false</code> otherwise.
     * 
     * The behavior of this method is undefined if <code>v</code> is not
     * an element of this vertex's graph.
     */
    public boolean isNeighborOf(ArchetypeVertex v)
    {
        return getNeighbors().contains(v);
    }

    /**
     * Returns <code>true</code> if the specified edge <code>e</code> is 
     * incident to this vertex, and <code>false</code> otherwise.
     * 
     * The behavior of this method is undefined if <code>e</code> is not
     * an element of this vertex's graph.
     */
    public boolean isIncident(ArchetypeEdge e)
    {
        return getIncidentEdges().contains(e);
    }
    

    /**
     * Creates a copy of this vertex in graph <code>g</code>.  The vertex 
     * created will be equivalent to this vertex: given 
     * <code>v = this.copy(g)</code>, then 
     * <code>this.getEquivalentVertex(g) == v</code>, and
     * <code>this.equals(v) == true</code>.  
     * 
     * @note In this partial implementation, Jungian copies are actually references to the same neoAsJung object.
     * @param g     the graph in which the copied vertex will be placed
     * @return      the vertex created
     */
    public ArchetypeVertex copy(ArchetypeGraph g)
    {
        if ((ArchetypeGraph)mGraphDb != g)
            throw new IllegalArgumentException("neoAsJung doesn't support multiple active database backends");
        return this;
    }

    /**
     * Returns an edge that connects this vertex to <code>v</code>.
     * If this edge is not uniquely
     * defined (that is, if the graph contains more than one edge connecting 
     * this vertex to <code>v</code>), any of these edges 
     * <code>v</code> may be returned.  <code>findEdgeSet(v)</code> may be 
     * used to return all such edges.
     * If <code>v</code> is not connected to this vertex, returns 
     * <code>null</code>.
     * 
     * @see ArchetypeVertex#findEdgeSet(ArchetypeVertex) 
     */
    public ArchetypeEdge findEdge(ArchetypeVertex v)
    {
        return findEdge((Vertex)v);
    }

    /**
     * Returns the set of all edges that connect this vertex
     * with the specified vertex <code>v</code>.  
     * <code>findEdge(v)</code> may be used to return
     * a single (arbitrary) element of this set.
     * If <code>v</code>
     * is not connected to this vertex, returns an empty <code>Set</code>.
     * 
     * @see ArchetypeVertex#findEdge(ArchetypeVertex)
     */
    public Set findEdgeSet(ArchetypeVertex v)
    {
        return findEdgeSet((Vertex)v);
    }

    /*****************************************************
     *****************************************************
     *****************************************************
     ***
     *** Methods for Element
     ***
     *****************************************************
     *****************************************************
     *****************************************************/

    /**
     * Returns a reference to the graph that contains this element.
     * If this element is not contained by any graph (is an "orphaned" element),
     * returns null.
     */
    public ArchetypeGraph getGraph()
    {
        return mGraphDb;
    }
    
    /**
     * Returns the set of elements that are incident to this element.
     * For a vertex this corresponds to returning the vertex's incident
     * edges; for an edge this corresponds to returning the edge's incident
     * vertices.
     */
    public Set getIncidentElements()
    {
        return getIncidentEdges();
    }

    /*****************************************************
     *****************************************************
     *****************************************************
     ***
     *** Methods for UserDataContainer
     ***
     *****************************************************
     *****************************************************
     *****************************************************/

    /**
     * Returning a reference to myself.  I'm pretty sure this isn't the Clone
     * contract, but... :-/
     */
    public Object clone() throws CloneNotSupportedException
    {
        return this;
    }
    
    /**
     * Adds the specified data with the specified key to this object's
     * user data repository, with the specified CopyAction.
     *
     * @note neoAsJung ignores the CopyAction stuff.  :P
     * @param key      the key of the datum being added
     * @param datum    the datum being added
     * @param copyAct  the CopyAction of the datum being added
     */
    public void addUserDatum(Object key, Object datum, CopyAction copyAct) {
        if ((key instanceof String))
            try (Transaction tx = mGraphDb.getGraphDb().beginTx()) {
            	mNeoNode.setProperty((String)key,datum);
            }
        mProp.addUserDatum(key, datum, copyAct);
    }

    /**
     * Takes the user data stored in udc and copies it to this object's 
     * user data repository, respecting each datum's CopyAction.
     * 
     * @param udc  the source of the user data to be copied into this container
     */
    public void importUserData(UserDataContainer udc) {
        try (Transaction tx = mGraphDb.getGraphDb().beginTx()) {
            Iterator<Object> i = udc.getUserDatumKeyIterator();
            while(i.hasNext()) {
                Object k = i.next();
                if ((k instanceof String))
                    mNeoNode.setProperty((String)k,udc.getUserDatum(k));
            }
        }
        mProp.importUserData(udc);
    }

    /**
     * Provides an iterator over this object's user data repository key set.
     */
    public Iterator getUserDatumKeyIterator()
    {
        return mProp.getUserDatumKeyIterator();
    }

    /**
     * Retrieves the CopyAction for the object stored in this object's
     * user data repository to which key refers.
     *
     * @note neoAsJung *tries* to ignore the CopyAction stuff.  Hopefully this does the trick.
     * @param key          the key of the datum whose CopyAction is requested
     * @return CopyAction  the requested CopyAction
     */
    public CopyAction getUserDatumCopyAction(Object key)
    {
        return mProp.getUserDatumCopyAction(key);
    }

    /**
     * Retrieves the object in this object's user data repository to which key
     * refers.
     *
     * @param key      the key of the datum to retrieve
     * @return Object  the datum retrieved
     */
    public Object getUserDatum(Object key)
    {
        return mProp.getUserDatum(key);
    }

    /**
     * If key refers to an existing user datum in this object's repository, 
     * that datum is replaced by the specified datum.  Otherwise this is equivalent to 
     * addUserDatum(key, data, copyAct).
     * 
     * @param key      the key of the datum being added/modified
     * @param datum    the replacement/new datum
     * @param copyAct  the CopyAction for the new (key, datum) pair
     */
    public void setUserDatum(Object key, Object datum, CopyAction copyAct) {
        if ((key instanceof String))
            try (Transaction tx = mGraphDb.getGraphDb().beginTx()) {
            	mNeoNode.setProperty((String)key,datum);
            }
        mProp.setUserDatum(key,datum,copyAct);
    }

    /**
     * Retrieves the object in this object's user data repository to which key
     * refers, and removes it from the repository.
     * 
     * @param key      the key of the datum to be removed
     * @return Object  the datum removed
     */
    public Object removeUserDatum(Object key) {
        if ((key instanceof String))
            try (Transaction tx = mGraphDb.getGraphDb().beginTx()) {
            	mNeoNode.removeProperty((String)key);
            }
        return mProp.removeUserDatum(key);
    }

    /**
     * Reports whether <code>key</code> is a key of this user data container.
     * 
     * @param key   the key to be queried
     * @return      true if <code>key</code> is present in this user data container
     */
    public boolean containsUserDatumKey(Object key)
    {
        return mProp.containsUserDatumKey(key);
    }
}
