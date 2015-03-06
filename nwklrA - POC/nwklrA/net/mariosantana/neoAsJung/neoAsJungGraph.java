package net.mariosantana.neoAsJung;

import net.mariosantana.neoAsJung.*;

import edu.uci.ics.jung.graph.ArchetypeVertex;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.ArchetypeEdge;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.ArchetypeGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.event.GraphEventListener;
import edu.uci.ics.jung.graph.event.GraphEventType;

import edu.uci.ics.jung.utils.UserDataContainer;
import edu.uci.ics.jung.utils.UserDataContainer.CopyAction;
import edu.uci.ics.jung.utils.DefaultUserData;
import edu.uci.ics.jung.utils.Pair;

import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Vector;
import java.util.Iterator;
import java.lang.System;
import java.lang.UnsupportedOperationException;

/**
 * Wraps a Neo4J graph with Jungian trappings. Importantly: - allows for
 * vertices and edges to exist "disconnected" from a neo4j element
 */
public class neoAsJungGraph implements Graph {

	/**
	 * The underlying neo4j database object and tooling
	 */
	protected GraphDatabaseService mNeoGraphDb;

	public GraphDatabaseService getGraphDb() {
		return mNeoGraphDb;
	}

	protected GlobalGraphOperations mNeoGraphOp;

	public GlobalGraphOperations getGraphOp() {
		return mNeoGraphOp;
	}

	protected String mDbLocation;

	public String getDbLocation() {
		return mDbLocation;
	}

	protected String mDbProperties;

	public String getDbProperties() {
		return mDbProperties;
	}

	/**
	 * These sets are copies that might be filtered
	 */
	protected Set mVertices;
	protected Set mEdges;

	/**
	 * These sets should contain everything from the underlying Neo4J db.
	 */
	protected Set mUnfilteredVertices;
	protected Set mUnfilteredEdges;

	/**
	 * These strings define the filter to weed out.
	 *
	 * @todo use sets of pairs to allow for multiple active filters...or
	 *       implement using the Jung filtering mechanisms.
	 */
	protected String mKeyFilter;
	protected String mValFilter;

	/**
	 * Since Neo4J graphs don't have properties, we use some Jung framework
	 * stuff to enable properties in this class. But they won't be persisted to
	 * the database...
	 */
	protected DefaultUserData props;

	/**
	 * Constructors
	 */
	public neoAsJungGraph(String location, String propertyFile) {
		mDbLocation = location;
		mDbProperties = propertyFile;
		System.err.println("About to open Neo4J at " + mDbLocation);
		GraphDatabaseBuilder dbBuilder = new GraphDatabaseFactory()
				.newEmbeddedDatabaseBuilder(mDbLocation);
		dbBuilder.loadPropertiesFromFile(mDbProperties);
		mNeoGraphDb = dbBuilder.newGraphDatabase();
		registerShutdownHook(mNeoGraphDb);
		initialize();
	}

	public neoAsJungGraph(GraphDatabaseService db) {
		if (!db.isAvailable(5))
			throw new IllegalStateException(
					"Won't initialize from unusable Neo4J database");
		mNeoGraphDb = db;
		initialize();
	}

	/**
	 * This object has the "Master Copy" of neoAsJung objects. All Neo4J object
	 * creation MUST happen here. All neoAsJung object creation SHOULD happen
	 * here. To create a new Neo4J object, call the addVertex(Vertex v) or
	 * addEdge(Edge e) methods. This will create the underlying Neo4J object if
	 * necessary - but . To get a neoAsJung object wrapping a particular Neo4J
	 * object, call the getVertexFor(Node n) and getEdgeFor(Relationship r)
	 * methods. By following these rules, the neoAsJung objects will stay
	 * consistent with the underlying Neo4J database.
	 */
	private void initialize() {
		mNeoGraphOp = GlobalGraphOperations.at(mNeoGraphDb);
		props = new DefaultUserData();
		mKeyFilter = "";
		mValFilter = "";

		try (Transaction tx = mNeoGraphDb.beginTx()) {
			mVertices = new HashSet();
			Iterator i = mNeoGraphOp.getAllNodes().iterator();
			while (i.hasNext()) {
				neoAsJungVertex najV = new neoAsJungVertex();
				najV.initialize((Node) i.next(), this);
				mVertices.add(najV);
			}
			mUnfilteredVertices = new HashSet(mVertices);

			mEdges = new HashSet();
			i = mNeoGraphOp.getAllRelationships().iterator();
			while (i.hasNext()) {
				Relationship r = (Relationship) i.next();
				neoAsJungVertex najVfrom = getVertexFor(r.getStartNode());
				neoAsJungVertex najVto = getVertexFor(r.getEndNode());
				neoAsJungEdge najE = new neoAsJungEdge(najVfrom, najVto);
				najE.initialize(r, this);
				mEdges.add(najE);
			}
			mUnfilteredEdges = new HashSet(mEdges);

			tx.success();
		}
	}

	/**
	 * Methods to grab from my stash of neoAsJung objects, to keep from having
	 * to create them prolifically
	 */
	public neoAsJungVertex getVertexFor(Node n) {
		if (n == null)
			return null;
		long nId = n.getId();
		Iterator i = mVertices.iterator();
		while (i.hasNext()) {
			neoAsJungVertex najV = (neoAsJungVertex) i.next();
			if (((Node) najV.getNode()).getId() == nId)
				return najV;
		}
		throw new IllegalStateException(
				"Could not find a neoAsJung proxy for Neo4J Node");
	}

	public neoAsJungEdge getEdgeFor(Relationship r) {
		if (r == null)
			return null;
		long rId = r.getId();
		Iterator i = mEdges.iterator();
		while (i.hasNext()) {
			neoAsJungEdge najE = (neoAsJungEdge) i.next();
			if (((Relationship) najE.getRelationship()).getId() == rId)
				return najE;
		}
		throw new IllegalStateException(
				"Could not find a neoAsJung proxy for Neo4J Relationship");
	}

	/**
	 * Sets the filter we use when returning edgeSets and nodeSets. Any elements
	 * with a key/value matching this will not be included in the Set used by
	 * other methods in this class.
	 */
	public void setFilter(String key, String val) {
		mKeyFilter = key;
		mValFilter = val;

		mVertices = new HashSet();
		Iterator i = mUnfilteredVertices.iterator();
		while (i.hasNext()) {
			neoAsJungVertex najV = (neoAsJungVertex) i.next();
			if (isFiltered(najV.getProp()))
				continue;
			mVertices.add(najV);
		}

		mEdges = new HashSet();
		i = mUnfilteredEdges.iterator();
		while (i.hasNext()) {
			neoAsJungEdge najE = (neoAsJungEdge) i.next();
			if (isFiltered(najE.getProp()))
				continue;
			if (isFiltered(najE.getFrom()) || isFiltered(najE.getTo()))
				continue; // filtering a node filters all its edges
			mEdges.add(najE);
		}
	}
	private boolean isFiltered(UserDataContainer udc)
	{
		Iterator i = udc.getUserDatumKeyIterator();
		while (i.hasNext())
		{
			Object key = i.next();
			if (!(key instanceof String))
				continue;
			String k = (String)key;
			if (!mKeyFilter.equals(key))
				continue;
			Object val = udc.getUserDatum(key);
			if (!(val instanceof String))
				continue;
			String v = (String)val;
			if (!mValFilter.equals(v))
				continue;
			return true;
		}
		return false;
	}

	/**
	 * Registers a shutdown hook for the Neo4j instance so that it shuts down
	 * nicely when the VM exits. Straight from neo4j samples.
	 */
	private static void registerShutdownHook(final GraphDatabaseService graphDb) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				graphDb.shutdown();
			}
		});
	}

	/*****************************************************
	 *****************************************************
	 *****************************************************
	 ***
	 *** Methods for Graph
	 ***
	 *****************************************************
	 *****************************************************
	 *****************************************************/

	/**
	 * Returns <code>true</code> because all Neo4J graphs are directed.
	 * 
	 * @deprecated As of version 1.4, replaced by
	 *             {@link edu.uci.ics.jung.graph.utils.PredicateUtils#enforcesDirected(Graph)}
	 *             and
	 *             {@link edu.uci.ics.jung.graph.utils.PredicateUtils#enforcesUndirected(Graph)}
	 *             .
	 */
	public boolean isDirected() {
		return true;
	}

	/**
	 * Adds <code>v</code> to this graph, and returns a reference to the added
	 * vertex.
	 * 
	 * @note This will add the element to the unfiltered set; therefore, this
	 *       element may not be returned from other methods in this class if it
	 *       matches an active filter.
	 * @param v
	 *            the vertex to be added
	 */
	public Vertex addVertex(Vertex v) {
		if (!(v instanceof neoAsJungVertex))
			throw new IllegalArgumentException(
					"Argument must be a neoAsJung type");
		neoAsJungVertex najV = (neoAsJungVertex) v;
		if (najV.getGraphDb() == null) {
			// No worries, let's create the underlying Node...
			Node n;
			try (Transaction tx = mNeoGraphDb.beginTx()) {
				n = mNeoGraphDb.createNode();
				tx.success();
			}
			najV.initialize(n, this);
			// ...and add the neoAsJung wrapper to our stash.
			mUnfilteredVertices.add(najV);
			setFilter(mKeyFilter, mValFilter);
		} else
			// Already initialized neoAsJung object? Better be in my stash!
			najV = getVertexFor(najV.getNode());
		return najV;
	}

	/**
	 * Default Relationship type, so that we can create the Relationships that
	 * underlie Jungian Edges. (Edges don't have types, so creation that happens
	 * from a Jungian context won't specify any.)
	 */
	enum DefaultRelationshipType implements RelationshipType {
		IS_CONNECTED_TO
	}

	/**
	 * Adds <code>e</code> to this graph, and returns a reference to the added
	 * vertex.
	 * 
	 * @note This will add the element to the unfiltered set; therefore, this
	 *       element may not be returned from other methods in this class if it
	 *       matches an active filter.
	 * @param e
	 *            the edge to be added
	 */
	public Edge addEdge(Edge e) {
		if (!(e instanceof neoAsJungEdge))
			throw new IllegalArgumentException(
					"Argument must be a neoAsJung type");
		neoAsJungEdge najE = (neoAsJungEdge) e;
		if (najE.getGraphDb() == null) {
			// No worries, let's sanity-check the supposed end-points...
			neoAsJungVertex najVfrom = najE.getFrom();
			neoAsJungVertex najVto = najE.getTo();
			if (najVfrom == null || najVto == null
					|| najVfrom.getNode() == null || najVto.getNode() == null)
				throw new IllegalStateException(
						"Must initialize vertices before using them to define an edge");
			getVertexFor(najVfrom.getNode()); // sanity-check that these
			getVertexFor(najVto.getNode()); // vertices are in my stash
			// ...then create the underlying Relationship...
			Relationship r;
			try (Transaction tx = mNeoGraphDb.beginTx()) {
				r = najVfrom.getNode().createRelationshipTo(najVto.getNode(),
						DefaultRelationshipType.IS_CONNECTED_TO);
				tx.success();
			}
			najE.initialize(r, this);
			// ...and add the neoAsJung wrapper to our stash.
			mUnfilteredEdges.add(najE);
			setFilter(mKeyFilter, mValFilter);
		} else
			// Already initialized neoAsJung object? Better be in my stash!
			najE = getEdgeFor(najE.getRelationship());
		return najE;
	}

	/**
	 * Removes <code>v</code> from this graph. Any edges incident to
	 * <code>v</code> which become ill-formed (as defined in the documentation
	 * for <code>ArchetypeEdge</code>) as a result of removing <code>v</code>
	 * are also removed from this graph. Throws
	 * <code>IllegalArgumentException</code> if <code>v</code> is not in this
	 * graph.
	 */
	public void removeVertex(Vertex v) {
		if (!(v instanceof neoAsJungVertex))
			throw new IllegalArgumentException(
					"Argument must be a neoAsJung type");
		neoAsJungVertex najV = (neoAsJungVertex) v;
		if (najV.getGraphDb() != mNeoGraphDb || !mVertices.contains(najV))
			throw new IllegalArgumentException(
					"Argument does not exist in this graph");
		Iterator i = najV.getNode().getRelationships().iterator();
		while (i.hasNext())
			removeEdge(getEdgeFor((Relationship) i.next()));
		mVertices.remove(najV);
		mUnfilteredVertices.remove(najV);
		najV.getNode().delete();
	}

	/**
	 * Removes <code>e</code> from this graph. Throws
	 * <code>IllegalArgumentException</code> if <code>e</code> is not in this
	 * graph.
	 */
	public void removeEdge(Edge e) {
		if (!(e instanceof neoAsJungEdge))
			throw new IllegalArgumentException(
					"Argument must be a neoAsJung type");
		neoAsJungEdge najE = (neoAsJungEdge) e;
		if (najE.getGraphDb() != mNeoGraphDb || !mEdges.contains(najE))
			throw new IllegalArgumentException(
					"Argument does not exist in this graph");
		mUnfilteredEdges.remove(najE);
		mEdges.remove(najE);
		najE.getRelationship().delete();
	}

	/*****************************************************
	 *****************************************************
	 *****************************************************
	 ***
	 *** Methods for ArchetypeGraph
	 ***
	 *****************************************************
	 *****************************************************
	 *****************************************************/

	/**
	 * Returns a graph of the same type as the graph on which this method is
	 * invoked.
	 *
	 * @return ArchetypeGraph
	 */
	public ArchetypeGraph newInstance() {
		// Not sure if I can have multiple Neo4J graph objects open on the same
		// db?
		// Will try to just return a pointer to this same one for now, see what
		// happens.
		return this;
	}

	/**
	 * Returns a Set view of all vertices in this graph. In general, this obeys
	 * the java.util.Set contract, and therefore makes no guarantees about the
	 * ordering of the vertices within the set.
	 */
	public Set getVertices() {
		return mVertices;
	}

	/**
	 * Returns a Set view of all edges in this graph. In general, this obeys the
	 * java.util.Set contract, and therefore makes no guarantees about the
	 * ordering of the edges within the set.
	 */
	public Set getEdges() {
		return mEdges;
	}

	/**
	 * Returns the number of vertices in this graph.
	 */
	public int numVertices() {
		return mVertices.size();
	}

	/**
	 * Returns the number of edges in this graph.
	 */
	public int numEdges() {
		return mEdges.size();
	}

	/**
	 * Removes all elements of <code>vertices</code> from this graph. If any
	 * element of <code>vertices</code> is not part of this graph, then throws
	 * <code>IllegalArgumentException</code>. If this exception is thrown, any
	 * vertices that may have been removed already are not guaranteed to be
	 * restored to the graph. Prunes any resultant ill-formed edges.
	 * 
	 * @param vertices
	 *            the set of vertices to be removed
	 * @deprecated As of version 1.7, replaced by
	 *             <code>GraphUtils.removeVertices(graph, vertices)</code>.
	 */
	public void removeVertices(Set vertices) {
		Iterator i = vertices.iterator();
		while (i.hasNext())
			removeVertex((Vertex) i.next());
	}

	/**
	 * Removes all elements of <code>edges</code> from this graph. If any
	 * element of <code>edges</code> is not part of this graph, then throws
	 * <code>IllegalArgumentException</code>. If this exception is thrown, any
	 * edges that may have been removed already are not guaranteed to be
	 * restored to the graph.
	 * 
	 * @deprecated As of version 1.7, replaced by
	 *             <code>GraphUtils.removeEdges(graph, edges)</code>.
	 */
	public void removeEdges(Set edges) {
		Iterator i = edges.iterator();
		while (i.hasNext())
			removeEdge((Edge) i.next());
	}

	/**
	 * Removes all edges from this graph, leaving the vertices intact.
	 * Equivalent to <code>removeEdges(getEdges())</code>.
	 */
	public void removeAllEdges() {
		removeEdges(getEdges());
	}

	/**
	 * Removes all vertices (and, therefore, edges) from this graph. Equivalent
	 * to <code>removeVertices(getVertices())</code>.
	 */
	public void removeAllVertices() {
		removeVertices(getVertices());
	}

	/**
	 * Supposed to: Performs a deep copy of the graph and its contents.
	 * Actually: returns a pointer to this same graph.
	 */
	public ArchetypeGraph copy() {
		return this;
	}

	/**
	 * Tells the graph to add gel as a listener for changes in the graph
	 * structure
	 * 
	 * @note Unimplemented for neoAsJung
	 * @param gel
	 *            the graph event listener
	 * @param get
	 *            the type of graph events the listeners wants to listen for
	 */
	public void addListener(GraphEventListener gel, GraphEventType get) {
		return;
	}

	/**
	 * Tells the graph to remove gel as a listener for changes in the graph
	 * structure
	 * 
	 * @note Unimplemented for neoAsJung
	 * @param gel
	 *            the graph event listener
	 * @param get
	 *            the type of graph events the listeners wants to not listen for
	 */
	public void removeListener(GraphEventListener gel, GraphEventType get) {
		return;
	}

	/**
	 * Returns the <code>Collection</code> of constraints that each vertex must
	 * satisfy when it is added to this graph. This collection may be viewed and
	 * modified by the user to add or remove constraints.
	 * 
	 * @note Returns empty Collection - not really implemented for neoAsJung
	 */
	public Collection getVertexConstraints() {
		return new Vector();
	}

	/**
	 * Returns the <code>Collection</code> of requirements that each edge must
	 * satisfy when it is added to this graph. This collection may be viewed and
	 * modified by the user to add or remove requirements.
	 * 
	 * @note Returns empty Collection - not really implemented for neoAsJung
	 */
	public Collection getEdgeConstraints() {
		return new Vector();
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
	 * Returning a reference to myself. I'm pretty sure this isn't the Clone
	 * contract, but... :-/
	 */
	public Object clone() throws CloneNotSupportedException {
		return this;
	}

	/**
	 * Adds the specified data with the specified key to this object's user data
	 * repository, with the specified CopyAction.
	 *
	 * @param key
	 *            the key of the datum being added
	 * @param datum
	 *            the datum being added
	 * @param copyAct
	 *            the CopyAction of the datum being added
	 */
	public void addUserDatum(Object key, Object datum, CopyAction copyAct) {
		props.addUserDatum(key, datum, copyAct);
	}

	/**
	 * Takes the user data stored in udc and copies it to this object's user
	 * data repository, respecting each datum's CopyAction.
	 * 
	 * @param udc
	 *            the source of the user data to be copied into this container
	 */
	public void importUserData(UserDataContainer udc) {
		props.importUserData(udc);
	}

	/**
	 * Provides an iterator over this object's user data repository key set.
	 */
	public Iterator getUserDatumKeyIterator() {
		return props.getUserDatumKeyIterator();
	}

	/**
	 * Retrieves the CopyAction for the object stored in this object's user data
	 * repository to which key refers.
	 *
	 * @param key
	 *            the key of the datum whose CopyAction is requested
	 * @return CopyAction the requested CopyAction
	 */
	public CopyAction getUserDatumCopyAction(Object key) {
		return props.getUserDatumCopyAction(key);
	}

	/**
	 * Retrieves the object in this object's user data repository to which key
	 * refers.
	 *
	 * @param key
	 *            the key of the datum to retrieve
	 * @return Object the datum retrieved
	 */
	public Object getUserDatum(Object key) {
		return props.getUserDatum(key);
	}

	/**
	 * If key refers to an existing user datum in this object's repository, that
	 * datum is replaced by the specified datum. Otherwise this is equivalent to
	 * addUserDatum(key, data, copyAct).
	 * 
	 * @param key
	 *            the key of the datum being added/modified
	 * @param datum
	 *            the replacement/new datum
	 * @param copyAct
	 *            the CopyAction for the new (key, datum) pair
	 */
	public void setUserDatum(Object key, Object datum, CopyAction copyAct) {
		props.setUserDatum(key, datum, copyAct);
	}

	/**
	 * Retrieves the object in this object's user data repository to which key
	 * refers, and removes it from the repository.
	 * 
	 * @param key
	 *            the key of the datum to be removed
	 * @return Object the datum removed
	 */
	public Object removeUserDatum(Object key) {
		return props.removeUserDatum(key);
	}

	/**
	 * Reports whether <code>key</code> is a key of this user data container.
	 * 
	 * @param key
	 *            the key to be queried
	 * @return true if <code>key</code> is present in this user data container
	 */
	public boolean containsUserDatumKey(Object key) {
		return containsUserDatumKey(key);
	}
}