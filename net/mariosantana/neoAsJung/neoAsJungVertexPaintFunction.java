package net.mariosantana.neoAsJung;

import java.awt.Color;
import java.awt.Paint;

import edu.uci.ics.jung.graph.Vertex;
import net.mariosantana.neoAsJung.neoAsJungVertex;
import edu.uci.ics.jung.graph.decorators.VertexPaintFunction;
import edu.uci.ics.jung.visualization.PickedInfo;

public class neoAsJungVertexPaintFunction implements VertexPaintFunction {

	protected Paint eth_fill_paint = Color.CYAN;
	protected Paint eth_picked_fill_paint = Color.BLUE;
    protected Paint ip_fill_paint = Color.PINK;
    protected Paint ip_picked_fill_paint = Color.RED;
	protected Paint default_fill_paint = Color.GRAY;
	protected Paint default_picked_fill_paint = Color.BLACK;
    protected Paint draw_paint = Color.DARK_GRAY;
    protected PickedInfo pi;
    
    /**
     * @param pi            specifies which vertices report as "picked"
     */
    public neoAsJungVertexPaintFunction(PickedInfo pi)
    {
        if (pi == null)
            throw new IllegalArgumentException("PickedInfo instance must be non-null");
        this.pi = pi;
    }

    public Paint getDrawPaint(Vertex v)
    {
        return draw_paint;
    }

    public Paint getFillPaint(Vertex v)
    {
		if (!(v instanceof neoAsJungVertex))
			throw new IllegalArgumentException("This PaintFunction takes only neoAsJung objects");
		neoAsJungVertex najV = (neoAsJungVertex)v;
		String name = najV.getUserDatum("name").toString(); 
		if (name == null)
			return pi.isPicked(v) ? default_picked_fill_paint : default_fill_paint;
		if (name.contains(":"))
			return pi.isPicked(v) ? eth_picked_fill_paint : eth_fill_paint;
		if (name.contains("."))
			return pi.isPicked(v) ? ip_picked_fill_paint : ip_fill_paint;
		return pi.isPicked(v) ? default_picked_fill_paint : default_fill_paint;
    }
}
