package net.osmand.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.osmand.LogUtil;
import net.osmand.render.OsmandRenderer.RenderingContext;
import net.osmand.render.OsmandRenderer.RenderingPaintProperties;
import net.osmand.render.OsmandRenderingRulesParser.EffectAttributes;
import net.osmand.render.OsmandRenderingRulesParser.FilterState;
import net.osmand.render.OsmandRenderingRulesParser.RenderingRuleVisitor;

import org.apache.commons.logging.Log;
import org.xml.sax.SAXException;

import android.graphics.Color;
import android.graphics.Paint.Cap;


public class BaseOsmandRender implements RenderingRuleVisitor {
	
	public String name = "default"; //$NON-NLS-1$
	public List<String> depends = new ArrayList<String>();
	private static final Log log = LogUtil.getLog(BaseOsmandRender.class);

	@SuppressWarnings("unchecked")
	private Map<String, Map<String, List<FilterState>>>[] rules = new LinkedHashMap[5]; 
	
	
	private static BaseOsmandRender defaultRender = null;
	public static BaseOsmandRender defaultRender() throws IOException, SAXException{
		if(defaultRender == null){
			defaultRender = new BaseOsmandRender(OsmandRenderingRulesParser.class.getResourceAsStream("default.render.xml")); //$NON-NLS-1$
		}
		return defaultRender;
	}
	
	public BaseOsmandRender(InputStream is) throws IOException, SAXException {
		long time = System.currentTimeMillis();
		OsmandRenderingRulesParser parser = new OsmandRenderingRulesParser();
		parser.parseRenderingRules(is, this);
		log.info("Init render " + name + " for " + (System.currentTimeMillis() - time) + " ms");   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	}
	
	protected BaseOsmandRender(){
	}

	@Override
	public void rendering(String name, String depends) {
		this.name = name;
		if(depends != null && depends.length() > 0){
			for(String s : depends.split(",")) { //$NON-NLS-1$
				if(s.trim().length() > 0){
					this.depends.add(s.trim());
				}
			}
		}
	}

	@Override
	public void visitRule(int state, FilterState filter) {
		boolean accept = filter.minzoom != -1;
		if(state == OsmandRenderingRulesParser.POINT_STATE){
			accept &= RenderingIcons.getIcons().containsKey(filter.icon);
		}
		if (accept) {
			if (rules[state] == null) {
				rules[state] = new LinkedHashMap<String, Map<String, List<FilterState>>>();
			}
			if (rules[state].get(filter.tag) == null) {
				rules[state].put(filter.tag, new LinkedHashMap<String, List<FilterState>>());
			}
			if (rules[state].get(filter.tag).get(filter.val) == null) {
				rules[state].get(filter.tag).put(filter.val, new ArrayList<FilterState>(3));
			}
			rules[state].get(filter.tag).get(filter.val).add(filter);
		}
	}
	
	public Integer getPointIcon(String tag, String val, int zoom){
		Integer i = getPointIconImpl(tag,val, zoom);
		if(i== null){
			return getPointIconImpl(tag, null, zoom);
		}
		return i;
	}

	private Integer getPointIconImpl(String tag, String val, int zoom) {
		Map<String, List<FilterState>> map = rules[OsmandRenderingRulesParser.POINT_STATE].get(tag);
		if (map != null) {
			List<FilterState> list = map.get(val);
			if (list != null) {
				for (FilterState f : list) {
					if (f.minzoom <= zoom && (zoom <= f.maxzoom || f.maxzoom == -1)) {
						return RenderingIcons.getIcons().get(f.icon);
					}
				}
			}
		}
		return null;
	}
	
	public boolean renderPolyline(String tag, String val, int zoom, RenderingContext rc, OsmandRenderer o, int layer){
		boolean r = renderPolylineImpl(tag,val, zoom, rc, o, layer);
		if(!r){
			return renderPolylineImpl(tag, null, zoom, rc, o, layer);
		}
		return r;
	}

	private boolean renderPolylineImpl(String tag, String val, int zoom, RenderingContext rc, OsmandRenderer o, int layer) {
		Map<String, List<FilterState>> map = rules[OsmandRenderingRulesParser.LINE_STATE].get(tag);
		if (map != null) {
			List<FilterState> list = map.get(val);
			if (list != null) {
				FilterState found = null;
				for (FilterState f : list) {
					if (f.minzoom <= zoom && (zoom <= f.maxzoom || f.maxzoom == -1) && f.layer == layer) {
						found = f;
						break;
					}
				}
				if (found == null) {
					for (FilterState f : list) {
						if (f.minzoom <= zoom && (zoom <= f.maxzoom || f.maxzoom == -1) && f.layer == 0) {
							found = f;
							break;
						}
					}
				}
				if (found != null) {
					// to not make transparent
					rc.main.color = Color.BLACK;
					if (found.shader != null) {
						Integer i = RenderingIcons.getIcons().get(found.shader);
						if (i != null) {
							rc.main.shader = o.getShader(i);
						}
					}
					rc.main.fillArea = false;
					applyEffectAttributes(found.main, rc.main, o);
					if (found.effectAttributes.size() > 0) {
						applyEffectAttributes(found.effectAttributes.get(0), rc.second, o);
						if (found.effectAttributes.size() > 1) {
							applyEffectAttributes(found.effectAttributes.get(1), rc.third, o);
						}
					}
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean renderPolygon(String tag, String val, int zoom, RenderingContext rc, OsmandRenderer o){
		boolean r = renderPolygonImpl(tag,val, zoom, rc, o);
		if(!r){
			return renderPolygonImpl(tag, null, zoom, rc, o);
		}
		return r;
	}

	private boolean renderPolygonImpl(String tag, String val, int zoom, RenderingContext rc, OsmandRenderer o) {
		Map<String, List<FilterState>> map = rules[OsmandRenderingRulesParser.POLYGON_STATE].get(tag);
		if (map != null) {
			List<FilterState> list = map.get(val);
			if (list != null) {
				for (FilterState f : list) {
					if (f.minzoom <= zoom && (zoom <= f.maxzoom || f.maxzoom == -1)) {
						if(f.shader != null){
							Integer i = RenderingIcons.getIcons().get(f.shader);
							if(i != null){
								// to not make transparent
								rc.main.color = Color.BLACK;
								rc.main.shader = o.getShader(i);
							}
						}
						rc.main.fillArea = true;
						applyEffectAttributes(f.main, rc.main, o);
						if(f.effectAttributes.size() > 0){
							applyEffectAttributes(f.effectAttributes.get(0), rc.second, o);
							if(f.effectAttributes.size() > 1){
								applyEffectAttributes(f.effectAttributes.get(1), rc.third, o);
							}
						}
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private void applyEffectAttributes(EffectAttributes ef, RenderingPaintProperties props, OsmandRenderer o){
		if(ef.cap != null){
			props.cap = Cap.valueOf(ef.cap.toUpperCase());
		}
		if(ef.color != 0){
			// do not set transparent color
			props.color = ef.color;
		}
		if(ef.pathEffect != null){
			props.pathEffect = o.getDashEffect(ef.pathEffect); 
		}
		if(ef.strokeWidth > 0){
			props.strokeWidth = ef.strokeWidth;
		}
		if(ef.shadowColor != 0 && ef.shadowRadius > 0){
			props.shadowColor = ef.shadowColor;
			props.shadowLayer = (int) ef.shadowRadius;
		}
	}
	
	public String renderObjectText(String name, String tag, String val, RenderingContext rc, boolean ref) {
		if(name == null || name.length() == 0){
			return null;
		}
		String ret = renderObjectTextImpl(name, tag, val, rc, ref);
		if(rc.textSize > 0){
			return ret;
		}
		return renderObjectTextImpl(name, tag, null, rc, ref);
	}
	
	private boolean checkRefTextRule(FilterState f, boolean ref){
		if(ref){
			return f.text != null && f.text.ref != null;
		} else {
			return f.text == null || f.text.ref == null || "true".equals(f.text.ref); //$NON-NLS-1$
		}
	}

	private String renderObjectTextImpl(String name, String tag, String val, RenderingContext rc, boolean ref) {
		Map<String, List<FilterState>> map = rules[OsmandRenderingRulesParser.TEXT_STATE].get(tag);
		if (map != null) {
			List<FilterState> list = map.get(val);
			if (list != null) {
				// first find rule with same text length
				for (FilterState f : list) {
					if (f.minzoom <= rc.zoom && (rc.zoom <= f.maxzoom || f.maxzoom == -1) && checkRefTextRule(f, ref)) {
						if(f.textLength == name.length() && f.text.textSize > 0){
							fillTextProperties(f, rc);
							return name;
						}
					}
				}
				
				for (FilterState f : list) {
					if (f.minzoom <= rc.zoom && (rc.zoom <= f.maxzoom || f.maxzoom == -1) && checkRefTextRule(f, ref)) {
						if(f.textLength == 0 && f.text.textSize > 0){
							fillTextProperties(f, rc);
							return name;
						}
					}
				}
			}
		}
		return null;
	}

	private void fillTextProperties(FilterState f, RenderingContext rc) {
		rc.textSize = f.text.textSize;
		rc.textColor = f.text.textColor == 0 ? Color.BLACK : f.text.textColor;
		rc.textSize = f.text.textSize;
		rc.textMinDistance = f.text.textMinDistance;
		rc.showTextOnPath = f.text.textOnPath;
		Integer i = RenderingIcons.getIcons().get(f.text.textShield);
		rc.textShield = i== null ? 0 : i.intValue();
		rc.textWrapWidth = f.text.textWrapWidth;
		rc.textHaloRadius = f.text.textHaloRadius;
		rc.textBold = f.text.textBold;
		rc.textDy = f.text.textDy;
	}
}