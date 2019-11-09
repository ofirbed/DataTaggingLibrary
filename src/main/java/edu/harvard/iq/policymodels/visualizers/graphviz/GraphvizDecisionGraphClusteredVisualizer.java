package edu.harvard.iq.policymodels.visualizers.graphviz;

import edu.harvard.iq.policymodels.model.decisiongraph.DecisionGraph;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.AskNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.CallNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.EndNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.Node;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.RejectNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.SetNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.ToDoNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.ConsiderNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.ContainerNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.ContinueNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.PartNode;
import edu.harvard.iq.policymodels.model.decisiongraph.nodes.SectionNode;
import edu.harvard.iq.policymodels.model.policyspace.values.AbstractValue;
import edu.harvard.iq.policymodels.model.policyspace.values.AggregateValue;
import edu.harvard.iq.policymodels.model.policyspace.values.AtomicValue;
import edu.harvard.iq.policymodels.model.policyspace.values.CompoundValue;
import edu.harvard.iq.policymodels.model.policyspace.values.ToDoValue;
import edu.harvard.iq.policymodels.runtime.exceptions.DataTagsRuntimeException;
import static edu.harvard.iq.policymodels.visualizers.graphviz.GvEdge.edge;
import static edu.harvard.iq.policymodels.visualizers.graphviz.GvNode.node;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import static java.util.stream.Collectors.joining;

/**
 * Given a {@link DecisionGraph}, instances of this class create gravphviz files
 * visualizing the decision graph flow.
 *
 * @author michael
 */
public class GraphvizDecisionGraphClusteredVisualizer extends AbstractGraphvizDecisionGraphVisualizer {
    
    /** Draws links between call nodes and their callees. */
    private boolean drawCallLinks = false;
    
    /**
     * Concentrates edges (Graphviz graph feature). When {@code true}, Graphviz will 
     * attempt to join edges that go to the same destination node.
     * 
     * Causes crash in Graphviz versions below 2.40, so should be turned off for these.
     */
    private boolean concentrate = true;
    
    private class NodePainter extends AbstractGraphvizDecisionGraphVisualizer.AbstractNodePainter {
        
        @Override
        public void visitImpl(ConsiderNode nd) throws DataTagsRuntimeException {
            out.println(node(nodeId(nd))
                    .shape(GvNode.Shape.egg)
                    .label(idLabel(nd) + "consider\n")
                    .gv());
            nd.getAnswers().forEach( option -> {
                if ( shouldLinkTo(nd.getNodeFor(option)) ) {
                    StringBuilder label = new StringBuilder();
                    option.getNonEmptySubSlots().forEach( tt -> {
                        label.append(indentedToString(option.get(tt)).replaceAll("\n","\\\\l"))
                             .append("\n");
                    });
                    advanceTo(nd.getNodeFor(option));
                    out.println(makeEdge(nd, nd.getNodeFor(option)).tailLabel(label.toString()).gv());
                }
            });
            
            if ( nd.getElseNode() != null ) {
                if ( shouldLinkTo(nd.getElseNode()) ) {
                    advanceTo(nd.getElseNode());
                    out.println(makeEdge(nd, nd.getElseNode()).tailLabel("else").gv());
                }
            }
        }

        @Override
        public void visitImpl(AskNode nd) throws DataTagsRuntimeException {
            String nodeText = nd.getText();
            if (nodeText.length() > 140) {
                nodeText = nodeText.substring(0, 140) + "...";
            }
            out.println(node(nodeId(nd))
                    .shape(GvNode.Shape.oval)
                    .label(idLabel(nd) + "ask\n" + wrap(nodeText))
                    .gv());
            nd.getAnswers().forEach( ans -> {
                if ( shouldLinkTo(nd.getNodeFor(ans)) ) {
                    advanceTo(nd.getNodeFor(ans));
                    out.println(makeEdge(nd, nd.getNodeFor(ans)).tailLabel(ans.getAnswerText()).gv());
                }
            });
        }

        @Override
        public void visitImpl(CallNode nd) throws DataTagsRuntimeException {
            out.println(node(nodeId(nd))
                    .label(idLabel(nd) 
                            + sanitizeIdDisplay(nd.getCalleeNode().getId()).replaceAll("/", "/\n")
                    )
                    .shape(GvNode.Shape.cds)
                    .fillColor("#BBBBFF")
                    .gv());
            if ( shouldLinkTo(nd.getNextNode()) ) {
                advanceTo(nd.getNextNode());
                out.println(makeEdge(nd, nd.getNextNode()).gv());
            }
        }

        @Override
        public void visitImpl(RejectNode nd) throws DataTagsRuntimeException {
            out.println(node(nodeId(nd))
                    .label(idLabel(nd) + "REJECT\n" + wrap(nd.getReason()))
                    .shape(GvNode.Shape.hexagon)
                    .fillColor("#FFAAAA")
                    .gv());
        }

        @Override
        public void visitImpl(ToDoNode nd) throws DataTagsRuntimeException {
            out.println(node(nodeId(nd))
                    .fillColor("#AAFFAA")
                    .shape(GvNode.Shape.note)
                    .label(idLabel(nd) + "todo\n" + wrap(nd.getTodoText())).gv());
            if ( shouldLinkTo(nd.getNextNode()) ) {
                advanceTo(nd.getNextNode());
                out.println(makeEdge(nd, nd.getNextNode()).gv());
            }
        }

        @Override
        public void visitImpl(SetNode nd) throws DataTagsRuntimeException {
            out.println(node(nodeId(nd))
                    .fillColor("#AADDAA")
                    .shape(GvNode.Shape.rect)
                    .label("Set\n" + indentedToString(nd.getTags()).replaceAll("\n","\\\\l") )
                    .gv());
            if ( shouldLinkTo(nd.getNextNode()) ) {
                advanceTo(nd.getNextNode());
                out.println(makeEdge(nd, nd.getNextNode()).gv());
            }
        }

        @Override
        public void visitImpl(EndNode nd) throws DataTagsRuntimeException {
            if ( drawEndNodes ) {
                out.println(node(nodeId(nd))
                    .shape(GvNode.Shape.point)
                    .fontColor("#AAAAAA")
                    .fillColor("#000000")
                    .add("height", "0.2")
                    .add("width", "0.2")
                    .label("x").gv());
            }
        }
        
        @Override
        public void visitImpl(ContinueNode nd) throws DataTagsRuntimeException {
            if ( drawEndNodes ) {
                out.println(node(nodeId(nd))
                        .shape(GvNode.Shape.point)
                        .fontColor("#AAAAAA")
                        .fillColor("#8833AA")
                        .add("height", "0.2")
                        .add("width", "0.2")
                        .label("c").gv());
            }
        }
        
        @Override
        public void visitImpl(SectionNode nd) throws DataTagsRuntimeException {
            out.println("subgraph cluster_" + nodeId(nd)  + "{ ");
            out.println("label=\"Section " + nd.getTitle() + "\"");
            out.println("color=\"#888888\"");
            advanceTo(nd.getStartNode());
            out.println("}");
            if ( shouldLinkTo(nd.getNextNode()) ) {
                advanceTo(nd.getNextNode());
                
                out.println(makeEdge(findDeepestDrawnNode(nd.getStartNode()), nd.getNextNode())
                            .add("ltail", "cluster_" + nodeId(nd)).gv() );
            }
        }

        @Override
        public void visitImpl(PartNode nd) throws DataTagsRuntimeException {
            String effTitle =  nd.getId();
            if ( nd.getTitle()!=null && (!nd.getTitle().trim().isEmpty()) ) {
                effTitle += "\\n" + nd.getTitle().trim();
            }
            out.println( node(sanitizeId(nd.getId()+"__[pstart]")).hidden().gv() );
            out.println("subgraph cluster_" + nodeId(nd)  + "{ ");
            out.println("label=\"Part " + effTitle +  "\"");
            advanceTo(nd.getStartNode());
            
            out.println("}");
            Node arrowDest = getFirstNonContainerNode(nd);
            GvEdge edge = edge(sanitizeId(nd.getId()+"__[pstart]"), nodeId(arrowDest));
            if ( nd.getStartNode() instanceof ContainerNode ) {
                edge = edge.add("lhead", "cluster_"+nodeId(nd.getStartNode()));
            }
            out.println(edge.gv() );
        }

    }

    @Override
    void printHeader(PrintWriter bOut) throws IOException {
        PrintWriter out = new PrintWriter(bOut, true);
        out.println("digraph decisionGraph {");
        out.println("graph [fontname=\"Courier\" " 
                    + (concentrate ? "concentrate=true " : "" )
                    + "compound=true]");
        
        out.println("edge [fontname=\"Courier\" fontsize=\"10\"]");
        out.println("node [fillcolor=\"lightgray\" style=\"filled\" fontname=\"Courier\" fontsize=\"10\"]");
        out.println(node(START_NODE_NAME)
                .label("start")
                .fillColor("transparent")
                .shape(GvNode.Shape.none)
                .fontColor("#008800")
                .fontSize(16)
                .gv());
        out.println("{rank=source; " + START_NODE_NAME + "}");
    }

    @Override
    protected void printBody(PrintWriter out) throws IOException {
        printChart(theGraph, new PrintWriter(out, true));
        out.println(makeEdge(START_NODE_NAME, theGraph.getStart())
                .color("#008800")
                .penwidth(4)
                .gv());
    }

    void printChart(DecisionGraph fc, PrintWriter wrt) throws IOException {
        wrt.println("subgraph cluster_" + sanitizeId(fc.getId()) + " {");
        wrt.println(String.format("label=\"%s\"", humanTitle(fc)));

        // group to subcharts
        Set<Node> subchartHeads = findSubchartHeades(fc);
        NodePainter np = new NodePainter();
        np.out = wrt;
        subchartHeads.forEach( chartHead -> {
            chartHead.accept(np);
        });
        
        if ( drawCallLinks ) {
            drawCallLinks(wrt);
        }
        
        wrt.println("edge [style=invis]");
        findPostSectionEdges().entrySet().stream()
            .map( kv ->makeEdge(kv.getKey(), kv.getValue()) )
            .map( GvEdge::gv )
            .forEach( wrt::println );
        
        wrt.println("}");

    }
    
    public void setDrawCallLinks(boolean drawCallLinks) {
        this.drawCallLinks = drawCallLinks;
    }

    public void setConcentrate(boolean concentrate) {
        this.concentrate = concentrate;
    }
    
    private static final String INDENT_15 = "               ";
    String indentedToString( AbstractValue cv ) {
        StringBuilder out = new StringBuilder();
        cv.accept( new AbstractValue.Visitor<Void>() {
            int indentLevel = 0;

            @Override
            public Void visitToDoValue(ToDoValue v) {
                add( v.getSlot().getName(), "(TODO)");
                return null;
            }

            @Override
            public Void visitAtomicValue(AtomicValue v) {
                add( v.getSlot().getName() + " = ", v.getName() );
                return null;
            }

            @Override
            public Void visitAggregateValue(AggregateValue v) {
                add( v.getSlot().getName() + " += ", v.getValues().stream()
                    .map( AtomicValue::getName )
                    .sorted()
                    .collect( joining(", ") )
                );
                return null;
            }

            @Override
            public Void visitCompoundValue(CompoundValue v) {
                add(v.getSlot().getName(), ":");
                indentLevel++;
                v.getNonEmptySubSlots().forEach(asl -> 
                    v.get(asl).accept(this)
                );
                indentLevel--;
                return null;
            }
            
            void add( String title, String values ) {
                indent();
                out.append( title ).append(values).append("\n");
            }
            
            void indent() {
                indent(indentLevel);
            }
            void indent(int amount) {
                if (amount == 0 ) return;
                if (amount < INDENT_15.length() ) {
                    out.append(INDENT_15.substring(0, indentLevel));
                } else {
                    out.append(INDENT_15);
                    indent(amount - INDENT_15.length());
                }
            }
        });
            
        return out.toString(); 
    }
}