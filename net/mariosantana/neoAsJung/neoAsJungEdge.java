package net.mariosantana.neoAsJung;

import net.mariosantana.neoAsJung.*;
import edu.uci.ics.jung.graph.ArchetypeVertex;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.ArchetypeEdge;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.ArchetypeGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.utils.UserDataContainer;
import edu.uci.ics.jung.utils.DefaultUserData;
import edu.uci.ics.jung.utils.Pair;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.lang.IllegalStateException;

/**
 * Wraps a Neo4J Node with Jungian trappings.  Attempts to be as thin a proxy as possible.
 */
public class neoAsJungEdge implements Edge
{

    /**
     * The underlying neo4j Relationship objects
     */
    protected Relationship mNeoRelationship;
    public Relationship getRelationship() { return mNeoRelationship; }

    protected neoAsJungGraph mGraphDb;
    public neoAsJungGraph getGraphDb() { return mGraphDb; }

    protected UserDataContainer mProp;
    public UserDataContainer getProp() { return mProp; }

    /**
     * We hold a copy of the Jungian from/to nodes, in order to support the
     * Jung idiom of: <code>g.addEdge(new neoAsJungEdge(v1, v2));</code>
     */
    protected neoAsJungVertex mFrom;
    public neoAsJungVertex getFrom() { return mFrom; }
    protected neoAsJungVertex mTo;
    public neoAsJungVertex getTo() { return mTo; }

    /**
     * Constructors
     */
    public neoAsJungEdge(Vertex from, Vertex to)
    {
        if (!(from instanceof neoAsJungVertex) || !(to instanceof neoAsJungVertex))
            throw new IllegalArgumentException("Arguments must be neoAsJung objects");
        mFrom = (neoAsJungVertex)from;
        mTo = (neoAsJungVertex)to;
        mProp = new DefaultUserData();
    }
    /**
     * This is normally only called only from neoAsJungGraph
     *      to wrap a Relationship that already exists in the database.
     */
    public void initialize(Relationship r, neoAsJungGraph g)
    {
        if (r.getGraphDatabase() != g.getGraphDb())
            throw new IllegalArgumentException("Relationship's graph is different than one provided");
        if (mFrom.getNode().getId() != r.getStartNode().getId() || mTo.getNode().getId() != r.getEndNode().getId())
            throw new IllegalStateException("Incident edges don't match with those previously provided");
        mGraphDb = g;
        mNeoRelationship = r;
        Iterator i = mNeoRelationship.getPropertyKeys().iterator();
        while (i.hasNext())
        {
            String k = (String)i.next();
            mProp.addUserDatum(k,mNeoRelationship.getProperty(k),new CopyAction.Shared());
        }
        mProp.addUserDatum("neoType",mNeoRelationship.getType().name(),new CopyAction.Shared());
        mProp.addUserDatum("neoURL",new String("relationship/"+mNeoRelationship.getId()),new CopyAction.Shared());
    }

    /**
     * Manage "connection."  Basically, the Jung framework supports Element
     * objects (Edges and Vertices) that don't belong to any graph yet.  These
     * Elements are then "added" to a graph.  So this stuff is to implement
     * that concept around the Neo4J objects, which don't have that concept.
     * We always keep a copy of the property data and incident vertices for
     * this edge.  Whenever we can infer what Neo4J DB we should be operating
     * in, we "connect" to it.  When we're connected, our underlying Neo4J
     * object is not null.  When we disconnect, we delete ourselves from the
     * underlying object.
     */
    /****** -----[ Gonna disable this all for now.  Let's assume that no orphans are ever made. ]
    public boolean isConnected()
    {
        return (mNeoRelationship != null);
    }
    public void connect(GraphDatabaseService g)
    {
        if (isConnected())
            return;
        mGraphDb = g;
        if (mFrom == null || mTo == null)
            throw new IllegalStateException("This neoAsJung object does not have enough information to connect");
        if (mFrom.getGraphDb() != mTo.getGraphDb())
            throw new IllegalStateException("Adjacent vertices must be in the same graph");
        if (mGraphDb == null)
            mGraphDb = mFrom.getGraphDb();
        if (mType == null)
            mType = DefaultRelationshipType.IS_CONNECTED_TO;
        mFrom.connect();
        mTo.connect();
        mNeoRelationship = mFrom.getNode().createRelationshipTo(mTo.getNode(),mType);
    }
    public void connect()
    {
        connect(mGraphDb);
    }
    public void disconnect()
    {
        mNeoRelationship.delete();
        mNeoRelationship = null;
    }

    /*****************************************************
     *****************************************************
     *****************************************************
     ***
     *** Methods for Edge
     ***
     *****************************************************
     *****************************************************
     *****************************************************/

    /**
     * Returns the vertex at the opposite end of this edge from the 
     * specified vertex <code>v</code>.  Throws 
     * <code>IllegalArgumentException</code> if <code>v</code> is 
     * not incident to this edge.
     * <P>
     * For example, if this edge connects vertices <code>a</code> and
     * <code>b</code>, <code>this.getOpposite(a)</code> returns 
     * <code>b</code>.
     * 
     * @throws IllegalArgumentException
     */
    public Vertex getOpposite(Vertex v)
    {
        if ((Vertex)mFrom == v)
            return mTo;
        if ((Vertex)mTo == v)
            return mFrom;
        throw new IllegalArgumentException("Argument must be incident to this edge");
    }
    
    /**
     * Returns a pair consisting of both incident vertices. This
     * is equivalent to getIncidentVertices, except that it returns
     * the data in the form of a Pair rather than a Set. This allows
     * easy access to the two vertices. Note that the pair is in no
     * particular order.
     */
    public Pair getEndpoints()
    {
        return new Pair(mFrom,mTo);
    }

    /*****************************************************
     *****************************************************
     *****************************************************
     ***
     *** Methods for ArchetypeEdge
     ***
     *****************************************************
     *****************************************************
     *****************************************************/

    /**
     * Returns the set of vertices which are incident to this edge.
     * Each of the vertices returned should implement 
     * <code>ArchetypeVertex</code>.
     * For example, returns the source and destination vertices of a 
     * directed edge. 
     *  
     * @return      the vertices incident to this edge
     */
    public Set getIncidentVertices()
    {
        Set s = new LinkedHashSet(2);
        s.add(mFrom);
        s.add(mTo);
        return s;
    }

    /**
     * Returns the edge in graph <code>g</code>, if any, 
     * that is equivalent to this edge.  
     * Two edges are equivalent if one of them is an ancestor 
     * (via <code>copy()</code>) of the other.
     * 
     * @see #copy(ArchetypeGraph)
     * @see ArchetypeVertex#getEqualVertex(ArchetypeGraph g)
     */
    public ArchetypeEdge getEqualEdge(ArchetypeGraph g)
    {
        return this;
    }

    /**
     * @deprecated As of version 1.4, renamed to getEqualEdge(g).
     */
    public ArchetypeEdge getEquivalentEdge(ArchetypeGraph g)
    {
        return getEqualEdge(g);
    }
    
    /**
     * Returns the number of vertices which are incident to this edge. 
     */
    public int numVertices()
    {
        return 2;
    }

    /**
     * Returns <code>true</code> if the specified vertex <code>v</code> 
     * is incident to this edge, and <code>false</code> otherwise.
     * 
     * The behavior of this method is undefined if <code>v</code> is not
     * an element of this edge's graph.
     */
    public boolean isIncident(ArchetypeVertex v)
    {
        return ((ArchetypeVertex)mFrom == v ||(ArchetypeVertex)mTo == v);
    }
    
    /**
     * Creates a copy of this edge in graph <code>g</code>.  The edge created 
     * will be equivalent to this edge: given <code>e = this.copy(g)</code>,
     * then <code>this.getEquivalentEdge(g) == e</code>,
     * and <code>this.equals(e) == true</code>.  
     * <P>
     * Given the set
     * of vertices S that are incident to this edge, the copied edge will be 
     * made incident to the set of vertices S' in <code>g</code> that are 
     * equivalent to S.  S must be copied into <code>g</code> before 
     * this edge can be copied into <code>g</code>.  If there is no 
     * such set of vertices in <code>g</code>,
     * this method throws <code>IllegalArgumentException</code>.
     * <P>
     * Thus, for example, given the following code:
     * 
     * <pre>
     *      Graph g1 = new Graph();
     *      Vertex v1 = g1.addVertex(new DirectedSparseVertex());
     *      Vertex v2 = g1.addVertex(new DirectedSparseVertex());
     *      ... 
     *      Edge e = g1.addEdge(new DirectedSparseEdge(v1, v2));
     *      Vertex v3 = v1.getEquivalentVertex(g2);
     *      Vertex v4 = v2.getEquivalentVertex(g2);
     * </pre>
     * 
     * then <code>e.copy(g2)</code> will create a directed edge 
     * connecting <code>v3</code> to <code>v4</code>
     * in <code>g2</code>.
     * 
     * @note In this partial implementation, Jungian copies are actually references to the same neoAsJung object.
     * @param g     the graph in which the copied edge will be placed
     * @return      the edge created
     * 
     * @see #getEqualEdge(ArchetypeGraph)
     * @see ArchetypeVertex#getEqualVertex(ArchetypeGraph)
     * 
     * @throws IllegalArgumentException
     */
    public ArchetypeEdge copy(ArchetypeGraph g)
    {
        if ((ArchetypeGraph)mNeoRelationship.getGraphDatabase() != g)
            throw new IllegalArgumentException("Partially implemented: the graph specified must be the one containing this neoAsJung object");
        return this;
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
        return getIncidentVertices();
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
    public void addUserDatum(Object key, Object datum, CopyAction copyAct)
    {
        if ((key instanceof String))
            try (Transaction tx = mGraphDb.getGraphDb().beginTx()) { mNeoRelationship.setProperty((String)key,datum); }
        mProp.addUserDatum(key, datum, copyAct);
    }

    /**
     * Takes the user data stored in udc and copies it to this object's 
     * user data repository, respecting each datum's CopyAction.
     * 
     * @param udc  the source of the user data to be copied into this container
     */
    public void importUserData(UserDataContainer udc)
    {
        Iterator i = udc.getUserDatumKeyIterator();
        while(i.hasNext())
        {
            Object k = i.next();
            if ((k instanceof String))
                try (Transaction tx = mGraphDb.getGraphDb().beginTx()) { mNeoRelationship.setProperty((String)k,udc.getUserDatum(k)); }
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
    public void setUserDatum(Object key, Object datum, CopyAction copyAct)
    {
        if ((key instanceof String))
            try (Transaction tx = mGraphDb.getGraphDb().beginTx()) { mNeoRelationship.setProperty((String)key,datum); }
        mProp.setUserDatum(key,datum,copyAct);
    }

    /**
     * Retrieves the object in this object's user data repository to which key
     * refers, and removes it from the repository.
     * 
     * @param key      the key of the datum to be removed
     * @return Object  the datum removed
     */
    public Object removeUserDatum(Object key)
    {
        if ((key instanceof String))
            try (Transaction tx = mGraphDb.getGraphDb().beginTx()) { mNeoRelationship.removeProperty((String)key); }
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
