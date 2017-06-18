package edu.harvard.iq.datatags.cli.commands;

import edu.harvard.iq.datatags.cli.CliRunner;
import edu.harvard.iq.datatags.externaltexts.LocalizationLoader;
import edu.harvard.iq.datatags.model.PolicyModel;
import edu.harvard.iq.datatags.model.graphs.DecisionGraph;
import edu.harvard.iq.datatags.model.graphs.nodes.AskNode;
import edu.harvard.iq.datatags.model.graphs.nodes.CallNode;
import edu.harvard.iq.datatags.model.graphs.nodes.ConsiderNode;
import edu.harvard.iq.datatags.model.graphs.nodes.EndNode;
import edu.harvard.iq.datatags.model.graphs.nodes.Node;
import edu.harvard.iq.datatags.model.graphs.nodes.RejectNode;
import edu.harvard.iq.datatags.model.graphs.nodes.SectionNode;
import edu.harvard.iq.datatags.model.graphs.nodes.SetNode;
import edu.harvard.iq.datatags.model.graphs.nodes.ToDoNode;
import edu.harvard.iq.datatags.model.types.AggregateSlot;
import edu.harvard.iq.datatags.model.types.AtomicSlot;
import edu.harvard.iq.datatags.model.types.CompoundSlot;
import edu.harvard.iq.datatags.model.types.SlotType;
import edu.harvard.iq.datatags.model.types.ToDoSlot;
import edu.harvard.iq.datatags.parser.decisiongraph.AstNodeIdProvider;
import edu.harvard.iq.datatags.runtime.exceptions.DataTagsRuntimeException;
import static edu.harvard.iq.datatags.util.StringHelper.nonEmpty;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * A command that creates a localization
 * @author michael
 */
public class CreateLocalizationCommand extends AbstractCliCommand {
    
    private String localizationName = null;
    private Path localizationPath = null;
    
    public CreateLocalizationCommand() {
        super("loc-create", "Creates a new localization for the current model.");
    }
    
    @Override
    public void execute(CliRunner rnr, List<String> args) throws Exception {
        // start new
        localizationPath = createLocalizationFolder(rnr);
        
        if ( localizationPath == null ) {
            rnr.println(" (localization creation canceled)");
            return;
        }
        
        createAnswersFile(rnr);
        createReadmeFile(rnr);
        createPolicySpace(rnr);
        createNodeFiles(rnr);
        
        // clean up
        localizationName=null;
        localizationPath=null;
        
    }
    
    /**
     * Prompts the user for the localization name, and creates the folder for it.
     * @param rnr
     * @return The path to the new localization folder, or {@code null} if the user canceled.
     * @throws IOException 
     */
    private Path createLocalizationFolder( CliRunner rnr ) throws IOException {
        localizationName = rnr.readLine("Localization name:");
        localizationName = localizationName.trim();
        if ( localizationName.isEmpty() ) return null;
        localizationName = localizationName.replaceAll("\\\\", "_").replaceAll("/", "_");
        Path localizationsDir = rnr.getModel().getDirectory()
                                    .resolve(LocalizationLoader.LOCALIZATION_DIRECTORY_NAME)
                                    .resolve(localizationName);
        
        if ( Files.exists(localizationsDir) ) {
            rnr.printWarning("A localization named '%s' already exists.", localizationName);
            return null;
        }
        Files.createDirectories(localizationsDir);
        
        return localizationsDir;
    }

    private void createAnswersFile(CliRunner rnr) throws IOException {
        rnr.print(" - Creating answers file");
        
        Set<String> answers = new TreeSet<>();
        DecisionGraph decisionGraph = rnr.getModel().getDecisionGraph();
        for ( Node nd : decisionGraph.nodes() ) {
            if ( nd instanceof AskNode ) {
                AskNode ask = (AskNode) nd;
                answers.addAll( ask.getAnswers().stream()
                                   .map(a->a.getAnswerText()).collect(toSet()));
            }
        }
        rnr.print(".");
        try ( BufferedWriter bwrt = Files.newBufferedWriter(localizationPath.resolve(LocalizationLoader.ANSWERS_FILENAME));
              PrintWriter prt = new PrintWriter(bwrt) ){
            List<String> orderedAnswers = new ArrayList<>(answers);
            Collections.sort(orderedAnswers);
            orderedAnswers.forEach( ans -> 
                prt.println( ans + ": " + ans )
            );
        }
        rnr.println("..Done");
    }

    private void createReadmeFile(CliRunner rnr) throws IOException {
        rnr.print(" - Creating readme.md file");
        PolicyModel pm = rnr.getModel();
        try ( BufferedWriter bwrt = Files.newBufferedWriter(localizationPath.resolve("readme.md"));
              PrintWriter prt = new PrintWriter(bwrt) ){
            prt.println("# " + pm.getMetadata().getTitle() );
            String subTitle = pm.getMetadata().getSubTitle();
            if ( subTitle!=null && ! subTitle.trim().isEmpty() ) {
                prt.println("### " + subTitle );
            }
            prt.println();
            prt.println("__Version " + pm.getMetadata().getVersion() + "__");
            prt.println();
            prt.println("(add about text here)");
        }
        rnr.println("...Done");
    }

    private void createPolicySpace(CliRunner rnr) throws IOException {
        rnr.print(" - Creating " + LocalizationLoader.SPACE_DATA_FILENAME + " file");
        try ( BufferedWriter bwrt = Files.newBufferedWriter(localizationPath.resolve(LocalizationLoader.ANSWERS_FILENAME));
              PrintWriter prt = new PrintWriter(bwrt) ){
            rnr.getModel().getSpaceRoot().accept( new SlotType.VoidVisitor(){
                
                LinkedList<String> stack = new LinkedList<>();
                
                @Override
                public void visitAtomicSlotImpl(AtomicSlot t) {
                    stack.push(t.getName());
                    String curPath = curPath();
                    
                    prt.println("# " + curPath );
                    prt.println(nonEmpty(t.getNote()) ? t.getNote() : ("<-- TODO: describe " + curPath) );
                    prt.println();
                    
                    t.values().forEach( v -> {
                        prt.println("# " + curPath + "/" + v.getName() );
                        prt.println(nonEmpty(v.getNote()) ? v.getNote() : ("<-- TODO: describe " + curPath + "/" + v.getName()) );
                        prt.println();
                    });
                    
                    stack.pop();
                }

                @Override
                public void visitAggregateSlotImpl(AggregateSlot t) {
                    stack.push(t.getName());
                    String curPath = curPath();
                    
                    prt.println("# " + curPath );
                    prt.println(nonEmpty(t.getNote()) ? t.getNote() : ("<-- TODO: describe " + curPath) );
                    prt.println();
                    
                    t.getItemType().values().forEach( v -> {
                        prt.println("# " + curPath + "/" + v.getName() );
                        prt.println(nonEmpty(v.getNote()) ? v.getNote() : ("<-- TODO: describe " + curPath + "/" + v.getName()) );
                        prt.println();
                    });
                    
                    stack.pop();

                }

                @Override
                public void visitCompoundSlotImpl(CompoundSlot t) {
                    stack.push(t.getName());
                    
                    prt.println("# " + curPath() );
                    prt.println(nonEmpty(t.getNote()) ? t.getNote() : ("<-- TODO: describe " + curPath() ) );
                    prt.println();
                    
                    t.getFieldTypes().forEach( ft -> ft.accept(this) );
                    
                    stack.pop();
                }

                @Override
                public void visitTodoSlotImpl(ToDoSlot t) {
                    
                }
                
                private String curPath() {
                    List<String> now = new ArrayList<>(stack);
                    Collections.reverse(now);
                    return now.stream().collect( joining("/") );
                }
            } );
        }
        rnr.println("...Done");
    }
    
    private void createNodeFiles(CliRunner rnr) throws IOException {
        rnr.print(" - Creating node files");
        
        final Path nodesDir = localizationPath.resolve(LocalizationLoader.NODE_DIRECTORY_NAME);
        if ( ! Files.exists(nodesDir) ) {
            Files.createDirectory(nodesDir);
        }
        
        Node.Visitor writer = new Node.VoidVisitor() {

            @Override
            public void visitImpl(AskNode nd) throws DataTagsRuntimeException {
                StringBuilder sb = new StringBuilder();
                sb.append(nd.getText());
                if ( ! nd.getTermNames().isEmpty() ) {
                    sb.append("\n");
                    sb.append("\n");
                    sb.append("### Terms\n");
                    nd.getTermOrder().forEach( termName -> sb.append("* *").append(termName)
                                                             .append("*: ")
                                                             .append(nd.getTermText(termName))
                                                             .append("\n")
                    );
                }
                createFileWithContent(nodesDir.resolve(nd.getId()+".md"), sb.toString());
            }

            @Override
            public void visitImpl(SectionNode nd) throws DataTagsRuntimeException {
                createFileWithContent(nodesDir.resolve(nd.getId()+".md"), nd.getTitle());
            }

            @Override
            public void visitImpl(RejectNode nd) throws DataTagsRuntimeException {
                createFileWithContent(nodesDir.resolve(nd.getId()+".md"), nd.getReason());
            }

            @Override
            public void visitImpl(ToDoNode nd) throws DataTagsRuntimeException {
                createFileWithContent(nodesDir.resolve(nd.getId()+".md"), nd.getTodoText());
            }

            @Override
            public void visitImpl(ConsiderNode nd) throws DataTagsRuntimeException {}
            @Override
            public void visitImpl(SetNode nd) throws DataTagsRuntimeException {}
            @Override
            public void visitImpl(CallNode nd) throws DataTagsRuntimeException {}
            @Override
            public void visitImpl(EndNode nd) throws DataTagsRuntimeException {}
        };

        rnr.getModel().getDecisionGraph().nodeIds()
                .stream().filter( id->!AstNodeIdProvider.isAutoId(id))
                .forEach( id->rnr.getModel().getDecisionGraph().getNode(id).accept(writer) );
        
        rnr.println("..Done");
    }

    
    private void createFileWithContent(Path p, String c) {
        try {
            Files.write(p, c.getBytes());
        } catch (IOException ex) {
            Logger.getLogger(CreateLocalizationCommand.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}