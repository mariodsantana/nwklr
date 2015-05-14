package net.mariosantana.neoAsJung;

import java.awt.Color;
import java.awt.Paint;

import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.decorators.EdgePaintFunction;
import edu.uci.ics.jung.visualization.PickedInfo;

public class neoAsJungEdgePaintFunction implements EdgePaintFunction {

	protected Paint aka_draw_paint = Color.BLUE;
	protected Paint aka_picked_draw_paint = Color.BLUE.darker().darker();
	protected Paint aka_fill_paint = null;

	protected Paint pktTo_draw_paint = Color.PINK;
	protected Paint pktTo_picked_draw_paint = Color.PINK.darker().darker();
	protected Paint pktTo_fill_paint = null;

	protected Paint strTo_draw_paint = Color.RED;
	protected Paint strTo_picked_draw_paint = Color.RED.darker().darker();
	protected Paint strTo_fill_paint = null;
	
	protected Paint default_draw_paint = Color.GRAY;
	protected Paint default_picked_draw_paint = Color.GRAY.darker();
	protected Paint default_fill_paint = null;
	
	PickedInfo pi;

	public neoAsJungEdgePaintFunction(PickedInfo pi) {
        if (pi == null)
            throw new IllegalArgumentException("PickedInfo instance must be non-null");
        this.pi = pi;
	}

	public Paint getDrawPaint(Edge e) {
		if (!(e instanceof neoAsJungEdge))
			throw new IllegalArgumentException("This EdgePaintFunction takes only neoAsJung objects");
		neoAsJungEdge najE = (neoAsJungEdge)e;
		String type = najE.getUserDatum("neoType").toString(); 
		if (type.equals("aka"))
			return pi.isPicked(e) ? aka_picked_draw_paint : aka_draw_paint;
		if (type.equals("pktTo"))
			return pi.isPicked(e) ? pktTo_picked_draw_paint : pktTo_draw_paint;
		if (type.equals("strTo"))
			return pi.isPicked(e) ? strTo_picked_draw_paint : strTo_draw_paint;
		return pi.isPicked(e) ? default_picked_draw_paint : default_draw_paint;
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
