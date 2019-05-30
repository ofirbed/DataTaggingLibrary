package edu.harvard.iq.datatags.model.graphs.nodes;

import edu.harvard.iq.datatags.runtime.exceptions.DataTagsRuntimeException;
import java.util.Objects;

/**
 * A node that tells the system to continue execution from its container's next node.
 * As an example, when getting to a {@code ContinueNode} while in a {@link SectionNode},
 * execution continues from the section's {@link SectionNode#getNextNode()}.
 * 
 * @author michael
 */
public class ContinueNode extends TerminalNode {

    public ContinueNode(String anId) {
        super(anId);
    }

    @Override
    public <R> R accept(Visitor<R> vr) throws DataTagsRuntimeException {
        return vr.visit( this );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof ContinueNode) {
            return Objects.equals(getId(), ((Node) o).getId());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return getId() != null ? getId().hashCode() : 0;
    }
    
    
    
}