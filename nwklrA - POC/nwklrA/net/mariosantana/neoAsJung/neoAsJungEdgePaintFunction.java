package net.mariosantana.neoAsJung;

import java.awt.Color;
import java.awt.Paint;

import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.decorators.EdgePaintFunction;
import edu.uci.ics.jung.visualization.control.PickingGraphMousePlugin;

public class neoAsJungEdgePaintFunction implements EdgePaintFunction {

	protected Paint aka_draw_paint = Color.BLUE;
	protected Paint aka_fill_paint = null;

	protected Paint pktTo_draw_paint = Color.PINK;
	protected Paint pktTo_fill_paint = null;

	protected Paint strTo_draw_paint = Color.RED;
	protected Paint strTo_fill_paint = null;
	
	protected Paint default_draw_paint = Color.BLACK;
	protected Paint default_fill_paint = null;

	public neoAsJungEdgePaintFunction(Paint draw, Paint fill)
	{
		this.default_draw_paint = draw;
		this.default_fill_paint = fill;
	}

	public Paint getDrawPaint(Edge e) {
		if (!(e instanceof neoAsJungEdge))
			throw new IllegalArgumentException("This EdgePaintFunction takes only neoAsJung objects");
		neoAsJungEdge najE = (neoAsJungEdge)e;
		String type = najE.getUserDatum("neoType").toString(); 
		if (type.equals("aka"))
			return aka_draw_paint;
		if (type.equals("pktTo"))
			return pktTo_draw_paint;
		if (type.equals("strTo"))
			return strTo_draw_paint;
		return default_draw_paint;
	}

	public Paint getFillPaint(Edge e) {
		if (!(e instanceof neoAsJungEdge))
			throw new IllegalArgumentException("This PaintFunction takes only neoAsJung objects");
		neoAsJungEdge najE = (neoAsJungEdge)e;
		String type = najE.getUserDatum("neoType").toString(); 
		if (type.equals("aka"))
			return aka_fill_paint;
		if (type.equals("pktTo"))
			return pktTo_fill_paint;
		if (type.equals("strTo"))
			return strTo_fill_paint;
		return default_fill_paint;
	}

}
