import org.intocps.maestro.ast.MableAstFactory;
import org.intocps.maestro.ast.analysis.AnalysisException;
import org.intocps.maestro.ast.node.ABlockStm;
import org.intocps.maestro.controlflowgraph.ControlFlowGraph;
import org.junit.Test;

public class CfgTest {

    @Test
    public void testCfg() throws AnalysisException {
        ABlockStm blockStm = MableAstFactory.newABlockStm();
        blockStm.getBody().add(MableAstFactory.newExpressionStm(MableAstFactory.newAIntLiteralExp(1)));
        ABlockStm thenStm = MableAstFactory.newABlockStm();
        thenStm.getBody().add(MableAstFactory.newExpressionStm(MableAstFactory.newAIntLiteralExp(2)));
        ABlockStm elseStm = MableAstFactory.newABlockStm();
        elseStm.getBody().add(MableAstFactory.newExpressionStm(MableAstFactory.newAIntLiteralExp(3)));
        blockStm.getBody().add(MableAstFactory.newIf(MableAstFactory.newABoolLiteralExp(true), thenStm, elseStm));
        blockStm.getBody().add(MableAstFactory.newExpressionStm(MableAstFactory.newAIntLiteralExp(4)));
        ControlFlowGraph cfg = new ControlFlowGraph();
        cfg.createControlFlowGraph(blockStm);
    }
}
