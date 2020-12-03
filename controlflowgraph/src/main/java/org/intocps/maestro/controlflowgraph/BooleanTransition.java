package org.intocps.maestro.controlflowgraph;

public class BooleanTransition extends ATransition {
    public boolean condition;

    public BooleanTransition(BasicBlock to, boolean b) {
        super(to);
        this.condition = b;
    }
}
