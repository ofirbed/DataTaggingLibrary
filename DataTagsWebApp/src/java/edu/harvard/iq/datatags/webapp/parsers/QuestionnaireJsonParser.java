package edu.harvard.iq.datatags.webapp.parsers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.harvard.iq.datatags.questionnaire.Answer;
import edu.harvard.iq.datatags.questionnaire.DecisionNode;
import edu.harvard.iq.datatags.tags.AuthenticationType;
import edu.harvard.iq.datatags.tags.DataTags;
import edu.harvard.iq.datatags.tags.DuaAgreementMethod;
import edu.harvard.iq.datatags.tags.EncryptionType;
import edu.harvard.iq.datatags.tags.HarmLevel;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Parses a JSON file into a questionnaire, and a list of titles (needed for the demo).
 * 
 * @author michael
 */
public class QuestionnaireJsonParser {
	
	private static final Logger logger = Logger.getLogger(QuestionnaireJsonParser.class.getName());
	
	private List<String> topLevelNames;
	private DecisionNode root;
	private String name;
	private final Map<String, HarmLevel> harmLevelNames = new HashMap<>();
	private final Map<String, EncryptionType> encryptionNames = new HashMap<>();
	private final Map<String, AuthenticationType> authTypeNames = new HashMap<>();
	private final Map<String, DuaAgreementMethod> duaAgreementMenthodNames = new HashMap<>();
	
	
	static class NameParseResult {
		Answer ans;
		String id;
		String title;
		String info;
	}
	
	static class NodeParseResult {
		Answer ans;
		DecisionNode node;
	}
	
	public QuestionnaireJsonParser() {
		harmLevelNames.put( "none", HarmLevel.NoRisk );
		harmLevelNames.put( "negligible", HarmLevel.Minimal );
		harmLevelNames.put( "criminal", HarmLevel.CivilPenalties );
		
		authTypeNames.put( "none", AuthenticationType.None );
		authTypeNames.put( "approval", AuthenticationType.Password );
		
		encryptionNames.put( "clear", EncryptionType.Clear );
		encryptionNames.put( "encrypt", EncryptionType.Encrypted );
		
		duaAgreementMenthodNames.put("none", DuaAgreementMethod.None);
		duaAgreementMenthodNames.put("online", DuaAgreementMethod.ClickThrough);
		duaAgreementMenthodNames.put("sign", DuaAgreementMethod.Sign);
		
	}
	
	public void parse( URL source ) throws IOException {
		ObjectMapper omp = new ObjectMapper();
		JsonNode parsedRoot = omp.readTree( source );
		readRoot( parsedRoot );
	}
	
	/**
	 * Reads the parts that are unique for the root: the name, and the 
	 * category names.
	 * @param parsedRoot 
	 */
	private void readRoot(JsonNode parsedRoot) {
		name = parsedRoot.get("name").asText();
		topLevelNames = new LinkedList<>();
		DecisionNode lastAdded = null;
		for ( JsonNode jsNode : Iterator2Iterable.cnv(parsedRoot.get("children").iterator())) {
			String nodeName = jsNode.get("name").textValue();
			if ( Character.isDigit( nodeName.charAt(0)) ) {
				topLevelNames.add( nodeName.split(" ",2)[1].split("\\.")[0].trim() );
				NodeParseResult ndPr = parseBDD( jsNode );
				if ( root == null ) {
					root = ndPr.node;
				} else {
					assignOpenEndsTo( lastAdded, ndPr.node );
				}
				lastAdded = ndPr.node;
			}
		}
	}
	
	private NodeParseResult parseBDD( JsonNode jsNode ) {
		 final NameParseResult nmPr = parseNameField( jsNode.get("name").textValue() );
		 
		 final DecisionNode dNode = new DecisionNode(nmPr.id);
		 dNode.setTitle( nmPr.title );
		 dNode.setQuestionText( nmPr.info );
		 
		 JsonNode tags = jsNode.get("tags");
		 if ( tags != null ) {
			String tagsStr = jsNode.get("tags").textValue();
			dNode.setBaseAssumption( parseTagsString(tagsStr) );
		 }
		 
		 if ( jsNode.has("children") ) {
			for ( JsonNode subJsNode : Iterator2Iterable.cnv(jsNode.get("children").iterator()) ) {
				NodeParseResult ndPr = parseBDD( subJsNode );
				if ( ndPr.ans != null ) {
					dNode.setNodeFor(ndPr.ans, ndPr.node);
				} 
			}
		 }
		 return new NodeParseResult(){{
			 ans  = nmPr.ans;
			 node = dNode;
		 }};
	}
	
	/**
	 * Assigns all the open answers in {@literal tree} to {@literal node}.
	 * @param tree The root of the tree that might not be assigned yet
	 * @param node the destination of the unanswered questions
	 */
	private void assignOpenEndsTo(DecisionNode tree, DecisionNode node) {
		if ( tree.getAbsoluteAssumption().isComplete() ) return;
		for ( Answer a : Answer.values() ) {
			DecisionNode answerNode = tree.getNodeFor(a);
			
			if ( answerNode == node ) return;
			
			if ( answerNode == null ) {
				tree.setNodeFor(a, node);
			} else {
				assignOpenEndsTo(answerNode, node);
			}
		}
	}
	
	
	/**
	 * Takes the string value of the {@literal name} field, and parses
	 * it to a result. There are 2 possible formats, one with answer
	 * type (YES/NO) and one without.
	 * @param nameField
	 * @return A parse result from which we can build a {@link DecisionNode}.
	 */
	NameParseResult parseNameField( String nameField ) {
		NameParseResult npr = new NameParseResult();
		
		String comps[] = nameField.split("\\.",2);
		npr.ans = Answer.valueOfOpt( comps[0].toUpperCase() );
		
		if ( npr.ans != null ) {
			nameField = nameField.substring( comps[0].length() + 1 ).trim();
		}
		// INVARIANT: nameField does not contain an answer.
		comps = nameField.split(" ",2);
		npr.id = comps[0].trim();
		
		comps = comps[1].split("\\.",2);
		npr.title = comps[0].trim();
		npr.info = comps.length > 1 ? (comps[1].trim().isEmpty() ? null : comps[1].trim())
									: null;
		
		return npr;
	}
	
	private DataTags parseTagsString( String tags ) {
		// initial  cleanning
		tags = tags.trim();
		if ( tags.startsWith("[") ) {
			tags = tags.substring(1);
		}
		if ( tags.endsWith("]") ) {
			tags = tags.substring(0, tags.length()-1);
		}
		tags = tags.trim();
		String[] fields = tags.split(",");
		
		Map<String,String> values = new HashMap<>();
		for ( String f : fields ) {
			f = f.trim();
			if ( f.isEmpty() || f.equals("___") ) continue;
			String[] comps = f.split("=");
			if ( comps.length == 1 ) {
				values.put("__COLOR__", comps[0] );
			} else {
				comps[1] = comps[1].trim().toLowerCase();
				if ( (!comps[1].isEmpty()) && (!comps[1].equals("___")) ) {
					values.put( comps[0].trim().toLowerCase(), comps[1] );
				}
			}
		}
		
		DataTags res = new DataTags();
		for ( Map.Entry<String, String> e : values.entrySet() ) {
			String key = e.getKey();
			String value = e.getValue();

			switch ( key ) {
				case "harm":
					return harmLevelNames.get(values.get("harm")).tags();
					
				case "store" : 
					res.setStorageEncryptionType( encryptionNames.get(value) );
					break;
					
				case "transfer" :
					res.setTransitEncryptionType( encryptionNames.get(value) );
					break;
					
				case "auth" :
					res.setAuthenticationType( authTypeNames.get(value) );
					break;
					
				case "dua" :
					res.setDuaAgreementMethod(duaAgreementMenthodNames.get(value) );
					break;
			}
		}
		return res;
	}
	
	private void dumpNode( JsonNode nd, int depth ) {
		StringBuilder sb = new StringBuilder( depth );
		for ( int i=0; i<depth; i++ ) sb.append( " " );
		String prefix = sb.toString();
		
		System.out.println(prefix + "===  Node [" + nd.getNodeType()  +"]");
		switch ( nd.getNodeType() ) {
			case OBJECT:
				System.out.println(prefix + "names:");
				for ( String str : Iterator2Iterable.cnv(nd.fieldNames()) ) {
					System.out.println(prefix + " " + str + " \t-> " + nd.get(str));
				}
				for ( JsonNode snd : Iterator2Iterable.cnv(nd.elements()) ) {
					dumpNode( snd, depth+1 );
				}
				break;
				
			case ARRAY:
				for ( int i=0; i<nd.size(); i++ ) {
					JsonNode sbnd = nd.get(i);
					System.out.println(String.format(prefix + " [%d]: %s", i, sbnd) );
				}
				for ( int i=0; i<nd.size(); i++ ) {
					dumpNode( nd.get(i), depth+1 );
				}
				break;
				
			default:
				System.out.println(prefix + "value:" + nd.asText());
		}
		System.out.println(prefix + "=== /Node");
	}
	
	public void parse( InputStream strm ) {
		JsonFactory fact = new JsonFactory();
		fact.enable(JsonParser.Feature.ALLOW_COMMENTS);

		try ( JsonParser jp = fact.createParser(strm) ) {
			parse( jp );
			
		} catch ( IOException e ) {
			logger.log( Level.SEVERE, "Error parsing JSON: " + e.getMessage(), e );
		} catch (QuestionnaireParseException ex) {
			logger.log( Level.SEVERE, "Error parsing qustionnaire format: " + ex.getMessage(), ex );
		}
		
		
	}

	public void parse( JsonParser json ) throws IOException, QuestionnaireParseException {
		topLevelNames = new LinkedList<>();
		root = null;
		name = null;
		
		if ( json.nextToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected data to start with an Object");
		}
		
		String idnt = " ";
		int d=1;
		while ( d != 0 ) {
			JsonToken nextT = json.nextToken();
//			System.out.println("nextT = " + nextT);
			
			if ( nextT == JsonToken.FIELD_NAME ) {
				String fieldName = json.getCurrentName();
				System.out.println( idnt + fieldName + ":" );
				
			} else if ( nextT == JsonToken.START_ARRAY ) {
				while ( json.nextToken() != JsonToken.END_ARRAY ) {
					System.out.println(idnt + " + " + json.getValueAsString() );
				}
				
			} else if ( nextT == JsonToken.START_OBJECT ) {
				idnt = idnt + "-";
				d++;
				
			} else if ( nextT == JsonToken.END_OBJECT ) {
				idnt = idnt.substring(1);
				d--;
						
			} else {
				String value = json.getValueAsString();
				System.out.println( idnt + " +--> " + value );
			}
		}
	}
	
	/**
	 * The main point of this method is to filter out the "about..." nodes,
	 * and to list the names of the other nodes.
	 * @param json 
	 */
	private void parseTopLevelChildern(JsonParser json) throws IOException, QuestionnaireParseException {
		if ( json.getCurrentToken()!= JsonToken.START_ARRAY ) {
			throw new QuestionnaireParseException("Parse childern expects an array");
		}
		
		Pattern validNodes = Pattern.compile("^\\d+\\. .*$");
		json.nextToken(); // start object
		logger.info("json.getCurrentToken() = " + json.getCurrentToken());
		json.nextToken(); // Object data
		logger.info("json.getCurrentToken() = " + json.getCurrentToken());
		String tagName = json.getCurrentName();
		if ( tagName.equals("name") ) {
			json.nextToken();
			String tagValue = json.getValueAsString();
			
			if ( validNodes.matcher(tagValue).matches() ) {
				System.out.println("tagValue = " + tagValue);
				skipToNodeEnd( json );
				
			} else {
				Logger.getLogger(QuestionnaireJsonParser.class.getName()).log(Level.INFO, "Filtering out top level node named ''{0}''", tagValue);
				skipToNodeEnd( json );
			}
			
		}
	}
	
	private void skipToNodeEnd( JsonParser json ) throws IOException {
		int depth = 1;
		while ( depth != 0 ) {
			JsonToken t = json.nextToken();
			if ( t == JsonToken.START_OBJECT ) depth++;
			if ( t == JsonToken.START_ARRAY ) depth++;
			if ( t == JsonToken.END_ARRAY ) depth--;
			if ( t == JsonToken.END_OBJECT ) depth--;
		}
		json.nextToken();
	}
	
	public List<String> getTopLevelNames() {
		return topLevelNames;
	}

	public DecisionNode getRoot() {
		return root;
	}

	public String getName() {
		return name;
	}
}

class Iterator2Iterable<T> implements Iterable<T> {
	
	static <S> Iterable<S> cnv( Iterator<S> anIt ) {
		return new Iterator2Iterable(anIt);
	}

	private final Iterator<T> it;

	public Iterator2Iterable(Iterator<T> it) {
		this.it = it;
	}
	
	@Override
	public Iterator<T> iterator() {
		return it;
	}
}