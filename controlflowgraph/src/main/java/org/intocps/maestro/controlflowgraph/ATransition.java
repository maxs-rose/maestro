package org.intocps.maestro.controlflowgraph;

public abstract class ATransition {
    public ABasicBlock to;

    public ATransition(ABasicBlock to) {
        this.to = to;
    }
}

