package org.intocps.maestro.interpreter.values.variablestepsizecalculator;

import org.intocps.fmi.IFmiComponent;
import org.intocps.orchestration.coe.cosim.varstep.StepsizeInterval;
import org.intocps.orchestration.coe.json.InitializationMsgJson;

import java.util.Set;

public class VariableStepSizeCalculator {
    public Set<InitializationMsgJson.Constraint> constraints;
    public Set<IFmiComponent> fmiComponents;
    public StepsizeInterval stepsizeInterval;
    public double initialStepSize;
    public boolean supportsRollback = false;
    public double lastStepSize = -1;

    public VariableStepSizeCalculator() {

    }


}
