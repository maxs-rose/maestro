package org.intocps.maestro.plugin.synthesizer.instructions

import org.intocps.maestro.framework.fmi2.api.Fmi2Builder

abstract class ComplexCoSimInstruction(protected var retryActions: List<CoSimInstruction>, protected var simulationActions: List<CoSimInstruction>,
                                       protected var scope: Fmi2Builder.Scope<*>) : CoSimInstruction {
    override val isSimple: Boolean
        get() = false
}