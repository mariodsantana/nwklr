/*
 * Copyright (c) 2003, the JUNG Project and the Regents of the University of
 * California All rights reserved.
 * 
 * This software is open-source under the BSD license; see either "license.txt"
 * or http://jung.sourceforge.net/license.txt for a description.
 */
package net.mariosantana.nwklr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.IllegalArgumentException;
import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.ItemSelectable;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.PredicateUtils;

import net.mariosantana.neoAsJung.neoAsJungEdge;
import net.mariosantana.neoAsJung.neoAsJungGraph;
import net.mariosantana.neoAsJung.neoAsJungEdgePaintFunction;
import net.mariosantana.neoAsJung.neoAsJungVertexPaintFunction;
import edu.uci.ics.jung.graph.DirectedEdge;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedEdge;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.decorators.DirectionalEdgeArrowFunction;
import edu.uci.ics.jung.graph.decorators.EdgeArrowFunction;
import edu.uci.ics.jung.graph.decorators.ToolTipFunction;
import edu.uci.ics.jung.utils.UserDataContainer;
import edu.uci.ics.jung.visualization.ArrowFactory;
import edu.uci.ics.jung.visualization.PluggableRenderer;
import edu.uci.ics.jung.visualization.ShapePickSupport;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.Layout;
import edu.uci.ics.jung.visualization.SpringLayout;
import edu.uci.ics.jung.visualization.FRLayout;
import edu.uci.ics.jung.visualization.ISOMLayout;
import edu.uci.ics.jung.visualization.contrib.CircleLayout;
import edu.uci.ics.jung.visualization.contrib.KKLayout;
import edu.uci.ics.jung.visualization.control.AnimatedPickingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.CrossoverScalingControl;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.control.RotatingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.ScalingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.ShearingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.TranslatingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.PickingGraphMousePlugin;

/**
 * nwklr is the NetWitness killer. Built on Jung and Neo4J.
 * 
 * @author Mario D. Santana
 */
public class nwklr extends JApplet {
	protected static neoAsJungGraph g;
	protected static String g_base;
	protected static VisualizationViewer vv;
	protected static Layout layout;
	protected static LayoutChooser mLayoutChooser;
	protected static nwklrModalGraphMouse mModalGraphMouse;
	protected static PluggableRenderer renderer;
	protected static JPanel gPanel;
	protected static JPanel cPanel;
	protected static JTextArea queryField;
	protected static JFrame mainFrame;
	protected static JSplitPane mainPane;
	protected static JTree propTree;
	protected static JScrollPane propPane;
	protected static DefaultMutableTreeNode propRoot;
	protected static UserDataContainer udc;
	protected static nwklrWrappingPickingGraphMousePlugin mPickingMouseWrapper;
	protected static nwklrWrappingTranslatingGraphMousePlugin mTranslatingMouseWrapper;
	protected static Checkbox arrowCheckbox;
	protected static JTextField statusField;


	/**
	 * Just like PickingGraphMousePlugin, but refresh the properties displayed
	 * in the infopane, and use Transform mode if click is not on a vertex
	 * or edge.
	 * 
	 * @note This is ugly, but it accomplishes a combined PICKING and TRANSFORMING mouse mode
	 * at the same time, as long as Jung is set to be in PICKING mode.
	 *
	 * TODO: Refactor nwklrWrapping*GraphMousePlugin stuff
	 */
	private static class nwklrWrappingPickingGraphMousePlugin extends PickingGraphMousePlugin implements MouseListener, MouseMotionListener {
		public nwklrWrappingPickingGraphMousePlugin() {
			super();
		}
		public nwklrWrappingPickingGraphMousePlugin(int selectionModifiers, int addToSelectionModifiers) {
			super(selectionModifiers, addToSelectionModifiers);
		}
		private MouseEvent button3MouseEventToButton1MouseEvent(MouseEvent e) {
			int newModifiers = (e.getModifiers()-e.BUTTON3_MASK) | e.BUTTON1_MASK;
			MouseEvent newMouseEvent =
					//            (Component source,         int id,    long when,   int modifiers, int x,    int y,    int xAbs,         int yAbs,         int clickCount,    boolean popupTrigger, int button)
					new MouseEvent((Component)e.getSource(), e.getID(), e.getWhen(), newModifiers,  e.getX(), e.getY(), e.getXOnScreen(), e.getYOnScreen(), e.getClickCount(), e.isPopupTrigger(),   e.BUTTON1);
			//System.out.println("Orig: "+e.paramString());
			//System.out.println("New:  "+newMouseEvent.paramString());
			return newMouseEvent;
		}
		public void mouseReleased(MouseEvent e) {
			statusField.setText("Processing mouseRelease in Picking mode");
			if (SwingUtilities.isRightMouseButton(e)) {
				statusField.setText("Right-click mousePress in PICKING, processing as left-click but not picking");
				super.mouseReleased(button3MouseEventToButton1MouseEvent(e));
				return;
			}
			super.mouseReleased(e);			
		}
		public void doMousePressed(MouseEvent e, boolean realMousePress) {
			statusField.setText("Processing mousePress in Picking mode");
			if (SwingUtilities.isRightMouseButton(e)) {
				statusField.setText("Right-click mousePress in PICKING, processing as left-click but not picking");
				super.mousePressed(button3MouseEventToButton1MouseEvent(e));
				return;
			}
			super.mousePressed(e);
			if (edge == null && vertex == null) {
				if (realMousePress) {
					statusField.setText("Nothing Picked on real PICKING mousePress, calling TRANSLATING mousePress");
					mTranslatingMouseWrapper.doMousePressed(e,false);
				}
				statusField.setText("Nothing Picked, blanking propTree");
				propRoot.setUserObject("Select an element");
				propTree.setModel(new DefaultTreeModel(propRoot));
				return;
			} else if (vertex != null) {
				statusField.setText("Vertex Picked");
				udc = vertex;
				propRoot = new DefaultMutableTreeNode(udc.getUserDatum("Label0") + " " + udc.getUserDatum("name"));
			} else {
				statusField.setText("Edge Picked");
				udc = edge;
				String rType = (String) udc.getUserDatum("neoType");
				String name = rType + "@" + udc.getUserDatum("neoURL");
				if (rType.equals("pktTo"))
					if (udc.containsUserDatumKey("inStr"))
						name += " in " + udc.getUserDatum("inStr");
					else
						name += " in NO STREAM";
				propRoot = new DefaultMutableTreeNode(name);
			}
			statusField.setText("Creating propTree");
			Iterator<Object> i = udc.getUserDatumKeyIterator();
			while (i.hasNext()) {
				Object key = i.next();
				if (!(key instanceof String))
					continue;
				String k = (String) key;
				DefaultMutableTreeNode n = new DefaultMutableTreeNode(k);
				if (!k.contains(".")) {
					n.setUserObject(n.toString() + ": "
							+ udc.getUserDatum(k).toString());
					propRoot.add(n);
					continue;
				}

				// Split the key on '.', use components as tree path, and
				// walk the tree along that path, creating tree nodes as
				// necessary.
				String[] path = k.split("\\.");
				DefaultMutableTreeNode ptr = propRoot;
				PATH_ELEMENT: for (int pCount = 0; pCount < path.length; pCount++) {
					// Look through all existing children for this path element.
					for (int j = 0; j < ptr.getChildCount(); j++) {
						if (ptr.getChildAt(j).toString().equals(path[pCount])
								|| ptr.getChildAt(j).toString().startsWith(path[pCount]+": ")) {
							// Path element exists as an existing child! Advance
							// pointer and proceed to next path element
							ptr = (DefaultMutableTreeNode) ptr.getChildAt(j);
							continue PATH_ELEMENT;
						}
					}
					// No child found which matches the path element. Create it.
					ptr.add(new DefaultMutableTreeNode(path[pCount]));
					ptr = (DefaultMutableTreeNode) ptr.getLastChild();
				}
				// ptr now points to the deepest node specified by the
				// key-as-treepath. Add the key's value to the text of the node.
				Object userdatum = udc.getUserDatum(k);
				String userdatumstring;
				if (userdatum.getClass().getName().equals("[Ljava.lang.String;")) {
					userdatumstring = Arrays.toString((String[])userdatum);
				} else {
					userdatumstring = userdatum.toString();
				}
				ptr.setUserObject(ptr.toString() + ": " + userdatumstring);
			}
			statusField.setText("Redrawing propTree");
			propTree.setModel(new DefaultTreeModel(propRoot));
		}
		public void mousePressed(MouseEvent e) {
			statusField.setText("Got mousePressed in PICKING mouse");
			doMousePressed(e, true);
		}
		public void mouseDragged(MouseEvent e) {
			statusField.setText("Got mouseDragged in PICKING mouse");
			if (edge == null && vertex == null) {
				if (SwingUtilities.isRightMouseButton(e)) {
					statusField.setText("Right-click drag on background, doing PICKING mouse drag");
					super.mouseDragged(button3MouseEventToButton1MouseEvent(e));
				} else {
					statusField.setText("Doing Translating mouseDragged even though we're in Picking mode");
					mTranslatingMouseWrapper.mouseDragged(e);
				}
			} else {
				statusField.setText("Drag on element, doing PICKING mouse drag");
				super.mouseDragged(e);
			}
		}
	}


	/**
	 * Just like TranslatingGraphMousePlugin, but use picking mode if click
	 * is on a Vertex or Edge.
	 */
	private static class nwklrWrappingTranslatingGraphMousePlugin extends TranslatingGraphMousePlugin implements MouseListener, MouseMotionListener {
		public nwklrWrappingTranslatingGraphMousePlugin() {
			super();
		}
		public nwklrWrappingTranslatingGraphMousePlugin(int modifiers) {
			super(modifiers);
		}
		public void doMousePressed(MouseEvent e, boolean realMousePress) {
			statusField.setText("doing Translating mousePressed");
			super.mousePressed(e);
			if (realMousePress) {
				statusField.setText("doing mousePressed on PickingMouse even through we're in transforming mode");
				mPickingMouseWrapper.doMousePressed(e, false);
			}
		}
		public void mousePressed(MouseEvent e) {
			doMousePressed(e,true);
		}
		public void mouseDragged(MouseEvent e) {
			statusField.setText("Got mouseDragged in TRANSLATING mouse");
			super.mouseDragged(e);
		}
	}


	/**
	 * Just like the DefaultModalGraphMouse, except: - use a nwklrWrapping
	 * version of the PickingGraphMousePlugin - start out in PICKING mode
	 * instead of TRANSFORMING
	 */
	private static class nwklrModalGraphMouse extends DefaultModalGraphMouse implements ModalGraphMouse, ItemSelectable {
		protected void loadPlugins() {
			// pickingPlugin = new PickingGraphMousePlugin();
			mPickingMouseWrapper = new nwklrWrappingPickingGraphMousePlugin();
			pickingPlugin = mPickingMouseWrapper;

			animatedPickingPlugin = new AnimatedPickingGraphMousePlugin();

			// translatingPlugin = new
			// TranslatingGraphMousePlugin(InputEvent.BUTTON1_MASK);
			mTranslatingMouseWrapper = new nwklrWrappingTranslatingGraphMousePlugin(
					InputEvent.BUTTON1_MASK);
			translatingPlugin = mTranslatingMouseWrapper;

			scalingPlugin = new ScalingGraphMousePlugin(
					new CrossoverScalingControl(), 0, in, out);
			rotatingPlugin = new RotatingGraphMousePlugin();
			shearingPlugin = new ShearingGraphMousePlugin();

			add(scalingPlugin);
			setMode(Mode.PICKING);
		}
	}


	/**
	 * Manage switching between layouts.
	 */
	private static final class LayoutChooser implements ActionListener {
		protected static JComboBox<Class<?>> jcbLayoutChooser;
		private LayoutChooser(JComboBox<Class<?>> jcb) {
			super();
			jcbLayoutChooser = jcb;
		}
		public void doAction(Graph aGraph) {
			Class<?> layoutClass = (Class<?>) jcbLayoutChooser.getSelectedItem();
			Object[] constructorArgs = { aGraph };
			if (statusField != null)
				statusField.setText("Redrawing Graph with layout "+layoutClass.toString());
			try {
				Constructor<?> constructor = layoutClass
						.getConstructor(new Class[] { Graph.class });
				Layout l = (Layout) constructor.newInstance(constructorArgs);
				vv.stop();
				vv.setGraphLayout(l, false);
				vv.restart();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		public void actionPerformed(ActionEvent arg0) {
			doAction(g);
		}
	}
	

	/**
	 * Manage the tooltips in the graph visualization
	 */
	private static class nwklrToolTipFunction implements ToolTipFunction {
		private Graph g;
		public nwklrToolTipFunction(Graph graph) {
			this.g = graph;
		}
		public String getToolTipText(MouseEvent event) {
			statusField.setText("Generating MouseEvent toolTip");
			return String.format("%d Nodes, %d Edges",g.numVertices(),g.numEdges());
		}
		public String getToolTipText(Vertex v) {
			statusField.setText("Generating Vertex toolTip");
			return (String)v.getUserDatum("name");
		}
		public String getToolTipText(Edge e) {
			statusField.setText("Generating Edge toolTip");
			return (String)e.getUserDatum("frame.protocols");
		}
	}
	

	/**
	 * Return the arrow shape used in the visualization
	 */
	private static class nwklrEdgeArrowFunction implements EdgeArrowFunction {
	    protected Shape aka_arrow;
	    protected Shape comm_arrow;
	    public nwklrEdgeArrowFunction(int length, int width, int notch_depth) {
	        aka_arrow = ArrowFactory.getNotchedArrow(width, length, notch_depth);
	        comm_arrow = ArrowFactory.getWedgeArrow(width, length);
	    }
	    public Shape getArrow(Edge e) {
			if (!(e instanceof neoAsJungEdge))
				throw new IllegalArgumentException("Argument must be a neoAsJung type");
			neoAsJungEdge najE = (neoAsJungEdge) e;
	        if (najE.getUserDatum("neoType").equals("aka"))
	            return aka_arrow;
	        else
	            return comm_arrow;
	    }
	}


	/**
	 * Return the arrow shape used in the visualization
	 */
	private static class nwklrEdgeArrowPredicate implements Predicate {
		public boolean evaluate(Object arg0) {
			return arrowCheckbox.getState();
		}
	}


	/**
	 * Does things when a node is selected in the propTree
	 */
	private static class nwklrTreeSelectionListener implements TreeSelectionListener {
	    public void valueChanged(TreeSelectionEvent e) {
	    	statusField.setText("Processing change in property selection");
	    	TreePath path = propTree.getSelectionPath();
	        if (path == null || path.getParentPath() == null) {
	        	// nothing selected, or root selected
	        	statusField.setText("Clearing picked edges/vertices");
	        	vv.getPickedState().clearPickedEdges();
	        	vv.getPickedState().clearPickedVertices();
	        	return;
	        }
	        statusField.setText("Parsing key/value of selected property");
	        String key = "";
	        for (int i=1; i < path.getPathCount(); i++) { // start at 1 to skip the root node
	        	if (i > 1) key += ".";
	        	key += path.getPathComponent(i).toString();
	        }
	        int separator = key.indexOf(':');
	        String value = null;
	        if (separator > -1) {
	        	value = key.substring(separator+2);
	        	key = key.substring(0,separator);
	        }
	        statusField.setText("Selecting edges that match "+key+"/"+(value==null?"NoVal":value));
            Iterator<Edge> ie = g.getEdges().iterator();
	        while (ie.hasNext()) {
	        	Edge edge = ie.next();
            	if (value == null && edge.containsUserDatumKey(key))
            		// No value but key exists, so match! 
            		vv.getPickedState().pick(edge,true);
            	else if (value == null) {
            		// No value, key doesn't exist, so presume no match...
            		vv.getPickedState().pick(edge,false);
            		Iterator<Object> ki = edge.getUserDatumKeyIterator();
            		while (ki.hasNext()) {
            			Object k = ki.next();
            			if (k instanceof String && ((String)k).startsWith(key+".")) {
            				// ...except the key is a valid prefix of existing key, so match and move on!
                    		vv.getPickedState().pick(edge,true);
                    		break;
            			}
            		}
            	} else
            		// We have a value, so it must match the userDatum's value for this key.
            		vv.getPickedState().pick(edge,value.equals(edge.getUserDatum(key)));
	        }
	        statusField.setText("Selecting vertices that match "+key+"/"+(value==null?"NoVal":value));
            Iterator<Vertex> iv = g.getVertices().iterator();
	        while (iv.hasNext()) {
	        	Vertex vertex = iv.next();
            	if (value == null && vertex.containsUserDatumKey(key))
            		// No value but key exists, so match! 
            		vv.getPickedState().pick(vertex,true);
            	else if (value == null) {
            		// No value, key doesn't exist, so presume no match...
            		vv.getPickedState().pick(vertex,false);
            		Iterator<Object> ki = vertex.getUserDatumKeyIterator();
            		while (ki.hasNext()) {
            			Object k = ki.next();
            			if (k instanceof String && ((String)k).startsWith(key+".")) {
            				// ...except the key is a valid prefix of existing key, so match and move on!
                    		vv.getPickedState().pick(vertex,true);
                    		break;
            			}
            		}
            	} else
            		// We have a value, so it must match the userDatum's value for this key.
            		vv.getPickedState().pick(vertex,value.equals(vertex.getUserDatum(key)));
	        }
	    }
	}


	/**
	 * Performs the actions for the context menu in the propTree 
	 */
	private static class nwklrPropTreeContextMenuActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			statusField.setText("Processing click on propTree context menu item");
			if (!(e.getSource() instanceof JMenuItem)) {
				System.err.println("Got an ActionListener with bad source:"+e.getSource());
				System.exit(1);
			}
			statusField.setText("Copying CYPHER query that matches the selection to clipboard");
			JMenuItem mi = (JMenuItem)e.getSource();
			StringSelection s = new StringSelection(mi.getToolTipText());
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s,s);
		}
	}
	

	/**
	 * Pops up the context menu when a tree node is right-clicked
	 */
    private static class nwklrMouseListener implements MouseListener {
        public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isRightMouseButton(e))
            	return; // Right now we only handle right-clicks
            statusField.setText("Parsing key/value of right-clicked propTree node");
            TreePath path = propTree.getPathForLocation (e.getX(),e.getY());
	        String key = "";
	        for (int i=1; i < path.getPathCount(); i++) { // start at 1 to skip the root node
	        	if (i > 1) key += ".";
	        	key += path.getPathComponent(i).toString();
	        }
	        int separator = key.indexOf(':');
	        String value = null;
	        if (separator > -1) {
	        	value = key.substring(separator+2);
	        	key = key.substring(0,separator);
	        }
            statusField.setText("Creating propTree context menu");
	        Rectangle pathBounds = propTree.getUI().getPathBounds(propTree,path);
            if (pathBounds != null && pathBounds.contains(e.getX(),e.getY())) {
            	// Text is descriptive, tooltipText is the CYPHER query
                JMenuItem ccm = new JMenuItem("Copy CYPHER match");
                ccm.setToolTipText(key+" = '"+value+"'");
                ccm.addActionListener(new nwklrPropTreeContextMenuActionListener());
                JPopupMenu menu = new JPopupMenu();
                menu.add(ccm);
                menu.show(propTree,pathBounds.x,pathBounds.y+pathBounds.height);
            }
        }
		public void mousePressed(MouseEvent e) {
			return; // Don't care
		}
		public void mouseReleased(MouseEvent e) {
			return; // Don't care
		}
		public void mouseEntered(MouseEvent e) {
			return; // Don't care
		}
		public void mouseExited(MouseEvent e) {
			return; // Don't care
		}
    }
	

    private static JPanel getGraphPanel() {
		layout = new FRLayout(g);
		renderer = new PluggableRenderer();
		renderer.setVertexPaintFunction(new neoAsJungVertexPaintFunction(renderer));
		renderer.setEdgePaintFunction(new neoAsJungEdgePaintFunction(renderer));
		renderer.setEdgeArrowFunction(new nwklrEdgeArrowFunction(8,6,3));
		renderer.setEdgeArrowPredicate(new nwklrEdgeArrowPredicate());
		vv = new VisualizationViewer(layout,renderer);
		vv.setGraphMouse(mModalGraphMouse);
		vv.setPickSupport(new ShapePickSupport());
		vv.setToolTipFunction(new nwklrToolTipFunction(g));
		
		JPanel jp = new JPanel();
		jp.setBackground(Color.WHITE);
		jp.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		jp.setLayout(new BorderLayout());
		jp.add(vv, BorderLayout.CENTER);
		vv.setBackground(Color.WHITE);
		return jp;
	}

    
	private static JPanel getControlPanel() {
		final JComboBox<Class<?>> jcbLayout = new JComboBox<Class<?>>(getArrayOfLayouts());
		// use a renderer to shorten the layout name presentation
		jcbLayout.setRenderer(new DefaultListCellRenderer() {
			public Component getListCellRendererComponent(JList list,
					Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				String valueString = value.toString();
				valueString = valueString.substring(valueString
						.lastIndexOf('.') + 1);
				return super.getListCellRendererComponent(list, valueString,
						index, isSelected, cellHasFocus);
			}
		});
		mLayoutChooser = new LayoutChooser(jcbLayout);
		jcbLayout.addActionListener(mLayoutChooser);
		jcbLayout.setSelectedItem(FRLayout.class);
		jcbLayout.setMaximumSize(new Dimension(Integer.MAX_VALUE, jcbLayout
				.getPreferredSize().height));

		propRoot = new DefaultMutableTreeNode("Select an element");
		propTree = new JTree(propRoot);
		propTree.setShowsRootHandles(true);
		((DefaultTreeCellRenderer)propTree.getCellRenderer()).setIcon(null);
		((DefaultTreeCellRenderer)propTree.getCellRenderer()).setLeafIcon(null);
		((DefaultTreeCellRenderer)propTree.getCellRenderer()).setClosedIcon(null);
		((DefaultTreeCellRenderer)propTree.getCellRenderer()).setOpenIcon(null);
		// TODO: allow contiguous selection mode, and teach the event listener some more tricks!
		propTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		propTree.addTreeSelectionListener(new nwklrTreeSelectionListener());
	    propTree.addMouseListener(new nwklrMouseListener());

		propPane = new JScrollPane(propTree);
		propPane.setBackground(Color.WHITE);

		queryField = new JTextArea(g.getCypherFilter());
		queryField.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));
		JScrollPane queryPane = new JScrollPane(queryField);
		
		JButton filterButton = new JButton();
		filterButton
				.setToolTipText("Apply the CYPHER query as a filter (A blank value will reset to the default filter)");
		filterButton.setText("Apply");
		filterButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				statusField.setText("Applying CYPHER filter query");
				String oldCypherFilter = g.getCypherFilter();
				g.setCypherFilter(queryField.getText());
				try {
					g.applyFilters();
					mLayoutChooser.doAction(g);
				} catch (Exception exception) {
					g.setCypherFilter(oldCypherFilter);
					String msg = "// There's an error in this CYPHER query\n\n";
					msg += "// " + exception.toString().replace("\n", "\n// ");
					msg += "\n\n" + queryField.getText();					
					msg += "\n\n// The old query is:\n";
					msg += "// " + oldCypherFilter.replace("\n", "\n// ");
					queryField.setText(msg);
				}
			}
		});
		arrowCheckbox.setMinimumSize(new Dimension(arrowCheckbox.getMinimumSize().width,filterButton.getMinimumSize().height));
		arrowCheckbox.setMaximumSize(new Dimension(arrowCheckbox.getMaximumSize().width,filterButton.getMaximumSize().height));
		arrowCheckbox.setPreferredSize(new Dimension(arrowCheckbox.getPreferredSize().width,filterButton.getPreferredSize().height));
		arrowCheckbox.setSize(new Dimension(arrowCheckbox.getSize().width,filterButton.getSize().height));
		JPanel filterAndArrowPanel = new JPanel();
		//filterAndArrowPanel.setPreferredSize(new Dimension(1,filterButton.getHeight()));
		filterAndArrowPanel.setLayout(new BoxLayout(filterAndArrowPanel, BoxLayout.X_AXIS));
		filterAndArrowPanel.add(filterButton);
		filterAndArrowPanel.add(arrowCheckbox);
		
		statusField = new JTextField("Initializing...");
		statusField.setEditable(false);
		statusField.setFont(new Font(Font.MONOSPACED,Font.BOLD,8));
		statusField.setMaximumSize(new Dimension(statusField.getMaximumSize().width,statusField.getFont().getSize()));
		

		JPanel jp = new JPanel();
		jp.setLayout(new BoxLayout(jp, BoxLayout.Y_AXIS));
		jp.add(jcbLayout);
		jp.add(queryPane);
		jp.add(filterAndArrowPanel);
		jp.add(propPane);
		jp.add(statusField);
		return jp;
	} // getControlPanel()

	
	private static Class<?>[] getArrayOfLayouts() {
		List<Class<?>> layouts = new ArrayList<Class<?>>();
		layouts.add(KKLayout.class);
		layouts.add(FRLayout.class);
		layouts.add(CircleLayout.class);
		// layouts.add(DAGLayout.class);
		layouts.add(SpringLayout.class);
		layouts.add(ISOMLayout.class);
		return (Class<?>[]) layouts.toArray(new Class<?>[0]);
	}

	
	public static void main(String[] args) {
		if (args.length != 1)
			throw new IllegalArgumentException(
					"Provide a single argument: the Neo4J base directory");
		g_base = args[0];
		g = new neoAsJungGraph(g_base + "/data/graph.db", g_base + "/conf/neo4j-server.properties");

		// initialize a couple of things used both in cPanel and gPanel
		mModalGraphMouse = new nwklrModalGraphMouse();
		arrowCheckbox = new Checkbox("Arrows",false);
		
		gPanel = getGraphPanel();
		cPanel = getControlPanel();
		mainFrame = new JFrame();
		mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gPanel, cPanel);
		mainPane.setOneTouchExpandable(true);
		Dimension minimumSize = new Dimension(100, 50);
		gPanel.setMinimumSize(minimumSize);
		cPanel.setMinimumSize(minimumSize);
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setPreferredSize(mainFrame.getToolkit().getScreenSize());
		mainFrame.getContentPane().add(mainPane);
		mainFrame.pack();
		mainPane.setDividerLocation(0.8);
		mainFrame.setVisible(true);
	}
}
