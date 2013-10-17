package edu.harvard.iq.datatags.runtime.listeners;

import edu.harvard.iq.datatags.runtime.Node;
import edu.harvard.iq.datatags.runtime.RuntimeEngine;
import edu.harvard.iq.datatags.runtime.exceptions.DataTagsRuntimeException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A runtime engine listener that records the nodes the engine visited.
 * To allow stdout logging as well, this listener is built with the decorator pattern.
 * 
 * @author michael
 */
public class RuntimeEngineTracingListener implements  RuntimeEngine.Listener {
	
	private RuntimeEngine.Listener decorated;
	private final LinkedList<Node> visitedNodes = new LinkedList<>();

	public RuntimeEngineTracingListener(RuntimeEngine.Listener decorated) {
		this.decorated = decorated;
	}

	public RuntimeEngineTracingListener() {
		this(new RuntimeEngineSilentListener());
	}
	
	public List<Node> getVisitedNodes() {
		return visitedNodes;
	}
	
	public List<String> getVisitedNodeIds() {
		List<String> out = new ArrayList<>(visitedNodes.size());
		for ( Node n : getVisitedNodes() ) {
			out.add( n.getId() );
		}
		return out;
	}
	
	@Override
	public void runStarted(RuntimeEngine ngn) {
		visitedNodes.clear();
		decorated.runStarted(ngn);
	}

	@Override
	public void nodeEntered(RuntimeEngine ngn, Node node) {
		visitedNodes.add( node );
		decorated.nodeEntered(ngn, node);
	}

	@Override
	public void runTerminated(RuntimeEngine ngn) {
		decorated.runTerminated(ngn);
	}

	@Override
	public void runError(RuntimeEngine ngn, DataTagsRuntimeException e) {
		decorated.runError(ngn, e);
	}
	
}