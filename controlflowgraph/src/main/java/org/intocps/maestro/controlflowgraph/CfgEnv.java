package org.intocps.maestro.controlflowgraph;

public class CfgEnv {
    public ABasicBlock joinNode;
    BasicBlock currentBasicBlock;
    ABasicBlock successor;
    CfgEnv parent;

    public CfgEnv(CfgEnv env) {
        parent = env;
    }

    public ABasicBlock getJoinBlock() {
        if (this.joinNode == null) {
            return parent.joinNode;
        } else {
            return this.joinNode;
        }
    }
}
