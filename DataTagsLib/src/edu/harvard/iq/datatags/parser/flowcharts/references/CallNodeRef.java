package edu.harvard.iq.datatags.parser.flowcharts.references;

import java.util.Objects;

/**
 * 
 * @author Michael Bar-Sinai
 */
public class CallNodeRef extends InstructionNodeRef {
    private final String calleeId;

    public CallNodeRef(TypedNodeHeadRef head, String calleeId) {
        super(head);
        this.calleeId = calleeId;
    }

    public String getCalleeId() {
        return calleeId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.calleeId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if ( ! (obj instanceof CallNodeRef) ) {
            return false;
        }
        final CallNodeRef other = (CallNodeRef) obj;
        if (!Objects.equals(this.calleeId, other.calleeId)) {
            return false;
        }
        return equalsAsInstructionNodeRef(other);
    }
    
    @Override
    public String toString() {
        return "[CallNodeRef id:" + getHead().getId() + " callee:" + getCalleeId() + "]";
    }
    
}