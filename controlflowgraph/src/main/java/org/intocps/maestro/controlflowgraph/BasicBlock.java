package org.intocps.maestro.controlflowgraph;

import org.intocps.maestro.ast.node.INode;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock extends ABasicBlock {
    public List<INode> nodes = new ArrayList<>();
    public List<ATransition> successors = new ArrayList<>();

    public void addSuccessor(ATransition transition) {
        this.successors.add(transition);
    }
}

