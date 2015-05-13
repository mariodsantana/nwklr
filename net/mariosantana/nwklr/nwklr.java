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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.ItemSelectable;

import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JScrollPane;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import net.mariosantana.neoAsJung.neoAsJungGraph;
import net.mariosantana.neoAsJung.neoAsJungEdgePaintFunction;
import net.mariosantana.neoAsJung.neoAsJungVertexPaintFunction;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.decorators.ToolTipFunction;
import edu.uci.ics.jung.utils.UserDataContainer;
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
	protected static JPanel cPane;
	protected static JTextArea queryField;
	protected static JFrame mainFrame;
	protected static JSplitPane mainPane;
	protected static JTree propTree;
	protected static JScrollPane propPane;
	protected static DefaultMutableTreeNode propRoot;
	protected static UserDataContainer udc;
	protected static nwklrWrappingPickingGraphMousePlugin mPickingMouseWrapper;
	protected static nwklrWrappingTranslatingGraphMousePlugin mTranslatingMouseWrapper;

	/**
	 * Just like PickingGraphMousePlugin, but refresh the properties displayed
	 * in the infopane, and turn on Transform mode if click is not on a vertex
	 * or edge.
	 */
	private static class nwklrWrappingPickingGraphMousePlugin extends
			PickingGraphMousePlugin implements MouseListener,
			MouseMotionListener {
		public nwklrWrappingPickingGraphMousePlugin() {
			super();
		}

		public nwklrWrappingPickingGraphMousePlugin(int selectionModifiers,
				int addToSelectionModifiers) {
			super(selectionModifiers, addToSelectionModifiers);
		}

		public void doMousePressed(MouseEvent e, boolean realMousePress) {
			System.out.println("doing Picking mousePressed");
			super.mousePressed(e);
			if (edge == null && vertex == null) {
				propRoot.setUserObject("Select an element");
				propTree.setModel(new DefaultTreeModel(propRoot));
				return;
			} else if (vertex != null) {
				udc = vertex;
				propRoot = new DefaultMutableTreeNode(
						udc.getUserDatum("Label0") + " "
								+ udc.getUserDatum("name"));
			} else {
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
				// TODO: make it do something when a treenode is clicked
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
			// redraw the tree!
			propTree.setModel(new DefaultTreeModel(propRoot));
		}

		@Override
		public void mousePressed(MouseEvent e) {
			System.out.println("Got mousePressed in PICKING mouse");
			doMousePressed(e, true);
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			System.out.println("Got mouseDragged in PICKING mouse");
			super.mouseDragged(e);
		}
	}

	/**
	 * Just like TranslatingGraphMousePlugin, but turn on picking mode if click
	 * is on a Vertex or Edge.
	 */
	private static class nwklrWrappingTranslatingGraphMousePlugin extends
			TranslatingGraphMousePlugin implements MouseListener,
			MouseMotionListener {
		public nwklrWrappingTranslatingGraphMousePlugin() {
			super();
		}

		public nwklrWrappingTranslatingGraphMousePlugin(int modifiers) {
			super(modifiers);
		}

		public void doMousePressed(MouseEvent e, boolean realPress) {
			System.out.println("doing Translating mousePressed");
			if (realPress) {
				System.out
						.println("doing mousePressed on PickingMouse even through we're in transforming mode");
				mPickingMouseWrapper.doMousePressed(e, false);
			}
			super.mousePressed(e);
		}

		@Override
		public void mousePressed(MouseEvent e) {
			System.out.println("Got mousePressed in TRANSLATING mouse");
			doMousePressed(e, true);
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			System.out.println("Got mouseDragged in TRANSLATING mouse");
			super.mouseDragged(e);
		}
	}

	/**
	 * Just like the DefaultModalGraphMouse, except: - use a nwklrWrapping
	 * version of the PickingGraphMousePlugin - start out in PICKING mode
	 * instead of TRANSFORMING
	 */
	private static class nwklrModalGraphMouse extends DefaultModalGraphMouse
			implements ModalGraphMouse, ItemSelectable {
		@Override
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

		@Override
		public void actionPerformed(ActionEvent arg0) {
			doAction(g);
		}
	}
	
	/**
	 * Manage the tooltips in the graph visualization
	 */
	private static final class nwklrToolTipFunction implements ToolTipFunction {
		public String getToolTipText(MouseEvent event) {
			return null;
		}
		public String getToolTipText(Vertex v) {
			return (String)v.getUserDatum("name");
		}
		public String getToolTipText(Edge e) {
			return (String)e.getUserDatum("frame.protocols");
		}
	}
	
	private static JPanel getGraphPanel() {
		layout = new FRLayout(g);
		renderer = new PluggableRenderer();
		renderer.setVertexPaintFunction(new neoAsJungVertexPaintFunction(
				renderer));
		renderer.setEdgePaintFunction(new neoAsJungEdgePaintFunction(
				Color.black, null));
		vv = new VisualizationViewer(layout, renderer);
		mModalGraphMouse = new nwklrModalGraphMouse();
		vv.setGraphMouse(mModalGraphMouse);
		vv.setPickSupport(new ShapePickSupport());
		vv.setToolTipFunction(new nwklrToolTipFunction());
		
		JPanel jp = new JPanel();
		jp.setBackground(Color.WHITE);
		jp.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		jp.setLayout(new BorderLayout());
		jp.add(vv, BorderLayout.CENTER);
		vv.setBackground(Color.WHITE);
		return jp;
	}

	private static JPanel getControlPanel() {
		JComboBox modeBox = mModalGraphMouse.getModeComboBox();
		modeBox.addItemListener(((DefaultModalGraphMouse) vv.getGraphMouse())
				.getModeListener());
		modeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, modeBox
				.getPreferredSize().height));

		final JComboBox<Class<?>> jcbLayout = new JComboBox<Class<?>>(getArrayOfLayouts());
		// use a renderer to shorten the layout name presentation
		jcbLayout.setRenderer(new DefaultListCellRenderer() {
			@Override
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
		propPane = new JScrollPane(propTree);
		propPane.setBackground(Color.WHITE);

		queryField = new JTextArea(g.getCypherFilter());
		JScrollPane queryPane = new JScrollPane(queryField);
		JButton filterButton = new JButton();
		filterButton
				.setToolTipText("Apply the CYPHER query as a filter (A blank value will reset to the default filter)");
		filterButton.setText("Apply");
		filterButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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

		JPanel filterPanel = new JPanel();
		filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
		filterPanel.add(queryPane);
		filterPanel.add(filterButton);

		JPanel jp = new JPanel();
		jp.setLayout(new BoxLayout(jp, BoxLayout.Y_AXIS));
		jp.add(modeBox);
		jp.add(jcbLayout);
		jp.add(filterPanel);
		jp.add(propPane);
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
		g = new neoAsJungGraph(g_base + "/data/graph.db", g_base
				+ "/conf/neo4j-server.properties");

		gPanel = getGraphPanel();
		cPane = getControlPanel();
		mainFrame = new JFrame();
		mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gPanel, cPane);
		mainPane.setOneTouchExpandable(true);
		Dimension minimumSize = new Dimension(100, 50);
		gPanel.setMinimumSize(minimumSize);
		cPane.setMinimumSize(minimumSize);
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setPreferredSize(mainFrame.getToolkit().getScreenSize());
		mainFrame.getContentPane().add(mainPane);
		mainFrame.pack();
		mainPane.setDividerLocation(0.8);
		mainFrame.setVisible(true);
	}
}
