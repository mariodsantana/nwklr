package net.mariosantana.neoAsJung;

import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.ArchetypeGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.event.GraphEventListener;
import edu.uci.ics.jung.graph.event.GraphEventType;

import edu.uci.ics.jung.utils.UserDataContainer;
import edu.uci.ics.jung.utils.DefaultUserData;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.tooling.GlobalGraphOperations;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Vector;
import java.util.Iterator;
import java.lang.System;

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
	
	protected ExecutionEngine mExecutionEngine;
	public ExecutionEngine getExecutionEngine() {
		return mExecutionEngine;
	}

	/**
	 * These sets are copies from the DB, filtered by the cypherFilter CYPHER expression
	 */
	protected Set<neoAsJungVertex> mVertices;
	protected Set<neoAsJungEdge> mEdges;

	/**
	 * The CYPHER query that was used to filter the nodes we have right now.
	 */
	protected String defaultCypherFilter = "// See http://neo4j.com/docs/stable/cypher-query-lang.html\n"
			+ "// Sample: find first 10 streams-or-aka edges, and associated nodes\n"
			+ "MATCH (s)-[r:strTo|aka]->(d) return r limit 10";
	protected String cypherFilter = defaultCypherFilter;
	public String getCypherFilter() {
		return this.cypherFilter;
	}
	public void setCypherFilter(String f) {
		this.cypherFilter = f.trim();
		if (cypherFilter.length() == 0)
			this.cypherFilter = this.defaultCypherFilter;
	}
	
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
		mExecutionEngine = new ExecutionEngine(mNeoGraphDb);
		props = new DefaultUserData();
		applyFilters();
	}

	/**
	 * Get a neoAsJung version of a neo4j object.
	 * Fetch from current (possibly filtered!) list of neoAsJung objects,
	 * otherwise wrap in a new neoAsJung object.
	 */
	public neoAsJungVertex getVertexFor(Node n) {
		if (n == null)
			return null;
		long nId = n.getId();
		Iterator<neoAsJungVertex> i = mVertices.iterator();
		while (i.hasNext()) {
			neoAsJungVertex najV = (neoAsJungVertex) i.next();
			if (((Node) najV.getNode()).getId() == nId)
				return najV;
		}
		neoAsJungVertex najV = new neoAsJungVertex();
		try (Transaction tx = mNeoGraphDb.beginTx()) {
			najV.initialize(n, this);
		}
		mVertices.add(najV);
		return najV;
	}

	/**
	 * Get a neoAsJung version of a neo4j object.
	 * Fetch from current (possibly filtered!) list of neoAsJung objects,
	 * otherwise wrap in a new neoAsJung object.
	 */
	public neoAsJungEdge getEdgeFor(Relationship r) {
		if (r == null)
			return null;
		long rId = r.getId();
		Iterator<neoAsJungEdge> i = mEdges.iterator();
		while (i.hasNext()) {
			neoAsJungEdge najE = i.next();
			if (najE.getRelationship().getId() == rId)
				return najE;
		}
		// Didn't find it in our current list, create a new wrapper
		neoAsJungVertex najVfrom = getVertexFor(r.getStartNode());
		neoAsJungVertex najVto = getVertexFor(r.getEndNode());
		neoAsJungEdge najE = new neoAsJungEdge(najVfrom, najVto);
		try (Transaction tx = mNeoGraphDb.beginTx()) {
			najE.initialize(r, this);
			tx.success();
		}
		mEdges.add(najE);
		return najE;
	}

	/**
	 * Resets the filter to the default
	 */
	public void resetFilters() {
		this.cypherFilter = this.defaultCypherFilter;
		applyFilters();
	}
	
	/**
	 * Applies the existing filter.
	 * 
	 * @throws Exception
	 * 		Be sure to catch this in case the cypher query doesn't work.
	 */
	public void applyFilters() {
		mVertices = new HashSet<neoAsJungVertex>();
		mEdges = new HashSet<neoAsJungEdge>();
		try (Transaction tx = mNeoGraphDb.beginTx()) {
			Object neoObj; // neo4j objects (nodes or relationships) returned from query
			Iterator<Map<String,Object>> rMapIterator = this.mExecutionEngine.execute(this.cypherFilter).iterator();
			int rMapCount = 0;
			while (rMapIterator.hasNext()) {
				rMapCount++;
				Iterator<Object> neoObjects = rMapIterator.next().values().iterator();
				int rObjCount = 0;
				while (neoObjects.hasNext()) {
					rObjCount++;
					neoObj = neoObjects.next();
					if (neoObj instanceof Node) {
						neoAsJungVertex najV = new neoAsJungVertex();
						najV.initialize((Node) neoObj, this);
						mVertices.add(najV);
					} else if (neoObj instanceof Relationship) {
						Relationship rel = (Relationship) neoObj;
						neoAsJungVertex najVfrom = getVertexFor(rel.getStartNode());
						if (najVfrom == null) {
							najVfrom = new neoAsJungVertex();
							najVfrom.initialize(rel.getStartNode(), this);
							mVertices.add(najVfrom);
						}
						neoAsJungVertex najVto = getVertexFor(rel.getEndNode());
						if (najVto == null) {
							najVto = new neoAsJungVertex();
							najVto.initialize(rel.getEndNode(), this);
							mVertices.add(najVto);
						}
						neoAsJungEdge najE = new neoAsJungEdge(najVfrom, najVto);
						najE.initialize(rel, this);
						mEdges.add(najE);
					} else {
						// FIXME: handle arrays of Node/Relationship objects as well
						System.err.println("Got a non-Node/non-Relationship result from cypherFilter.");
						System.err.println("Cypher query was -----'"+this.cypherFilter+"'-----");
						System.err.println("Object gotten was: "+neoObj);
						System.exit(1);
					}
				}
			}
		}
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
				najV.initialize(n, this);
				tx.success();
			}
		} else {
			// Already initialized neoAsJung object? Sanity-check by refreshing...
			najV = getVertexFor(najV.getNode());
		}
		// ...and make sure the neoAsJung wrapper is in our current stash.
		mVertices.add(najV);
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
	 * edge.
	 * 
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
			najVfrom = getVertexFor(najVfrom.getNode()); // sanity-check these...
			najVto = getVertexFor(najVto.getNode()); // ...vertices by refreshing...
			// ...then create the underlying Relationship...
			Relationship r;
			try (Transaction tx = mNeoGraphDb.beginTx()) {
				r = najVfrom.getNode().createRelationshipTo(najVto.getNode(),
						DefaultRelationshipType.IS_CONNECTED_TO);
				tx.success();
			}
			najE.initialize(r, this);
		} else {
			// Already initialized neoAsJung object? Sanity-check by refreshing...
			najE = getEdgeFor(najE.getRelationship());
		}
		// ...and make sure the neoAsJung wrapper is in our current stash.
		mEdges.add(najE);
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
		if (najV.getGraphDb() != mNeoGraphDb)
			throw new IllegalArgumentException(
					"Argument not initialized in this graph");
		Iterator<Relationship> i = najV.getNode().getRelationships().iterator();
		while (i.hasNext())
			removeEdge(getEdgeFor(i.next()));
		mVertices.remove(najV);
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
		if (najE.getGraphDb() != mNeoGraphDb)
			throw new IllegalArgumentException(
					"Argument not initialized in this graph");
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
		Iterator<Vertex> i = vertices.iterator();
		while (i.hasNext())
			removeVertex(i.next());
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
		Iterator<Edge> i = edges.iterator();
		while (i.hasNext())
			removeEdge(i.next());
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
		return props.containsUserDatumKey(key);
	}
}