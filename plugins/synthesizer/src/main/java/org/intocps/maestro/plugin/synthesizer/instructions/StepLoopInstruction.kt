package org.intocps.maestro.plugin.synthesizer.instructions

import org.intocps.maestro.ast.node.PStm
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder.IntVariable
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.variables.IntVariableFmi2Api
import java.util.function.Consumer

class StepLoopInstruction(scope: Fmi2Builder.Scope<*>, private val maxStepAcceptAttempts: IntVariable<PStm>, retryActions: List<CoSimInstruction>,
                          simulationActions: List<CoSimInstruction>, private val convergeFMUs: List<ComponentVariableFmi2Api>) : ComplexCoSimInstruction(retryActions, simulationActions, scope) {

    override fun Perform() {
        val commonStepNotPerformed = scope.store(true)
        val iterationCounter= scope.store(0) as IntVariableFmi2Api
        val loopPredicate = iterationCounter.toMath().addition(1).lessThan(maxStepAcceptAttempts).and(commonStepNotPerformed.toPredicate())
        scope.enterWhile(loopPredicate)
        run {
            simulationActions.forEach{act: CoSimInstruction -> act.Perform() }




        }

        /*var predicate = ConvergencePredicate();

        var convergenceIfScope = scope.enterIf(predicate);
        convergenceIfScope.enterThen();

        retryActions.forEach(CoSimInstruction::Perform);

        convergenceIfScope.enterElse();
        //has converged
        commonStepNotPerformed.setValue(false);



         */scope!!.leave()
    }

    private fun ConvergencePredicate() {}
}