package org.intocps.maestro.controlflowgraph;

import org.intocps.maestro.ast.analysis.AnalysisException;
import org.intocps.maestro.ast.analysis.DepthFirstAnalysisAdaptorQuestion;
import org.intocps.maestro.ast.node.ABlockStm;
import org.intocps.maestro.ast.node.AExpressionStm;
import org.intocps.maestro.ast.node.AIfStm;
import org.intocps.maestro.ast.node.INode;

import java.util.ArrayList;
import java.util.List;

public class ControlFlowGraph extends DepthFirstAnalysisAdaptorQuestion<CfgEnv> {
    List<ABasicBlock> basicBlocks = new ArrayList<>();

    @Override
    public void caseABlockStm(ABlockStm node, CfgEnv env) throws AnalysisException {
        // Retrieve the join before possibly changing it.
        // The reason for this is that an a block statement
        // can contain several basic blocks, and the last basic block has to jump to joinnode.
        ABasicBlock blockStmJoinNode = env.getJoinBlock();

        for (int i = 0; i < node.getBody().size(); i++) {
            INode n = node.getBody().get(i);
            // An if marks the end of a basic block.
            if (n instanceof AIfStm) {
                /** Create a join basic block for the progression after the if
                 *     [a]
                 *  [b]   [c]
                 *    [join]
                 **/
                BasicBlock joinNode = new BasicBlock();
                env.joinNode = joinNode;
                // Compute the if
                n.apply(this, env);
                // Set the current basic block to joinNode
                env.currentBasicBlock = joinNode;
            } else {
                n.apply(this, env);
            }
        }

        // The end of the block statements marks an end of a basic block.
        // The successor therefore becomes the join node.
        env.currentBasicBlock.addSuccessor(new JoinTransition(blockStmJoinNode));
        // Add to basic blocks
        this.basicBlocks.add(env.currentBasicBlock);

    }

    @Override
    public void caseAExpressionStm(AExpressionStm node, CfgEnv question) throws AnalysisException {
        question.currentBasicBlock.nodes.add(node);
    }

    @Override
    public void caseAIfStm(AIfStm node, CfgEnv env) throws AnalysisException {
        // Add the test expression to current basic block
        env.currentBasicBlock.nodes.add(node.getTest());
        // This basic block finishes. Add it to basicBlocks
        this.basicBlocks.add(env.currentBasicBlock);
        // Create a then Basic Block
        BasicBlock then = new BasicBlock();
        env.currentBasicBlock.addSuccessor(new BooleanTransition(then, true));
        // Create a new environment for the then clause
        CfgEnv thenEnv = new CfgEnv(env);
        thenEnv.currentBasicBlock = then;
        node.getThen().apply(this, thenEnv);
        // Create an else Basic Block
        BasicBlock elseBlock = new BasicBlock();
        env.currentBasicBlock.addSuccessor(new BooleanTransition(elseBlock, false));
        // Create a new environment for the else clause
        CfgEnv elseEnv = new CfgEnv(env);
        elseEnv.currentBasicBlock = elseBlock;
        node.getElse().apply(this, elseEnv);
    }

    public void createControlFlowGraph(INode node) throws AnalysisException {
        CfgEnv env = new CfgEnv(null);
        env.joinNode = new EndBasicBlock();
        env.currentBasicBlock = new BasicBlock();
        node.apply(this, env);
    }
}
