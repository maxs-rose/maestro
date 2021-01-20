package org.intocps.maestro.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.intocps.maestro.ast.*;
import org.intocps.maestro.ast.node.*;
import org.intocps.maestro.core.Framework;
import org.intocps.maestro.core.messages.IErrorReporter;
import org.intocps.maestro.framework.core.ISimulationEnvironment;
import org.intocps.maestro.framework.fmi2.ComponentInfo;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment;
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder;
import org.intocps.maestro.framework.fmi2.api.mabl.*;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.DynamicActiveBuilderScope;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.IMablScope;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.DoubleVariableFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.FmuVariableFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.VariableFmi2Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.intocps.maestro.ast.MableAstFactory.*;

@SimulationFramework(framework = Framework.FMI2)
public class FixedStep implements IMaestroExpansionPlugin {

    final static Logger logger = LoggerFactory.getLogger(FixedStep.class);

    final AFunctionDeclaration fun = newAFunctionDeclaration(newAIdentifier("fixedStep"),
            Arrays.asList(newAFormalParameter(newAArrayType(newANameType("FMI2Component")), newAIdentifier("component")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("stepSize")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("startTime")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("endTime"))), newAVoidType());
    final AFunctionDeclaration funWithBuilder = newAFunctionDeclaration(newAIdentifier("fixedStepWithBuilder"),
            Arrays.asList(newAFormalParameter(newAArrayType(newANameType("FMI2Component")), newAIdentifier("component")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("stepSize")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("startTime")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("endTime"))), newAVoidType());


    public Set<AFunctionDeclaration> getDeclaredUnfoldFunctions() {
        return Stream.of(fun, funWithBuilder).collect(Collectors.toSet());
    }


    @Override
    public List<PStm> expand(AFunctionDeclaration declaredFunction, List<PExp> formalArguments, IPluginConfiguration config,
            ISimulationEnvironment envIn, IErrorReporter errorReporter) throws ExpandException {

        logger.info("Unfolding with fixed step: {}", declaredFunction.toString());


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

        PStm componentDecl = null;
        String componentsIdentifier = "fix_components";

        List<LexIdentifier> knownComponentNames = null;

        if (formalArguments.get(0) instanceof AIdentifierExp) {
            LexIdentifier name = ((AIdentifierExp) formalArguments.get(0)).getName();
            ABlockStm containingBlock = formalArguments.get(0).getAncestor(ABlockStm.class);

            Optional<AVariableDeclaration> compDecl =
                    containingBlock.getBody().stream().filter(ALocalVariableStm.class::isInstance).map(ALocalVariableStm.class::cast)
                            .map(ALocalVariableStm::getDeclaration)
                            .filter(decl -> decl.getName().equals(name) && !decl.getSize().isEmpty() && decl.getInitializer() != null).findFirst();

            if (!compDecl.isPresent()) {
                throw new ExpandException("Could not find names for comps");
            }

            AArrayInitializer initializer = (AArrayInitializer) compDecl.get().getInitializer();

            //clone for local use
            AVariableDeclaration varDecl = newAVariableDeclaration(newAIdentifier(componentsIdentifier), compDecl.get().getType().clone(),
                    compDecl.get().getSize().get(0).clone(), compDecl.get().getInitializer().clone());
            varDecl.setSize((List<? extends PExp>) compDecl.get().getSize().clone());
            componentDecl = newALocalVariableStm(varDecl);


            knownComponentNames = initializer.getExp().stream().filter(AIdentifierExp.class::isInstance).map(AIdentifierExp.class::cast)
                    .map(AIdentifierExp::getName).collect(Collectors.toList());
        }

        if (knownComponentNames == null || knownComponentNames.isEmpty()) {
            throw new ExpandException("No components found cannot fixed step with 0 components");
        }

        final List<LexIdentifier> componentNames = knownComponentNames;

        Set<Fmi2SimulationEnvironment.Relation> relations =
                env.getRelations(componentNames).stream().filter(r -> r.getOrigin() == Fmi2SimulationEnvironment.Relation.InternalOrExternal.External)
                        .collect(Collectors.toSet());

        PExp stepSize = formalArguments.get(1).clone();
        PExp startTime = formalArguments.get(2).clone();
        PExp endTime = formalArguments.get(3).clone();
        if (declaredFunction.equals(fun)) {
            try {
                return Arrays.asList(newIf(newAIdentifierExp(IMaestroPlugin.GLOBAL_EXECUTION_CONTINUE), newABlockStm(
                        Stream.concat(Stream.of(componentDecl), JacobianFixedStep
                                .generate(env, errorReporter, componentNames, componentsIdentifier, stepSize, startTime, endTime, relations).stream())
                                .collect(Collectors.toList())), null));
            } catch (InstantiationException e) {
                throw new ExpandException("Internal error", e);
            }
        } else {
            try {
                // Selected fun now matches funWithBuilder
                MablApiBuilder builder = new MablApiBuilder();

                DynamicActiveBuilderScope dynamicScope = builder.getDynamicScope();
                MathBuilderFmi2Api math = builder.getMathBuilder();

                Fmi2Builder.BoolVariable<PStm> b = dynamicScope.store(false);
                Fmi2Builder.IntVariable<PStm> c = dynamicScope.store(1);

                // Convert raw MaBL to API
                // TODO: Create a reference value type
                DoubleVariableFmi2Api externalStepSize = builder.getDoubleVariableFrom(stepSize);
                Fmi2Builder.DoubleVariable<PStm> stepSizeVar = dynamicScope.store("fixed_step_size", 0.0);
                stepSizeVar.setValue(externalStepSize);
                // TODO: Create a reference value type
                DoubleVariableFmi2Api externalStartTime = new DoubleVariableFmi2Api(null, null, null, null, startTime);
                Fmi2Builder.DoubleVariable<PStm> currentCommunicationTime = dynamicScope.store("fixed_current_communication_point", 0.0);
                currentCommunicationTime.setValue(externalStartTime);
                // TODO: Create a reference value type
                DoubleVariableFmi2Api externalEndTime = new DoubleVariableFmi2Api(null, null, null, null, endTime);
                Fmi2Builder.DoubleVariable<PStm> endTimeVar = dynamicScope.store("fixed_end_time", 0.0);
                endTimeVar.setValue(externalEndTime);

                HashMap<LexIdentifier, ComponentVariableFmi2Api> fmuInstances = new HashMap<>();

                for (LexIdentifier componentName : componentNames) {
                    ComponentInfo instance = env.getInstanceByLexName(componentName.getText());
                    FmuVariableFmi2Api fmu =
                            new FmuVariableFmi2Api(builder, null, null, null, null, null, null, newAIdentifierExp(instance.fmuIdentifier));

                    ModelDescriptionContext modelDescriptionContext = new ModelDescriptionContext(instance.modelDescription);

                    ComponentVariableFmi2Api a = new ComponentVariableFmi2Api(null, fmu, componentName.getText(), modelDescriptionContext, builder,
                            builder.getDynamicScope(), null, newAIdentifierExp(componentName.getText()));
                    fmuInstances.put(componentName, a);
                }

                // Create bindings
                for (Map.Entry<LexIdentifier, ComponentVariableFmi2Api> entry : fmuInstances.entrySet()) {
                    for (Fmi2SimulationEnvironment.Relation relation : env.getRelations(entry.getKey()).stream()
                            .filter(x -> x.getDirection() == Fmi2SimulationEnvironment.Relation.Direction.OutputToInput &&
                                    x.getOrigin() == Fmi2SimulationEnvironment.Relation.InternalOrExternal.External).collect(Collectors.toList())) {
                        PortFmi2Api[] targets = relation.getTargets().entrySet().stream().map(x -> {
                            ComponentVariableFmi2Api instance = fmuInstances.get(x.getValue().getScalarVariable().instance);
                            return instance.getPort(x.getValue().scalarVariable.scalarVariable.getName());
                        }).toArray(PortFmi2Api[]::new);
                        entry.getValue().getPort(relation.getSource().scalarVariable.scalarVariable.getName()).linkTo(targets);
                    }
                }

                // Create the iteration predicate
                PredicateFmi2Api loopPredicate =
                        LogicBuilderFmi2Api.isLessOrEqualTo(MathBuilderFmi2Api.add(currentCommunicationTime, stepSizeVar), endTimeVar)
                                .and(builder.getGlobalExecutionContinue());

                // Convergence related variables
                Fmi2Builder.DoubleVariable<PStm> absoluteTolerance = dynamicScope.store("absoluteTolerance", 1.0);
                Fmi2Builder.DoubleVariable<PStm> relativeTolerance = dynamicScope.store("relativeTolerance", 1.0);

                IMablScope sc = dynamicScope.getActiveScope();
                Fmi2Builder.WhileScope<PStm> scope = dynamicScope.enterWhile(loopPredicate);
                {
                    // Perform a step for all
                    fmuInstances.forEach((x, y) -> {
                        Map.Entry<Fmi2Builder.BoolVariable<PStm>, Fmi2Builder.DoubleVariable<PStm>> a = y.step(currentCommunicationTime, stepSizeVar);
                    });

                    Fmi2Builder.BoolVariable<PStm> a = builder.getRootScope().store(true);
                    // Perform get Outputs for all
                    fmuInstances.forEach((x, y) -> y.share(y.get()));


                    // Convergence example
                    ComponentVariableFmi2Api m1 =
                            fmuInstances.entrySet().stream().filter(x -> x.getKey().getText().equals("i2")).findFirst().get().getValue();
                    PortFmi2Api fkPort = m1.getPort("fk");
                    VariableFmi2Api fkShared = fkPort.getSharedAsVariable();
                    Map<Fmi2Builder.Port, Fmi2Builder.Variable<PStm, Object>> fkNonShared = m1.get(fkPort);
                    math.checkConvergence(fkNonShared.get(fkPort), fkShared, absoluteTolerance, relativeTolerance);
                    // Perform setLinked for all
                    fmuInstances.forEach((x, y) -> y.setLinked());

                    // Update currentCommunicationTime
                    currentCommunicationTime.setValue(MathBuilderFmi2Api.add(currentCommunicationTime, stepSizeVar));
                }

                ABlockStm algorithm = (ABlockStm) builder.buildRaw();
                algorithm.apply(new ToParExp());

                return algorithm.getBody();
            } catch (Exception e) {
                throw new ExpandException("Internal error: ", e);
            }
        }
    }


    LexIdentifier getStateName(LexIdentifier comp) {
        return newAIdentifier(comp.getText() + "State");
    }

    @Override
    public boolean requireConfig() {
        return false;
    }

    @Override
    public IPluginConfiguration parseConfig(InputStream is) throws IOException {
        return new FixedstepConfig(new ObjectMapper().readValue(is, Integer.class));
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

    enum ExecutionPhase {
        PreSimulationAllocation,
        SimulationLoopStage1,
        SimulationLoopStage2,
        SimulationLoopStage3,
        PostTimeSync,
        PostSimulationDeAllocation
    }


}
