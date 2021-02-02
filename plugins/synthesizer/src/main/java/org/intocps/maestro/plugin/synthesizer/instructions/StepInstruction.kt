package org.intocps.maestro.plugin.synthesizer.instructions

import org.intocps.maestro.ast.node.PStm
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder.DoubleVariable
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api
import java.util.*

class StepInstruction(fmu: ComponentVariableFmi2Api, private val stepSize: DoubleVariable<PStm>,
                      private val stepSizesOfFMUs: HashMap<String, DoubleVariable<*>>, private val currentCommunicationTime: DoubleVariable<PStm>) : FMUCoSimInstruction(fmu) {
    private val hasRejected = false
    override fun Perform() {
        val stepState = FMU.step(currentCommunicationTime, stepSize)
        stepSizesOfFMUs.replace(FMU.name, stepState.value)
        stepSize.setValue(stepState.value)
    }
}