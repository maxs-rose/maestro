package org.intocps.maestro.plugin.synthesizer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import core.*
import org.intocps.maestro.ast.*
import org.intocps.maestro.ast.MableAstFactory.*
import org.intocps.maestro.ast.display.PrettyPrinter
import org.intocps.maestro.ast.node.ABlockStm
import org.intocps.maestro.ast.node.AImportedModuleCompilationUnit
import org.intocps.maestro.ast.node.PExp
import org.intocps.maestro.ast.node.PStm
import org.intocps.maestro.core.Framework
import org.intocps.maestro.core.messages.IErrorReporter
import org.intocps.maestro.framework.core.IRelation
import org.intocps.maestro.framework.core.ISimulationEnvironment
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder.DoubleVariable
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder.IntVariable
import org.intocps.maestro.framework.fmi2.api.mabl.FromMaBLToMaBLAPI
import org.intocps.maestro.framework.fmi2.api.mabl.MablApiBuilder
import org.intocps.maestro.framework.fmi2.api.mabl.MathBuilderFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.PortFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.variables.DoubleVariableFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.variables.VariableFmi2Api
import org.intocps.maestro.plugin.ExpandException
import org.intocps.maestro.plugin.IMaestroExpansionPlugin
import org.intocps.maestro.plugin.IPluginConfiguration
import org.intocps.maestro.plugin.SimulationFramework
import org.intocps.maestro.plugin.synthesizer.instructions.*
import org.intocps.orchestration.coe.config.InvalidVariableStringException
import org.slf4j.LoggerFactory
import scala.jdk.javaapi.CollectionConverters
import synthesizer.LoopStrategy
import synthesizer.SynthesizerSimple
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

@SimulationFramework(framework = Framework.FMI2)
class Synthesizer : IMaestroExpansionPlugin {
    val f1: AFunctionDeclaration = newAFunctionDeclaration(LexIdentifier("synthesizer", null),
            listOf(newAFormalParameter(newAArrayType(newANameType("FMI2Component")), newAIdentifier("component")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("stepSize")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("startTime")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("endTime"))),
            newAVoidType())

    var config: SynthesizerConfig? = null
    var compilationUnit: AImportedModuleCompilationUnit? = null
    private var math: MathBuilderFmi2Api? = null
    private var scalaBuilder: ScalaModelBuilder

    constructor() {
        scalaBuilder = ScalaModelBuilder()
    }

    constructor(scalaBuilder: ScalaModelBuilder) {
        this.scalaBuilder = scalaBuilder
    }

    private val declaredUnfoldFunctions: Set<AFunctionDeclaration>
        get() = Stream.of(f1).collect(Collectors.toSet())

    var defaultStepSize: DoubleVariable<PStm>? = null
    var stepSizesOfFMUs: HashMap<String, DoubleVariable<*>>? = null

    var fmuStates: List<Fmi2Builder.StateVariable<PStm>>? = null

    // Convergence related variables
    var absoluteTolerance: DoubleVariable<PStm>? = null
    var relativeTolerance: DoubleVariable<PStm>? = null
    var maxConvergeAttempts: IntVariable<PStm>? = null
    var maxStepAcceptAttempts: IntVariable<PStm>? = null

    @Throws(ExpandException::class)
    override fun expand(declaredFunction: AFunctionDeclaration, formalArguments: List<PExp>, config: IPluginConfiguration,
                        envIn: ISimulationEnvironment, errorReporter: IErrorReporter): List<PStm> {
        logger.debug("Unfolding: {}", declaredFunction.toString())
        val env = envIn as Fmi2SimulationEnvironment
        verifyArguments(formalArguments, env)
        val stepSize = formalArguments[1].clone()
        val startTime = formalArguments[2].clone()
        val endTime = formalArguments[3].clone()
        return try {
            logger.debug("Build FMUs")
            // Selected fun now matches funWithBuilder
            val builder = MablApiBuilder()
            val dynamicScope = builder.dynamicScope
            math = builder.mathBuilder

            // Convert raw MaBL to API
            // TODO: Create a reference value type
            val externalStepSize = builder.getDoubleVariableFrom(stepSize)
            val stepSizeVar = dynamicScope.store("step_size", 0.0)
            stepSizeVar.setValue(externalStepSize)
            // TODO: Create a reference value type
            val externalStartTime = DoubleVariableFmi2Api(null, null, null, null, startTime)
            val currentCommunicationTime = dynamicScope.store("current_communication_point", 0.0) as DoubleVariableFmi2Api
            currentCommunicationTime.setValue(externalStartTime)
            // TODO: Create a reference value type
            val externalEndTime = DoubleVariableFmi2Api(null, null, null, null, endTime)
            val endTimeVar = dynamicScope.store("end_time", 0.0) as DoubleVariableFmi2Api
            endTimeVar.setValue(externalEndTime)

            // Import the external components into Fmi2API
            val fmuInstances = FromMaBLToMaBLAPI.GetComponentVariablesFrom(builder, formalArguments[0], env)

            // Create bindings
            FromMaBLToMaBLAPI.CreateBindings(fmuInstances, env)
            val connections = scalaBuilder.createConnections(env, fmuInstances, IRelation.InternalOrExternal.External)
            val feedthrough = scalaBuilder.createConnections(env, fmuInstances, IRelation.InternalOrExternal.Internal)
            val fmuModelMap = scalaBuilder.buildFmuModels(fmuInstances, env, feedthrough)
            val scenarioModel = ScenarioModel(scalaBuilder.toScalaMap(fmuModelMap), CollectionConverters.asScala(connections).toList(), 2)

            // Convergence related variables
            absoluteTolerance = dynamicScope.store("absoluteTolerance", this.config!!.absoluteTolerance)
            relativeTolerance = dynamicScope.store("relativeTolerance", this.config!!.relativeTolerance)
            maxConvergeAttempts = dynamicScope.store("maxConvergeAttempts", this.config!!.maxIterations)
            maxStepAcceptAttempts = dynamicScope.store("maxStepAcceptAttempts", this.config!!.maxIterations)
            defaultStepSize = dynamicScope.store(0.0)
            defaultStepSize!!.setValue(externalStepSize)
            logger.debug("Initialization")
            //Todo add logic about Function calls that goes into and out of Initialization
            val synthesizer = SynthesizerSimple(scenarioModel, LoopStrategy.maximum())
            val initializationInstructions: List<InitializationInstruction> = CollectionConverters.asJava(synthesizer.synthesizeInitialization())
            val initProcedure: List<CoSimInstruction> = initializationInstructions.map { i: InitializationInstruction -> createInitInstructions(i, dynamicScope, fmuInstances) }
            initProcedure.forEach{ action: CoSimInstruction -> action.Perform() }

            // Create the iteration predicate
            val loopPredicate = currentCommunicationTime.toMath().addition(stepSizeVar).lessThan(endTimeVar)
            dynamicScope.enterWhile(loopPredicate)
            run {
                val stepInstructions: List<CosimStepInstruction> = CollectionConverters.asJava(synthesizer.synthesizeStep())
                val stepProcedure: List<CoSimInstruction> = stepInstructions.map { i: CosimStepInstruction -> createStepInstructions(i, dynamicScope, fmuInstances, currentCommunicationTime) }
                stepProcedure.forEach{ action: CoSimInstruction -> action.Perform() }

                // Update currentCommunicationTime
                currentCommunicationTime.setValue(currentCommunicationTime.toMath().addition(stepSizeVar))
                //Update the default step size to the normal step
                defaultStepSize!!.setValue(externalStepSize)
                //Update step size of each FMU
                fmuInstances.keys.forEach(Consumer { i: String -> stepSizesOfFMUs!!.replace(i, externalStepSize) })
            }
            val algorithm = builder.buildRaw() as ABlockStm
            algorithm.apply(ToParExp())
            println(PrettyPrinter.print(algorithm))
            algorithm.body
        } catch (e: Exception) {
            throw ExpandException("Internal error: ", e)
        }
    }

    private fun createInitInstructions(instruction: InitializationInstruction, dynamicScope: Fmi2Builder.Scope<*>,
                                       fmuInstances: Map<String, ComponentVariableFmi2Api>): CoSimInstruction {
        when (instruction) {
            is InitGet -> {
                return GetInstruction(fmuInstances.getValue(instruction.port().fmu()),
                        fmuInstances[instruction.port().fmu()]?.getPort(instruction.port().port())!!)
            }
            is InitSet -> {
                return SetInstruction(fmuInstances.getValue(instruction.port().fmu()),
                        fmuInstances[instruction.port().fmu()]?.getPort(instruction.port().port())!!)
            }
            is AlgebraicLoopInit -> return LoopSimInstruction(
                    dynamicScope,
                    maxStepAcceptAttempts!!,
                    absoluteTolerance!!,
                    relativeTolerance!!,
                    emptyList(),
                    CollectionConverters.asJava(instruction.iterate()).map { i: InitializationInstruction -> createInitInstructions(i, dynamicScope, fmuInstances) },
                    createConvergencePorts(CollectionConverters.asJava(instruction.untilConverged()), fmuInstances),
                    math!!
            )
            else -> throw UnknownError("This should never happen - an unknown operation was returned from the Scenario-Verifier Plugin");
        }
    }

    private fun createStepInstructions(instruction: CosimStepInstruction, dynamicScope: Fmi2Builder.Scope<*>,
                                       fmuInstances: Map<String, ComponentVariableFmi2Api>, currentCommunication: DoubleVariable<PStm>): CoSimInstruction {
        when (instruction) {
            is GetTentative -> return GetInstruction(fmuInstances.getValue(instruction.port().fmu()),
                    fmuInstances[instruction.port().fmu()]?.getPort(instruction.port().port())!!, true)
            is Get -> return GetInstruction(fmuInstances.getValue(instruction.port().fmu()),
                    fmuInstances[instruction.port().fmu()]?.getPort(instruction.port().port())!!)
            is core.Set -> return SetInstruction(fmuInstances.getValue(instruction.port().fmu()),
                    fmuInstances[instruction.port().fmu()]?.getPort(instruction.port().port())!!)
            is Step -> return StepInstruction(fmuInstances.getValue(instruction.fmu()), defaultStepSize!!, stepSizesOfFMUs!!, currentCommunication)
            is SaveState -> return SaveInstruction(fmuInstances.getValue(instruction.fmu()), fmuStates!!)
            is RestoreState -> return RestoreInstruction(fmuInstances.getValue(instruction.fmu()), fmuStates!!)
            is AlgebraicLoop -> {
                return LoopSimInstruction(
                        dynamicScope,
                        maxStepAcceptAttempts!!,
                        absoluteTolerance!!,
                        relativeTolerance!!,
                        CollectionConverters.asJava(instruction.ifRetryNeeded()).map { i: CosimStepInstruction -> createStepInstructions(i, dynamicScope, fmuInstances, currentCommunication) },
                        CollectionConverters.asJava(instruction.iterate()).map { i: CosimStepInstruction -> createStepInstructions(i, dynamicScope, fmuInstances, currentCommunication) },
                        createConvergencePorts(CollectionConverters.asJava(instruction.untilConverged()), fmuInstances),
                        math!!
                )
            }
            is StepLoop -> {
                return StepLoopInstruction(
                        dynamicScope,
                        maxStepAcceptAttempts!!,
                        CollectionConverters.asJava(instruction.ifRetryNeeded()).map { i: CosimStepInstruction -> createStepInstructions(i, dynamicScope, fmuInstances, currentCommunication) },
                        CollectionConverters.asJava(instruction.iterate()).map { i: CosimStepInstruction -> createStepInstructions(i, dynamicScope, fmuInstances, currentCommunication) },
                        CollectionConverters.asJava(instruction.untilStepAccept()).map { key: String -> fmuInstances[key] } as List<ComponentVariableFmi2Api>
                )
            }
            else -> throw UnknownError("This should never happen - an unknown operation was returned from the Scenario-Verifier Plugin");
        }
    }

    private fun createConvergencePorts(ports: List<PortRef>, fmuInstances: Map<String, ComponentVariableFmi2Api>): Map<ComponentVariableFmi2Api, Map<PortFmi2Api, VariableFmi2Api<Any>>> {
        val fmuToPorts = ports.groupBy { i -> i.fmu() }.map { i -> i.key to i.value.map { p -> fmuInstances.getValue(i.key).getPort(p.port()) }  }.toMap()
        return fmuToPorts.map { (fmu, ports) -> fmuInstances.getValue(fmu) to ports.map { port -> port to fmuInstances[fmu]?.getSingle(port.name)!!}.toMap()}.toMap()
    }


    @Throws(ExpandException::class)
    private fun verifyArguments(formalArguments: List<PExp>?, env: ISimulationEnvironment?) {
        //maybe some of these tests are not necessary - but they are in my unit test
        if (formalArguments == null || formalArguments.size != f1.formals.size) {
            throw ExpandException("Invalid args")
        }
        if (env == null) {
            throw ExpandException("Simulation environment must not be null")
        }
    }

    override fun requireConfig(): Boolean {
        return true
    }

    override fun getName(): String {
        return Synthesizer::class.java.simpleName
    }

    override fun getVersion(): String {
        return "0.0.1"
    }




    override fun getDeclaredImportUnit(): AImportedModuleCompilationUnit {
        if (compilationUnit != null) {
            return compilationUnit as AImportedModuleCompilationUnit
        }
        compilationUnit = AImportedModuleCompilationUnit()
        compilationUnit!!.imports = Stream.of("FMI2", "TypeConverter", "Math", "Logger").map { identifier: String? -> newAIdentifier(identifier) }.collect(Collectors.toList())
        val module = AModuleDeclaration()
        module.name = newAIdentifier(name)
        module.setFunctions(ArrayList(declaredUnfoldFunctions))
        compilationUnit!!.module = module
        return compilationUnit as AImportedModuleCompilationUnit
    }

    @Throws(IOException::class)
    override fun parseConfig(`is`: InputStream?): IPluginConfiguration? {
        var root = ObjectMapper().readTree(`is`)
        //We are only interested in one configuration, so in case it is an array we take the first one.
        if (root is ArrayNode) {
            root = root[0]
        }
        val stabilisation = root["stabilisation"]
        val fixedPointIteration = root["fixedPointIteration"]
        val absoluteTolerance = root["absoluteTolerance"]
        val relativeTolerance = root["relativeTolerance"]
        var conf: SynthesizerConfig? = null
        try {
            conf = SynthesizerConfig(stabilisation, fixedPointIteration, absoluteTolerance, relativeTolerance)
        } catch (e: InvalidVariableStringException) {
            e.printStackTrace()
        }
        return conf
    }


    class SynthesizerConfig : IPluginConfiguration {
        var stabilisation = false
        var maxIterations = 0
        var absoluteTolerance = 1.0
        var relativeTolerance = 1.0

        @Throws(InvalidVariableStringException::class)
        constructor(stabilisation: JsonNode?, fixedPointIteration: JsonNode?, absoluteTolerance: JsonNode?,
                    relativeTolerance: JsonNode?) {
            this.stabilisation = stabilisation?.asBoolean(false) ?: false
            maxIterations = fixedPointIteration?.asInt(5) ?: 5
            if (absoluteTolerance == null) {
                this.absoluteTolerance = 0.2
            } else {
                this.absoluteTolerance = absoluteTolerance.asDouble(0.2)
            }
            if (relativeTolerance == null) {
                this.relativeTolerance = 0.1
            } else {
                this.relativeTolerance = relativeTolerance.asDouble(0.1)
            }
        }
    }

    //class SynthesizerConfig(val endtime: Double, val maxIterations: Int, val absoluteTolerance: Double, val relativeTolerance: Double, val stabilisation: Boolean) : IPluginConfiguration {}

    companion object {
        val logger = LoggerFactory.getLogger(Synthesizer::class.java)
    }
}