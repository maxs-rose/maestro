import org.intocps.maestro.simulationentity.*;
import org.junit.Test;

import java.util.Arrays;

public class compositionExampleTest {

    @Test
    public void JacGaussTest() {
        Fmu jac1 = new Fmu("jac1");
        Fmu jac2 = new Fmu("jac2");
        Fmu gauss1 = new Fmu("gauss1");
        Fmu gauss2 = new Fmu("gauss2");
        Jacobi jac = new Jacobi();
        GaussSeidel gs = new GaussSeidel();

        FixedStep jacStep = new FixedStep(0.5);
        jac.setStepAlgorithm(jacStep);
        jac.setSimulationEntities(Arrays.asList(jac1, jac2, gs));

        VariableStep gsStep = new VariableStep(0.5, 0.1, 1.0);
        gs.setStepAlgorithm(gsStep);
        gs.setSimulationEntities(Arrays.asList(gauss1, gauss2));

        jac.configureMaxStep();
        gs.configureMaxStep();

        System.out.println(jac.print(0));
    }
}
