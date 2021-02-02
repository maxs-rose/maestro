package org.intocps.maestro.plugin.synthesizer.instructions

interface CoSimInstruction {
    fun Perform()
    val isSimple: Boolean
}