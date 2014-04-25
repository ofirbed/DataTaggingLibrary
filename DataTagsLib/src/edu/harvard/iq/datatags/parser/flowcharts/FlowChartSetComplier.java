package edu.harvard.iq.datatags.parser.flowcharts;

import edu.harvard.iq.datatags.model.types.TagValueLookupResult;
import edu.harvard.iq.datatags.model.charts.FlowChart;
import edu.harvard.iq.datatags.model.charts.FlowChartSet;
import edu.harvard.iq.datatags.model.charts.nodes.AskNode;
import edu.harvard.iq.datatags.model.charts.nodes.CallNode;
import edu.harvard.iq.datatags.model.charts.nodes.EndNode;
import edu.harvard.iq.datatags.model.charts.nodes.Node;
import edu.harvard.iq.datatags.model.charts.nodes.RejectNode;
import edu.harvard.iq.datatags.model.charts.nodes.SetNode;
import edu.harvard.iq.datatags.model.charts.nodes.TodoNode;
import edu.harvard.iq.datatags.model.types.AggregateType;
import edu.harvard.iq.datatags.model.types.CompoundType;
import edu.harvard.iq.datatags.model.types.SimpleType;
import edu.harvard.iq.datatags.model.types.TagType;
import edu.harvard.iq.datatags.model.types.ToDoType;
import edu.harvard.iq.datatags.model.values.AggregateValue;
import edu.harvard.iq.datatags.model.values.Answer;
import static edu.harvard.iq.datatags.model.values.Answer.Answer;
import edu.harvard.iq.datatags.model.values.CompoundValue;
import edu.harvard.iq.datatags.model.values.TagValue;
import edu.harvard.iq.datatags.parser.exceptions.BadSetInstructionException;
import edu.harvard.iq.datatags.parser.flowcharts.references.AnswerNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.AskNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.CallNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.EndNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.InstructionNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.NodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.RejectNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.SetNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.TermNodeRef;
import edu.harvard.iq.datatags.parser.flowcharts.references.TodoNodeRef;
import static edu.harvard.iq.datatags.util.CollectionHelper.C;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parser for the chart set graphs.
 * 
 * @author michael
 */
public class FlowChartSetComplier {
	
	private final Map<String, NodeRef> id2NodeRef = new HashMap<>();
	private final Map<String, List<CallNode>> callNodesToLink = new HashMap<>();
	
    /**
     * The base type of the data tags (typically, "DataTags").
     * While constructing {@link SetNode}s, this type is used to build node's
     * {@link DataTags} object.
     */
    private final CompoundType topLevelType;
    
    private final Map<TagType, TagType> parentType = new HashMap<>();
    
    public FlowChartSetComplier(CompoundType baseType) {
        this.topLevelType = baseType;
    }
    
    
	public FlowChartSet parse( String source, String unitName ) throws BadSetInstructionException { 
		return parse( new FlowChartASTParser().graphParser().parse(source), unitName );
	}
	
	public FlowChartSet parse( List<? extends InstructionNodeRef> parsedNodes, String unitName ) throws BadSetInstructionException { 
		// TODO implement the namespace, use unit name.
        
        buildParentTypeRelations();
		
		// Map node refs ids to the node refs.
		initIds( parsedNodes );
		
		FlowChartSet chartSet = new FlowChartSet();
        chartSet.setTopLevelType(topLevelType);
        
		FlowChart chart = new FlowChart( unitName + "-c1" );
		chartSet.addChart(chart);
		chartSet.setDefaultChartId( chart.getId() );
        try {
            for ( List<InstructionNodeRef> nodes : breakList(parsedNodes) ) {
                Node startNode = buildNodes( nodes, chart, chart.getEndNode() );
                if ( chart.getStart()== null ) { 
                    chart.setStart(startNode);
                }
            }
        } catch ( RuntimeException rte ) {
            Throwable cause = rte.getCause();
            if ( cause != null && cause instanceof BadSetInstructionException ) {
                throw (BadSetInstructionException)cause;
            } else { 
                throw rte;
            }
        }
		
		// TODO validation
		//  - Ids get to appear at most once. 
		//  - Set nodes has valid slot names and values
		//  - no unreachable nodes (e.g. chart with no id at start node)
		
        
		return chartSet;
	}
	
	/**
	 * Break a list of instruction nodes to reachable components. 
	 * The list generated by the AST parser contains connections that do not make semantic
	 * sense - namely, EndNodeRef's next pointer. We break the list on those 
	 * @param parsed
	 * @return 
	 */
	List<List<InstructionNodeRef>> breakList( List<? extends InstructionNodeRef> parsed ) {
		List<List<InstructionNodeRef>> res = new LinkedList<>();
		List<InstructionNodeRef> cur = new LinkedList<>();
		
		for ( InstructionNodeRef node : parsed ) {
			cur.add( node );
			if ( node instanceof EndNodeRef ) {
				res.add( cur );
				cur = new LinkedList<>();
			}
		}
		res.add( cur );
		
		return res;
	}
	
	/**
	 * Builds nodes from parsed node references
	 * @param nodes the parsed node reference list,
	 * @param chart The chart being built (nodes are added to it)
	 * @param defaultNode the node to go to when the chart ends. In effect, when a 
	 *		              strand of nodes ends in a non-terminating reference, this node
	 *					  is returned.
	 * @return the node at the root of the execution path.
	 */
	private Node buildNodes( final List<? extends InstructionNodeRef> nodes, final FlowChart chart, final Node defaultNode ) {
		
		InstructionNodeRef.Visitor<Node> builder = new InstructionNodeRef.Visitor<Node>(){
			@Override
			public Node visit(AskNodeRef askRef) {
				AskNode res = new AskNode( askRef.getId() );
				
				res.setText( askRef.getTextNode().getText() );
				for ( TermNodeRef termRef : askRef.getTerms() ) {
					res.addTerm(termRef.getTerm(), termRef.getExplanation() );
				}
				
				Node syntacticallyNext = buildNodes(C.tail(nodes), chart, defaultNode );
				
				for ( AnswerNodeRef ansRef : askRef.getAnswers() ) {
					res.setNodeFor( Answer(ansRef.getAnswerText()), 
								    buildNodes(ansRef.getImplementation(), chart, syntacticallyNext)
								  );
				}
				
				// if the question is implied binary, we add implied answers.
				for ( Answer ans : impliedAnswers(res) ) {
					res.setNodeFor(ans, syntacticallyNext);
				} 
				
				return chart.add( res );
			}

			@Override
			public Node visit(CallNodeRef callRef) {
				CallNode res = new CallNode( callRef.getId() );
				res.setCalleeChartId( chart.getId() );
				res.setCalleeNodeId( callRef.getCalleeId() );
				if ( ! callNodesToLink.containsKey(res.getCalleeNodeId()) ) {
					callNodesToLink.put( res.getCalleeNodeId(), new LinkedList<CallNode>() );
				}
				callNodesToLink.get(res.getCalleeNodeId()).add( res );
				res.setNextNode( buildNodes(C.tail(nodes), chart, defaultNode) );
				return chart.add( res );
			}

			@Override
			public Node visit(EndNodeRef endRef) {
				return endRef.getId().startsWith("$") // LATER should be "isAuto", when id is a full object
						? chart.getEndNode()
						: new EndNode( endRef.getId() );
			}	

            @Override
            public Node visit(RejectNodeRef setRef) {
                return chart.add(new RejectNode( setRef.getId(), setRef.getReason() ));
            }
            
			@Override
			public Node visit(SetNodeRef setRef) {
                try {
                    SetNode res = new SetNode( buildDataTags(setRef), setRef.getId() );
                    res.setNextNode(buildNodes( C.tail(nodes), chart, defaultNode));
                    return chart.add( res );
                } catch (BadSetInstructionException ex) {
                    throw new RuntimeException("Bad Set", ex );
                }
			}

			@Override
			public Node visit(TodoNodeRef todoRef) {
				TodoNode res = new TodoNode( todoRef.getId() );
				res.setTodoText(todoRef.getTodoText() );
				res.setNextNode( buildNodes( C.tail(nodes), chart, defaultNode) );
				return chart.add(res);
			}
		};
		
		return nodes.isEmpty() ? defaultNode : C.head( nodes ).accept(builder);
	}
	
	CompoundValue buildDataTags( SetNodeRef nodeRef ) throws BadSetInstructionException {
		CompoundValue topLeveltypeInstance = topLevelType.createInstance();
        
        for ( String slotName : nodeRef.getSlotNames() ) {
            TagValueLookupResult slr = topLevelType.lookupValue( slotName, nodeRef.getValue(slotName));
            
            try {
                slr.accept( setOrFail(topLeveltypeInstance, slr) );
            } catch ( BadSetInstructionException ble ) {
                throw new BadSetInstructionException(ble.getBadResult(), nodeRef);
            }
        }
        
		return topLeveltypeInstance;
	}

    /**
     * Return an object that, for successful matches, builds the compound value to 
     * contain the matched value. For failures - throws an error.
     * @param topLevelInstance the value that's added to
     * @param lookupResult the result of the value lookup
     * @return Object building {@code topLevelInstance}.
     */
    private TagValueLookupResult.SuccessFailVisitor<CompoundValue, BadSetInstructionException> 
    setOrFail(final CompoundValue topLevelInstance, final TagValueLookupResult lookupResult) {
        return new TagValueLookupResult.SuccessFailVisitor<CompoundValue, BadSetInstructionException>() {
            @Override
            public CompoundValue visitSuccess(TagValueLookupResult.Success s) throws BadSetInstructionException {
                buildValue(topLevelInstance, C.tail(buildTypePath(s.getValue())), s.getValue());
                
                return topLevelInstance;
            }
            
            @Override
            public CompoundValue visitFailure(TagValueLookupResult s) throws BadSetInstructionException {
                throw new BadSetInstructionException(lookupResult, null);
        }};
    }
    
    void buildValue( final CompoundValue value, final List<TagType> path, final TagValue endValue ) {
        C.head(path).accept(new TagType.VoidVisitor() {

            @Override
            public void visitSimpleTypeImpl(SimpleType t) {
                value.set(endValue);
            }

            @Override
            public void visitAggregateTypeImpl(AggregateType t) {
                if ( value.get(t) == null ) {
                    value.set( t.createInstance() );
                }
                ((AggregateValue)value.get(t)).add( ((AggregateValue)endValue).getValues() );
            }

            @Override
            public void visitCompoundTypeImpl(CompoundType t) {
                if ( value.get(t) == null ) {
                    value.set( t.createInstance() );
                }
                buildValue( (CompoundValue)value.get(t), C.tail(path), endValue);
            }

            @Override
            public void visitTodoTypeImpl(ToDoType t) {
                value.set(endValue);
            }
        });
    }
	
	List<Answer> impliedAnswers( AskNode node ) {
		Set<Answer> answers = node.getAnswers();
		if ( answers.size() > 1 ) return Collections.emptyList();
		if ( answers.isEmpty() ) return Arrays.asList( Answer.NO, Answer.YES ); // special case, where both YES and NO lead to the same options. 
		// MAYBE issue a warning/suggestion to make this a (todo: ) node.
		Answer onlyAns = answers.iterator().next();
		// TODO parser should use singleton answers YES/NO, to avoid this lowercase equality test
		String ansText = onlyAns.getAnswerText().trim().toLowerCase();
		switch( ansText ) {
			case "yes": return Collections.singletonList( Answer.NO );
			case "no" : return Collections.singletonList( Answer.YES );
			default: return Collections.emptyList();	
		}
	}
    
	/**
	 * Inits all the ids of the nodes in the list. Also, collects the 
	 * user-assigned ids in {@link #id2NodeRef}.
	 * @param nodeRefs 
	 */
	protected void initIds( List<? extends InstructionNodeRef> nodeRefs ) {
		final InstructionNodeRef.Visitor<Void> visitor = new InstructionNodeRef.Visitor<Void>() {
			
			private int nextId=0;
			
			@Override
			public Void visit(AskNodeRef askRef) {
				visitSimpleNode( askRef );
				visitSimpleNode( askRef.getTextNode() );
				for ( TermNodeRef tnd:askRef.getTerms() ) {
					visitSimpleNode(tnd);
				}
				
				for ( AnswerNodeRef ans : askRef.getAnswers() ) {
					visitSimpleNode(ans);
					for ( InstructionNodeRef inr : ans.getImplementation() ) {
						inr.accept(this);
					}
				}
				
				return null;
			}

			@Override
			public Void visit(CallNodeRef callRef) {
				return visitSimpleNode(callRef);
			}

			@Override
			public Void visit(EndNodeRef nedRef) {
				return visitSimpleNode(nedRef);
			}

			@Override
			public Void visit(SetNodeRef setRef) {
				return visitSimpleNode(setRef);
			}

			@Override
			public Void visit(TodoNodeRef todoRef) {
				return visitSimpleNode(todoRef);
			}
            
            @Override
            public Void visit( RejectNodeRef rej ) {
                return visitSimpleNode(rej);
            }
			
			private Void visitSimpleNode( NodeRef simpleNodeRef ) {
				if ( simpleNodeRef.getId() == null ) {
					simpleNodeRef.setId(nextId());
				} 
				id2NodeRef.put(simpleNodeRef.getId(), simpleNodeRef);
                
				return null;
			}
			
			private String nextId() {
				return "$"+(nextId++);
			}
		};
		
		for ( InstructionNodeRef inr: nodeRefs ) {
			inr.accept(visitor);
		}
	}
    
    /**
     * Builds a path, made of {@link TagType} instances, from {@link #topLevelType}
     * to the passed value.
     * @param value
     * @return Types, from the top to the value's type.
     */
    List<TagType> buildTypePath( TagValue value ) {
        LinkedList<TagType> path = new LinkedList<>();
        
        TagType tp = value.getType();
        while( tp != null ) {
            path.addFirst(tp);
            tp = parentType.get(tp);
        }
        
        return path;
    }
    
    void buildParentTypeRelations() {
        final Deque<TagType> types = new LinkedList<>();
        types.add( topLevelType );
        
        while ( ! types.isEmpty() ) {
            types.pop().accept( new TagType.VoidVisitor() {

                @Override
                public void visitCompoundTypeImpl(CompoundType t) {
                    for ( TagType fieldType : t.getFieldTypes() ) {
                        parentType.put(fieldType, t);
                        if ( fieldType instanceof CompoundType ) {
                            types.add( fieldType );
                        }
                    }
                }

                @Override public void visitSimpleTypeImpl(SimpleType t) {}
                @Override public void visitAggregateTypeImpl(AggregateType t) {}
                @Override public void visitTodoTypeImpl(ToDoType t) {}
            });
        }
    }

}
