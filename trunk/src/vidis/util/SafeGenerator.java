package vidis.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import vidis.data.sim.SimLink;
import vidis.data.sim.SimNode;
import vidis.data.var.AVariable;
import vidis.data.var.vars.DefaultVariable;
import vidis.ui.model.graph.layouts.GraphLayout;
import vidis.util.graphs.graph.Vertex;
import vidis.util.graphs.graph.WeightedGraph;
import vidis.util.graphs.graph.WeightedGraphImpl;
import vidis.util.graphs.graph.algorithm.ShortestPathAlgorithm;
import vidis.util.graphs.graph.algorithm.ShortestPathAlgorithmDijkstra;
import vidis.util.graphs.util.HeapNodeComparator;

/**
 * very safe generator even safer for unsafe operations made safe xD
 * 
 * @deprecated this class is deprecated, use a GraphLayout instead!
 * @see GraphLayout
 * @author dominik
 * 
 */
@Deprecated
public class SafeGenerator {
	private static final double aMin = 0.15;
	private static final double aMax = 1.0;
	private static final double dMin = 1.0;
	private static final double dMax = 6;
	private static double a;
	private static double d;
	static {
		setNodeDensity(.2);
	}
	private static List<Point3d> points = new LinkedList<Point3d>();

	private static double spirale_rt(double t) {
		return a * t;
	}

	private static double spirale_st(double t) {
		return a / 2 * (Math.log(Math.sqrt(t + 1) + 1) + t * Math.sqrt(t * t + 1));
	}

	private static double spirale_xt(double t) {
		return spirale_rt(t) * Math.cos(t);
	}

	private static double spirale_yt(double t) {
		return 0;
	}

	private static double spirale_zt(double t) {
		return spirale_rt(t) * Math.sin(t);
	}

	/**
	 * retrieve the next point for a node
	 * 
	 * @return a point3d
	 */
	public static Point3d nextNodePoint3d() {
		Point3d tmp = new Point3d();
		double pi64 = (Math.PI / 64);
		// distance
		double t = 0.0;
		while (spirale_st(t) <= (points.size() + 1) * d) {
			t += pi64;
		}
		tmp.x = spirale_xt(t);
		tmp.y = spirale_yt(t);
		tmp.z = spirale_zt(t);
		points.add(tmp);
		return tmp;
	}

	public static void reset() {
		points.clear();
	}

	/**
	 * the smaller the value, the more "dense" all points will be
	 * 
	 * @param density
	 *          the density to set (double [0..1])
	 */
	public static void setNodeDensity(double density) {
		density = Math.max(0.0, density);
		density = Math.min(1.0, density);
		a = density * (aMax - aMin) + aMin;
		d = density * (dMax - aMin) + dMin;
	}

	/**
	 * generate positions using the nodes, checking their connections and
	 * then applying some fancy algorithm over a adjacence matrix
	 * @param nodes a list of nodes (THAT MUST BE CONNECTED)
	 * @return mapping for node to a unique point in the universe
	 */
	public static void generateByDistance(List<SimNode> nodes) throws Exception {
		// init graph
		WeightedGraph graph = new WeightedGraphImpl( false );
		
		// init vertices
		Map<SimNode, Vertex> vertices = new HashMap<SimNode, Vertex>();
		for(int i=0; i<nodes.size(); i++) {
			SimNode node_a = nodes.get(i);
			if(!vertices.containsKey(node_a)) {
				Vertex vertex_a = new Vertex(node_a);
				vertices.put(node_a, vertex_a);
				graph.add(vertex_a);
			}
			for(int j=0; j<nodes.size(); j++) {
				SimNode node_b = nodes.get(j);
				if(!vertices.containsKey(node_b)) {
					Vertex vertex_b = new Vertex(node_b);
					vertices.put(node_b, vertex_b);
					graph.add(vertex_b);
				}
				SimLink link = generateByDistance_getConnectedLink(node_a, node_b);
				if(link != null) {
					// fine, they are directly connected; set distance = link.getDelay()
					graph.addEdge(vertices.get(node_a), vertices.get(node_b), link.getDelay());
				}
			}
		}
		
		// execute dijkstra on it
		ShortestPathAlgorithm spa = new ShortestPathAlgorithmDijkstra( graph, new HeapNodeComparator(-1) );
		
		// here we use some nice algorithm others invented:
		// ----- name: electric spring algorithm (may the force with you, luke!)
		// ----- theoretic base: http://www.ics.uci.edu/~ses/papers/grafdraw.pdf (site 5-7)
		
		// this ensures that this algorithm terminates
		int maximum_relaxations = 1000;
		
		double delta = 0;
		while(maximum_relaxations > 0 || delta > 0.01) {
			delta = 0;
			// for each vertex call our function
			for(int i=0; i<nodes.size(); i++) {
				delta += apply_electricSpringAlgorithm(spa, nodes, vertices, nodes.get(i));
			}
			maximum_relaxations--;
		}
	}
	
	private static double apply_electricSpringAlgorithm(ShortestPathAlgorithm spa, List<SimNode> nodes, Map<SimNode, Vertex> vertices, SimNode node) {
		// constants to be configured nicely
		double stiffness = 0.2;
		double electricalRepulsion = 0.2;
		double increment = 0.5; // just small increments
		double pingFactor = 0.4;
		
		// temporary variables (are here so that they are not initialized too often)
		double distance;
		double spring;
		double repulsion;
		
		// our vectors, which we work with
		Vector3d springVector = new Vector3d(0,0,0);
		Vector3d repulseVector = new Vector3d(0,0,0);
		
		// this is the force vector that will be applied to the position
		Vector3d forceVector = new Vector3d(0,0,0);
		
		Vertex adjVertex;
		Point3d adjPos;
		
		Vertex thisVertex = vertices.get(node);
		Point3d thisPos = (Point3d) node.getVariableById(AVariable.COMMON_IDENTIFIERS.POSITION).getData();
		
		for(int i=0; i<nodes.size(); i++) {
			SimNode adjNode = nodes.get(i);
			adjVertex = vertices.get(adjNode);
			adjPos = (Point3d) adjNode.getVariableById(AVariable.COMMON_IDENTIFIERS.POSITION).getData();
			// calculate distance between our node and this adjacent node
			distance = thisPos.distance(adjPos);
			
			// determine if this is a adjacent one
			Vertex v1 = vertices.get(node);
			Vertex v2 = vertices.get(adjNode);
			List<Vertex> shortestPath = spa.getShortestPath(v1, v2);
			if(shortestPath != null) {
				// if it is so, then get the distance
				// set it as spring length and calculate the spring force
				double springLength = spa.getDistance(thisVertex, adjVertex);
				springLength *= pingFactor;
				// if zero, modify it to be a very small value
				if(distance == 0)
					distance = 0.0001;
				
				if(springLength > 0) {
					// get the spring force between this node and all adjacent nodes
					spring = stiffness * Math.log( distance / springLength) * ((thisPos.x - adjPos.x) / distance );
					springVector.x += spring;
					
					spring = stiffness * Math.log( distance / springLength) * ((thisPos.y - adjPos.y) / distance );
					springVector.y += spring;
					
					spring = stiffness * Math.log( distance / springLength) * ((thisPos.z - adjPos.z) / distance );
					springVector.z += spring;
				}
			}
			
			// get the electric repulsion force between this node and ALL NODES
			repulsion = ( electricalRepulsion / distance ) * ((thisPos.x - adjPos.x) / distance );
			repulseVector.x += repulsion;
			
			repulsion = ( electricalRepulsion / distance ) * ((thisPos.y - adjPos.y) / distance );
			repulseVector.y += repulsion;
			
			repulsion = ( electricalRepulsion / distance ) * ((thisPos.z - adjPos.z) / distance );
			repulseVector.z += repulsion;
		}
		
		springVector.sub(repulseVector);
		
		// subtract forces
		forceVector.add(springVector);
		
		// scale the force nicely
		forceVector.scale(-increment);
		
		// finally apply the force
		thisPos.add(forceVector);
		
		// store it into our variable system
		((DefaultVariable)(node.getVariableById(AVariable.COMMON_IDENTIFIERS.POSITION))).update(thisPos);
		
		return forceVector.length();
	}

	private static SimLink generateByDistance_getConnectedLink(SimNode node_a, SimNode node_b) {
		List<SimLink> node_a_links = node_a.getConnectedLinksSim();
		for(int i=0; i<node_a_links.size(); i++) {
			SimLink link = node_a_links.get(i);
			SimNode tmp = link.getOtherNode(node_a);
			if(tmp.equals(node_b)) {
				return link;
			}
		}
		return null;
	}
}