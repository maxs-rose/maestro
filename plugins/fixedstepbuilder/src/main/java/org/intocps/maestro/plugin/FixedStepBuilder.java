package org.intocps.maestro.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.intocps.maestro.ast.AFunctionDeclaration;
import org.intocps.maestro.ast.AModuleDeclaration;
import org.intocps.maestro.ast.MableAstFactory;
import org.intocps.maestro.ast.ToParExp;
import org.intocps.maestro.ast.display.PrettyPrinter;
import org.intocps.maestro.ast.node.ABlockStm;
import org.intocps.maestro.ast.node.AImportedModuleCompilationUnit;
import org.intocps.maestro.ast.node.PExp;
import org.intocps.maestro.ast.node.PStm;
import org.intocps.maestro.core.Framework;
import org.intocps.maestro.core.messages.IErrorReporter;
import org.intocps.maestro.framework.core.ISimulationEnvironment;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment;
import org.intocps.maestro.framework.fmi2.RelationVariable;
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder;
import org.intocps.maestro.framework.fmi2.api.mabl.*;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.DynamicActiveBuilderScope;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.ScopeFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.values.IntExpressionValue;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.*;
import org.intocps.orchestration.coe.modeldefinition.ModelDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.intocps.maestro.ast.MableAstFactory.*;

@SimulationFramework(framework = Framework.FMI2)
public class FixedStepBuilder implements IMaestroExpansionPlugin {

    final static Logger logger = LoggerFactory.getLogger(FixedStepBuilder.class);

    final AFunctionDeclaration fun = newAFunctionDeclaration(newAIdentifier("fixedStep"),
            Arrays.asList(newAFormalParameter(newAArrayType(newANameType("FMI2Component")), newAIdentifier("component")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("stepSize")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("startTime")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("endTime"))), newAVoidType());


    public Set<AFunctionDeclaration> getDeclaredUnfoldFunctions() {
        return Stream.of(fun).collect(Collectors.toSet());
    }


    @Override
    public List<PStm> expand(AFunctionDeclaration declaredFunction, List<PExp> formalArguments, IPluginConfiguration config,
            ISimulationEnvironment envIn, IErrorReporter errorReporter) throws ExpandException {

        logger.info("Unfolding with fixed step: {}", declaredFunction.toString());
        FixedstepConfig fixedstepConfig = (FixedstepConfig) config;

        if (!getDeclaredUnfoldFunctions().contains(declaredFunction)) {
            throw new ExpandException("Unknown function declaration");
        }
        AFunctionDeclaration selectedFun = fun;

        if (formalArguments == null || formalArguments.size() != selectedFun.getFormals().size()) {
            throw new ExpandException("Invalid args");
        }

        if (envIn == null) {
            throw new ExpandException("Simulation environment must not be null");
        }

        Fmi2SimulationEnvironment env = (Fmi2SimulationEnvironment) envIn;

        PExp stepSize = formalArguments.get(1).clone();
        PExp startTime = formalArguments.get(2).clone();
        PExp endTime = formalArguments.get(3).clone();
        if (declaredFunction.equals(fun)) {
            try {
                MablApiBuilder.MablSettings settings = new MablApiBuilder.MablSettings();
                settings.externalRuntimeLogger = true;
                // Selected fun now matches funWithBuilder
                MablApiBuilder builder = new MablApiBuilder(settings, true);

                DynamicActiveBuilderScope dynamicScope = builder.getDynamicScope();
                MathBuilderFmi2Api math = builder.getMablToMablAPI().getMathBuilder();
                BooleanBuilderFmi2Api booleanLogic = builder.getMablToMablAPI().getBooleanBuilder();

                // Convert raw MaBL to API
                DoubleVariableFmi2Api externalStepSize = builder.getDoubleVariableFrom(stepSize);
                DoubleVariableFmi2Api stepSizeVar = dynamicScope.store("fixed_step_size", 0.0);
                stepSizeVar.setValue(externalStepSize);
                DoubleVariableFmi2Api externalStartTime = new DoubleVariableFmi2Api(null, null, null, null, startTime);
                DoubleVariableFmi2Api currentCommunicationTime = (DoubleVariableFmi2Api) dynamicScope.store("fixed_current_communication_point", 0.0);
                currentCommunicationTime.setValue(externalStartTime);
                DoubleVariableFmi2Api externalEndTime = new DoubleVariableFmi2Api(null, null, null, null, endTime);
                DoubleVariableFmi2Api endTimeVar = (DoubleVariableFmi2Api) dynamicScope.store("fixed_end_time", 0.0);
                endTimeVar.setValue(externalEndTime);

                // Import the external components into Fmi2API
                Map<String, ComponentVariableFmi2Api> fmuInstances =
                        FromMaBLToMaBLAPI.GetComponentVariablesFrom(builder, formalArguments.get(0), env);

                // Create bindings
                FromMaBLToMaBLAPI.CreateBindings(fmuInstances, env);

                // Create the logging
                DataWriter dataWriter = builder.getMablToMablAPI().getDataWriter();
                DataWriter.DataWriterInstance dataWriterInstance = dataWriter.createDataWriterInstance();
                dataWriterInstance
                        .initialize(fmuInstances.values().stream().flatMap(x -> x.getVariablesToLog().stream()).collect(Collectors.toList()));


                // Create the iteration predicate
                PredicateFmi2Api loopPredicate = currentCommunicationTime.toMath().addition(stepSizeVar).lessThan(endTimeVar);
                DoubleVariableFmi2Api absTol = dynamicScope.store("absolute_tolerance", 1.0);
                DoubleVariableFmi2Api relTol = dynamicScope.store("relative_tolerance", 1.0);

                // Store the state for all
                List<Fmi2Builder.StateVariable<PStm>> fmuStates = fmuInstances.values().stream().map(x -> x.getState()).collect(Collectors.toList());

                // Get and share all variables related to outputs or logging.
                fmuInstances.forEach((x, y) -> {
                    List<RelationVariable> variablesToLog = env.getVariablesToLog(x);
                    y.share(y.get(variablesToLog.stream().map(var -> var.scalarVariable.getName()).toArray(String[]::new)));
                });

                IntVariableFmi2Api stabilisation_loop_max_iterations = dynamicScope.store("stabilisation_loop_max_iterations", 5);

                // SET ALL LINKED VARIABLES


                ScopeFmi2Api scopeFmi2Api = dynamicScope.enterWhile(loopPredicate);
                {
                    ScopeFmi2Api stabilisationScope = null;
                    IntVariableFmi2Api stabilisation_loop = null;
                    BooleanVariableFmi2Api convergenceReached = null;
                    if (fixedstepConfig.stabilisation) {
                        stabilisation_loop = dynamicScope.store("stabilisation_loop", stabilisation_loop_max_iterations);
                        convergenceReached = dynamicScope.store("hasConverged", false);
                        stabilisationScope = dynamicScope.enterWhile(
                                convergenceReached.toPredicate().not().and(stabilisation_loop.toMath().greaterThan(IntExpressionValue.of(0))));


                    }

                    // SET ALL LINKED VARIABLES
                    // This has to be carried out regardless of stabilisation or not.
                    fmuInstances.forEach((x, y) -> y.setLinked());

                    // STEP ALL
                    fmuInstances.forEach((x, y) -> {
                        Map.Entry<Fmi2Builder.BoolVariable<PStm>, Fmi2Builder.DoubleVariable<PStm>> a = y.step(currentCommunicationTime, stepSizeVar);
                    });

                    // GET ALL LINKED OUTPUTS INCLUDING LOGGING OUTPUTS
                    Map<ComponentVariableFmi2Api, Map<PortFmi2Api, VariableFmi2Api<Object>>> retrievedValues =
                            fmuInstances.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> {
                                List<RelationVariable> variablesToLog = env.getVariablesToLog(entry.getKey());
                                String[] variablesToGet = variablesToLog.stream().map(var -> var.scalarVariable.getName()).toArray(String[]::new);
                                return entry.getValue().get(variablesToGet);
                            }));

                    // CONVERGENCE
                    if (fixedstepConfig.stabilisation) {
                        // For each instance ->
                        //      For each retrieved variable
                        //          compare with previous in terms of convergence
                        //  If all converge, set retrieved values and continue
                        //  else reset to previous state, set retrieved values and continue
                        List<BooleanVariableFmi2Api> convergenceVariables = retrievedValues.entrySet().stream().flatMap(comptoPortAndVariable -> {
                            Stream<BooleanVariableFmi2Api> converged = comptoPortAndVariable.getValue().entrySet().stream()
                                    .filter(x -> x.getKey().scalarVariable.type.type == ModelDescription.Types.Real).map(portAndVariable -> {
                                        VariableFmi2Api oldVariable = portAndVariable.getKey().getSharedAsVariable();
                                        VariableFmi2Api<Object> newVariable = portAndVariable.getValue();
                                        return math.checkConvergence(oldVariable, newVariable, absTol, relTol);
                                    });
                            return converged;
                        }).collect(Collectors.toList());
                        convergenceReached.setValue(booleanLogic.allTrue("convergence", convergenceVariables));
                        ScopeFmi2Api ifScope = dynamicScope.enterIf(convergenceReached.toPredicate().not()).enterThen();
                        {
                            fmuStates.forEach(x -> x.set());
                            if (stabilisation_loop != null) {
                                stabilisation_loop.decrement();
                            } else {
                                throw new RuntimeException("NO STABILISATION LOOP FOUND");
                            }
                        }
                        stabilisationScope.activate();
                        retrievedValues.forEach((k, v) -> k.share(v));
                    }
                    scopeFmi2Api.activate();
                    if (!fixedstepConfig.stabilisation) {
                        retrievedValues.forEach((k, v) -> k.share(v));
                    }


                    // Update currentCommunicationTime
                    currentCommunicationTime.setValue(currentCommunicationTime.toMath().addition(stepSizeVar));

                    // Call log
                    dataWriterInstance.log(currentCommunicationTime);
                }

                ABlockStm algorithm = (ABlockStm) builder.buildRaw();

                algorithm.apply(new ToParExp());
                System.out.println(PrettyPrinter.print(algorithm));

                return algorithm.getBody();
            } catch (Exception e) {
                throw new ExpandException("Internal error: ", e);
            }
        } else {
            throw new ExpandException("Unknown function");
        }
    }


    @Override
    public boolean requireConfig() {
        return true;
    }

    @Override
    public IPluginConfiguration parseConfig(InputStream is) throws IOException {
        return (new ObjectMapper().readValue(is, FixedstepConfig.class));
    }

    @Override
    public AImportedModuleCompilationUnit getDeclaredImportUnit() {
        AImportedModuleCompilationUnit unit = new AImportedModuleCompilationUnit();
        unit.setImports(Stream.of("FMI2", "TypeConverter", "Math", "Logger", "DataWriter", "ArrayUtil").map(MableAstFactory::newAIdentifier)
                .collect(Collectors.toList()));
        AModuleDeclaration module = new AModuleDeclaration();
        module.setName(newAIdentifier(getName()));
        module.setFunctions(new ArrayList<>(getDeclaredUnfoldFunctions()));
        unit.setModule(module);
        return unit;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String getVersion() {
        return "0.0.1";
    }


}
