package org.intocps.maestro.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.intocps.maestro.ast.LexIdentifier;
import org.intocps.maestro.ast.MableAstFactory;
import org.intocps.maestro.ast.node.*;
import org.intocps.maestro.core.api.FixedStepSizeAlgorithm;
import org.intocps.maestro.core.api.IStepAlgorithm;
import org.intocps.maestro.framework.fmi2.ComponentInfo;
import org.intocps.maestro.framework.fmi2.FaultInject;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment;
import org.intocps.maestro.framework.fmi2.api.mabl.MablApiBuilder;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.DoubleVariableFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.FmuVariableFmi2Api;
import org.intocps.maestro.plugin.IMaestroPlugin;
import org.intocps.orchestration.coe.modeldefinition.ModelDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.xpath.XPathExpressionException;
import java.net.URI;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.intocps.maestro.ast.MableAstFactory.*;
import static org.intocps.maestro.ast.MableBuilder.call;
import static org.intocps.maestro.ast.MableBuilder.newVariable;

public class MaBLTemplateGeneratorV2 {
    public static final String START_TIME_NAME = "START_TIME";
    public static final String END_TIME_NAME = "END_TIME";
    public static final String STEP_SIZE_NAME = "STEP_SIZE";
    public static final String MATH_MODULE_NAME = "Math";
    public static final String BOOLEANLOGIC_MODULE_NAME = "BooleanLogic";
    public static final String LOGGER_MODULE_NAME = "Logger";
    public static final String DATAWRITER_MODULE_NAME = "DataWriter";
    public static final String FMI2_MODULE_NAME = "FMI2";
    public static final String TYPECONVERTER_MODULE_NAME = "TypeConverter";
    public static final String INITIALIZE_EXPANSION_FUNCTION_NAME = "initialize";
    public static final String INITIALIZE_EXPANSION_MODULE_NAME = "Initializer";
    public static final String FIXEDSTEP_EXPANSION_FUNCTION_NAME = "fixedStep";
    public static final String FIXEDSTEP_EXPANSION_MODULE_NAME = "FixedStep";
    public static final String ARRAYUTIL_EXPANSION_MODULE_NAME = "ArrayUtil";
    public static final String DEBUG_LOGGING_EXPANSION_FUNCTION_NAME = "enableDebugLogging";
    public static final String DEBUG_LOGGING_MODULE_NAME = "DebugLogging";
    public static final String FMI2COMPONENT_TYPE = "FMI2Component";
    public static final String COMPONENTS_ARRAY_NAME = "components";
    public static final String GLOBAL_EXECUTION_CONTINUE = IMaestroPlugin.GLOBAL_EXECUTION_CONTINUE;
    public static final String STATUS = IMaestroPlugin.FMI_STATUS_VARIABLE_NAME;
    public static final String LOGLEVELS_POSTFIX = "_log_levels";
    public static final String FAULT_INJECT_MODULE_NAME = "FaultInject";
    public static final String FAULT_INJECT_MODULE_VARIABLE_NAME = "faultInject";
    final static Logger logger = LoggerFactory.getLogger(MaBLTemplateGeneratorV2.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ALocalVariableStm createRealVariable(String lexName, Double initializerValue) {
        return MableAstFactory.newALocalVariableStm(MableAstFactory
                .newAVariableDeclaration(new LexIdentifier(lexName, null), MableAstFactory.newARealNumericPrimitiveType(),
                        MableAstFactory.newAExpInitializer(MableAstFactory.newARealLiteralExp(initializerValue))));
    }

    public static String removeFmuKeyBraces(String fmuKey) {
        return fmuKey.substring(1, fmuKey.length() - 1);
    }


    public static String findInstanceLexName(String preferredName, Collection<String> invalidNames) {
        // Remove dots
        String preferredNameNoDots = preferredName.replace('.', '_');
        String proposedName = preferredNameNoDots;
        int addition = 1;
        while (invalidNames.contains(proposedName)) {
            proposedName = preferredNameNoDots + "_" + addition;
            addition++;
        }
        return proposedName;
    }

    public static PStm createFMULoad(String fmuLexName, Map.Entry<String, ModelDescription> entry,
            URI uriFromFMUName) throws XPathExpressionException {

        String path = uriFromFMUName.toString();
        if (uriFromFMUName.getScheme() != null && uriFromFMUName.getScheme().equals("file")) {
            path = uriFromFMUName.getPath();
        }
        return newVariable(fmuLexName, newANameType("FMI2"),
                call("load", newAStringLiteralExp("FMI2"), newAStringLiteralExp(entry.getValue().getGuid()), newAStringLiteralExp(path)));

    }

    public static PStm createFMUUnload(String fmuLexName) {
        return MableAstFactory.newExpressionStm(
                MableAstFactory.newACallExp(MableAstFactory.newAIdentifier("unload"), Arrays.asList(MableAstFactory.newAIdentifierExp(fmuLexName))));
    }

    public static List<PStm> createFMUInstantiateStatement(String instanceLexName, String instanceEnvironmentKey, String fmuLexName, boolean visible,
            boolean loggingOn) {
        return createFMUInstantiateStatement(instanceLexName, instanceEnvironmentKey, fmuLexName, visible, loggingOn, Optional.empty());
    }

    public static List<PStm> createFMUInstantiateStatement(String instanceLexName, String instanceEnvironmentKey, String fmuLexName, boolean visible,
            boolean loggingOn, Optional<FaultInject> faultInject) {
        List<PStm> statements = new ArrayList<>();
        String instanceLexName_ = instanceLexName;
        if (faultInject.isPresent()) {

            instanceLexName_ = instanceLexName + "_original";
        }

        AInstanceMappingStm mapping = newAInstanceMappingStm(newAIdentifier(instanceLexName_), instanceEnvironmentKey);
        statements.add(mapping);
        PStm var = newVariable(instanceLexName_, newANameType("FMI2Component"), newNullExp());
        statements.add(var);
        AIfStm ifAssign = newIf(newAIdentifierExp(GLOBAL_EXECUTION_CONTINUE), newABlockStm(
                newAAssignmentStm(newAIdentifierStateDesignator(newAIdentifier(instanceLexName_)),
                        call(fmuLexName, "instantiate", newAStringLiteralExp(instanceEnvironmentKey), newABoolLiteralExp(visible),
                                newABoolLiteralExp(loggingOn))), checkNullAndStop(instanceLexName_)), null);
        statements.add(ifAssign);

        if (faultInject.isPresent()) {
            String faultInjectLexName = instanceLexName;
            PStm ficomp = newVariable(faultInjectLexName, newANameType("FMI2Component"), newNullExp());
            statements.add(ficomp);
            AIfStm stm = newIf(newAIdentifierExp(GLOBAL_EXECUTION_CONTINUE), newABlockStm(
                    newAAssignmentStm(newAIdentifierStateDesignator(newAIdentifier(faultInjectLexName)),
                            newACallExp(newAIdentifierExp(FAULT_INJECT_MODULE_VARIABLE_NAME), newAIdentifier("faultInject"),
                                    Arrays.asList(newAIdentifierExp(fmuLexName), newAIdentifierExp(instanceLexName_),
                                            newAStringLiteralExp(faultInject.get().constraintId)))), checkNullAndStop(faultInjectLexName)), null);
            statements.add(stm);
        }


        return statements;
    }

    public static ExpandStatements generateAlgorithmStms(IStepAlgorithm algorithm) {
        switch (algorithm.getType()) {
            case FIXEDSTEP:
                FixedStepSizeAlgorithm a = (FixedStepSizeAlgorithm) algorithm;
                return new ExpandStatements(
                        Arrays.asList(createRealVariable(STEP_SIZE_NAME, a.stepSize), createRealVariable(END_TIME_NAME, a.endTime)),
                        Arrays.asList(createExpandFixedStep(COMPONENTS_ARRAY_NAME, STEP_SIZE_NAME, START_TIME_NAME, END_TIME_NAME)));
            default:
                throw new IllegalArgumentException("Algorithm type is unknown.");
        }
    }

    public static ASimulationSpecificationCompilationUnit generateTemplate(MaBLTemplateConfiguration templateConfiguration) throws Exception {

        Fmi2SimulationEnvironment unitRelationShip = templateConfiguration.getUnitRelationship();

        MablApiBuilder builder = new MablApiBuilder();
        Map<String, FmuVariableFmi2Api> fmuNameToFmuObject = new HashMap<>();
        // Load the FMUs and create relevant instances
        for (Map.Entry<String, ModelDescription> x : templateConfiguration.getSimulationEnvironment().getFmusWithModelDescriptions()) {
            String name = removeFmuKeyBraces(x.getKey());
            fmuNameToFmuObject
                    .put(x.getKey(), builder.getDynamicScope().createFMU(name, x.getValue(), unitRelationShip.getUriFromFMUName(x.getKey())));
        }

        // Create the instances and set debug logging
        Map<String, ComponentVariableFmi2Api> instanceNameToInstanceObject = new HashMap<>();
        for (Map.Entry<String, ComponentInfo> instance : unitRelationShip.getInstances()) {
            ComponentVariableFmi2Api instance_ = fmuNameToFmuObject.get(instance.getValue().fmuIdentifier).instantiate(instance.getKey());
            instanceNameToInstanceObject.put(instance.getKey(), instance_);
            if (templateConfiguration.getLoggingOn()) {
                Map<String, List<String>> loglevels = templateConfiguration.getLogLevels();
                if (loglevels != null) {
                    instance_.setDebugLogging(loglevels.get(instance.getValue().fmuIdentifier + "." + instance.getKey()));
                }
            }
        }

        FixedStepSizeAlgorithm algorithm = (FixedStepSizeAlgorithm) templateConfiguration.getAlgorithm();

        DoubleVariableFmi2Api startTime = builder.getDynamicScope().store(START_TIME_NAME, 0.0);
        DoubleVariableFmi2Api endTime = builder.getDynamicScope().store(END_TIME_NAME, algorithm.endTime);

        PStm componentsArray = createComponentsArray(COMPONENTS_ARRAY_NAME, instanceNameToInstanceObject.keySet());
        // Components Array
        builder.getDynamicScope().add(componentsArray);
        // Create the initialize expand
        if (templateConfiguration.getInitialize().getKey()) {
            builder.getDynamicScope().add(new AConfigStm(StringEscapeUtils.escapeJava(templateConfiguration.getInitialize().getValue())));
            builder.getDynamicScope().add(createExpandInitialize(COMPONENTS_ARRAY_NAME, START_TIME_NAME, END_TIME_NAME));
        }

        return builder.build();


/*
        // This variable determines whether an expansion should be wrapped in globalExecutionContinue or not.
        boolean wrapExpansionPluginInGlobalExecutionContinue = false;

        StatementMaintainer stmMaintainer = new StatementMaintainer();
        stmMaintainer.add(createGlobalExecutionContinue());
        stmMaintainer.addAll(createStatusVariables());

        stmMaintainer.addAll(generateLoadUnloadStms(MaBLTemplateGeneratorV2::createLoadStatement));

        //        Fmi2SimulationEnvironment unitRelationShip = templateConfiguration.getUnitRelationship();
        boolean faultInject =
                unitRelationShip.getInstances().stream().anyMatch(x -> x.getValue() != null && x.getValue().getFaultInject().isPresent());
        if (faultInject) {
            stmMaintainer.add(createLoadStatement(FAULT_INJECT_MODULE_NAME,
                    Arrays.asList(newAStringLiteralExp(unitRelationShip.getFaultInjectionConfigurationPath()))));
        }

        // Create FMU load statements
        List<PStm> unloadFmuStatements = new ArrayList<>();
        HashMap<String, String> fmuNameToLexIdentifier = new HashMap<>();
        for (Map.Entry<String, ModelDescription> entry : unitRelationShip.getFmusWithModelDescriptions()) {
            String fmuLexName = removeFmuKeyBraces(entry.getKey());

            stmMaintainer.add(createFMULoad(fmuLexName, entry, unitRelationShip.getUriFromFMUName(entry.getKey())));
            stmMaintainer.add(checkNullAndStop(fmuLexName));

            unloadFmuStatements.add(createFMUUnload(fmuLexName));

            fmuNameToLexIdentifier.put(entry.getKey(), fmuLexName);
        }

        // Create Instantiate Statements
        HashMap<String, String> instanceLexToInstanceName = new HashMap<>();
        Set<String> invalidNames = new HashSet<>(fmuNameToLexIdentifier.values());
        List<PStm> freeInstanceStatements = new ArrayList<>();
        Map<String, String> instaceNameToInstanceLex = new HashMap<>();
        unitRelationShip.getInstances().forEach(entry -> {
            // Find parent lex
            String parentLex = fmuNameToLexIdentifier.get(entry.getValue().fmuIdentifier);
            // Get instanceName
            String instanceLexName = findInstanceLexName(entry.getKey(), invalidNames);
            invalidNames.add(instanceLexName);
            instanceLexToInstanceName.put(instanceLexName, entry.getKey());
            instaceNameToInstanceLex.put(entry.getKey(), instanceLexName);

            stmMaintainer.addAll(createFMUInstantiateStatement(instanceLexName, entry.getKey(), parentLex, templateConfiguration.getVisible(),
                    templateConfiguration.getLoggingOn(), entry.getValue().getFaultInject()));
            freeInstanceStatements.add(createFMUFreeInstanceStatement(instanceLexName, parentLex, entry.getValue().getFaultInject()));
        });


        // Debug logging
        if (templateConfiguration.getLoggingOn()) {
            //            if (templateConfiguration.getLogLevels() != null) {
            stmMaintainer.addAll(createDebugLoggingStms(instaceNameToInstanceLex, templateConfiguration.getLogLevels()));
            stmMaintainer.wrapInIfBlock();
            //            }
        }


        // Components Array
        stmMaintainer.add(createComponentsArray(COMPONENTS_ARRAY_NAME, instanceLexToInstanceName.keySet()));

        stmMaintainer.add(createRealVariable(START_TIME_NAME, 0.0));

        // Generate variable statements related to the given algorithm. I.e. the variable step size for fixed step.
        ExpandStatements algorithmStatements = null;
        if (templateConfiguration.getAlgorithm() != null) {
            algorithmStatements = generateAlgorithmStms(templateConfiguration.getAlgorithm());
            if (algorithmStatements.variablesToTopOfMabl != null) {
                stmMaintainer.addAll(algorithmStatements.variablesToTopOfMabl);
            }
        }

        // Add the initializer expand stm
        if (templateConfiguration.getInitialize().getKey()) {
            stmMaintainer.add(new AConfigStm(StringEscapeUtils.escapeJava(templateConfiguration.getInitialize().getValue())));
            stmMaintainer.add(createExpandInitialize(COMPONENTS_ARRAY_NAME, START_TIME_NAME, END_TIME_NAME));
        }

        // Add the algorithm expand stm
        if (algorithmStatements.body != null) {
            stmMaintainer.addAll(algorithmStatements.body);
        }

        // Free instances
        stmMaintainer.addAllCleanup(freeInstanceStatements);

        // Unload the FMUs
        stmMaintainer.addAllCleanup(unloadFmuStatements);
        stmMaintainer.addAllCleanup(generateLoadUnloadStms(x -> createUnloadStatement(StringUtils.uncapitalize(x))));
        if (faultInject) {
            stmMaintainer.addAllCleanup(Arrays.asList(createUnloadStatement(FAULT_INJECT_MODULE_VARIABLE_NAME)));
        }
        // Create the toplevel
        List<LexIdentifier> imports = new ArrayList<>(
                Arrays.asList(newAIdentifier(FIXEDSTEP_EXPANSION_MODULE_NAME), newAIdentifier(INITIALIZE_EXPANSION_MODULE_NAME),
                        newAIdentifier(DEBUG_LOGGING_MODULE_NAME), newAIdentifier(TYPECONVERTER_MODULE_NAME), newAIdentifier(DATAWRITER_MODULE_NAME),
                        newAIdentifier(FMI2_MODULE_NAME), newAIdentifier(MATH_MODULE_NAME), newAIdentifier(ARRAYUTIL_EXPANSION_MODULE_NAME),
                        newAIdentifier(LOGGER_MODULE_NAME), newAIdentifier(BOOLEANLOGIC_MODULE_NAME)));
        if (faultInject) {
            imports.add(newAIdentifier(FAULT_INJECT_MODULE_NAME));
        }

        ASimulationSpecificationCompilationUnit unit =
                newASimulationSpecificationCompilationUnit(imports, newABlockStm(stmMaintainer.getStatements()));
        unit.setFramework(Collections.singletonList(new LexIdentifier(templateConfiguration.getFramework().name(), null)));

        unit.setFrameworkConfigs(Arrays.asList(
                new AConfigFramework(new LexIdentifier(templateConfiguration.getFrameworkConfig().getKey().name(), null),
                        StringEscapeUtils.escapeJava(objectMapper.writeValueAsString(templateConfiguration.getFrameworkConfig().getValue())))));
        return unit;*/
    }

    private static Collection<? extends PStm> createStatusVariables() {
        List<PStm> list = new ArrayList<>();
        BiFunction<String, Integer, PStm> createStatusVariable_ = (name, value) -> newALocalVariableStm(
                newAVariableDeclaration(newLexIdentifier(name), newAIntNumericPrimitiveType(), newAExpInitializer(newAIntLiteralExp(value))));
        list.add(createStatusVariable_.apply("FMI_STATUS_OK", 0));
        list.add(createStatusVariable_.apply("FMI_STATUS_WARNING", 1));
        list.add(createStatusVariable_.apply("FMI_STATUS_DISCARD", 2));
        list.add(createStatusVariable_.apply("FMI_STATUS_ERROR", 3));
        list.add(createStatusVariable_.apply("FMI_STATUS_FATAL", 4));
        list.add(createStatusVariable_.apply("FMI_STATUS_PENDING", 5));
        list.add(MableAstFactory.newALocalVariableStm(MableAstFactory
                .newAVariableDeclaration(MableAstFactory.newAIdentifier(STATUS), MableAstFactory.newAIntNumericPrimitiveType(),
                        MableAstFactory.newAExpInitializer(MableAstFactory.newAIntLiteralExp(0)))));
        return list;
    }


    private static PStm checkNullAndStop(String identifier) {
        return newIf(newEqual(newAIdentifierExp(identifier), newNullExp()),
                newAAssignmentStm(newAIdentifierStateDesignator(newAIdentifier(GLOBAL_EXECUTION_CONTINUE)), newABoolLiteralExp(false)), null);
    }

    private static Collection<? extends PStm> createDebugLoggingStmsHelper(Map<String, String> instaceNameToInstanceLex, String instanceName,
            List<String> logLevels) {
        String instanceLexName = instaceNameToInstanceLex.get(instanceName);
        if (instanceLexName != null) {
            return createExpandDebugLogging(instanceLexName, logLevels);
        } else {
            logger.warn("Could not set log levels for " + instanceName);
            return Arrays.asList();
        }

    }

    private static Collection<? extends PStm> createDebugLoggingStms(Map<String, String> instaceNameToInstanceLex,
            Map<String, List<String>> logLevels) {
        List<PStm> stms = new ArrayList<>();

        // If no logLevels have defined, then call setDebugLogging for all instances
        if (logLevels == null) {
            for (Map.Entry<String, String> entry : instaceNameToInstanceLex.entrySet()) {
                stms.addAll(createDebugLoggingStmsHelper(instaceNameToInstanceLex, entry.getKey(), new ArrayList<>()));
            }
        } else {
            // If loglevels have been defined for some instances, then only call setDebugLogging for those instances.
            for (Map.Entry<String, List<String>> entry : logLevels.entrySet()) {
                // If the instance is available as key in loglevels but has an empty value, then call setDebugLogging with empty loglevels.
                if (entry.getValue().isEmpty()) {
                    stms.addAll(createDebugLoggingStmsHelper(instaceNameToInstanceLex, entry.getKey(), new ArrayList<>()));
                    continue;
                }
                // If the instance is available as key in loglevels and has nonempty value, then call setDebugLogging with the relevant values.
                stms.addAll(createDebugLoggingStmsHelper(instaceNameToInstanceLex, entry.getKey(), entry.getValue()));
            }
        }

        return stms;
    }

    private static List<PStm> createExpandDebugLogging(String instanceLexName, List<String> logLevels) {
        AArrayInitializer loglevelsArrayInitializer = null;
        String arrayName = instanceLexName + LOGLEVELS_POSTFIX;
        if (!logLevels.isEmpty()) {
            loglevelsArrayInitializer =
                    newAArrayInitializer(logLevels.stream().map(MableAstFactory::newAStringLiteralExp).collect(Collectors.toList()));
        }
        ALocalVariableStm arrayContent = MableAstFactory.newALocalVariableStm(MableAstFactory
                .newAVariableDeclaration(MableAstFactory.newAIdentifier(arrayName),
                        MableAstFactory.newAArrayType(MableAstFactory.newAStringPrimitiveType()), logLevels.size(), loglevelsArrayInitializer));

        AExpressionStm expandCall = MableAstFactory.newExpressionStm(MableAstFactory
                .newACallExp(newExpandToken(), newAIdentifierExp(MableAstFactory.newAIdentifier(DEBUG_LOGGING_MODULE_NAME)),
                        MableAstFactory.newAIdentifier(DEBUG_LOGGING_EXPANSION_FUNCTION_NAME),
                        Arrays.asList(MableAstFactory.newAIdentifierExp(instanceLexName), MableAstFactory.newAIdentifierExp(arrayName),
                                MableAstFactory.newAUIntLiteralExp(Long.valueOf(logLevels.size())))));

        return Arrays.asList(arrayContent, expandCall);

    }

    private static PStm createGlobalExecutionContinue() {
        return MableAstFactory.newALocalVariableStm(MableAstFactory
                .newAVariableDeclaration(MableAstFactory.newAIdentifier(GLOBAL_EXECUTION_CONTINUE), MableAstFactory.newABoleanPrimitiveType(),
                        MableAstFactory.newAExpInitializer(MableAstFactory.newABoolLiteralExp(true))));
    }

    private static PStm createFMUFreeInstanceStatement(String instanceLexName, String fmuLexName) {
        return MableAstFactory.newExpressionStm(MableAstFactory
                .newACallExp(MableAstFactory.newAIdentifierExp(fmuLexName), MableAstFactory.newAIdentifier("freeInstance"),
                        Arrays.asList(MableAstFactory.newAIdentifierExp(instanceLexName))));
    }

    private static PStm createFMUFreeInstanceStatement(String instanceLexName, String fmuLexName, Optional<FaultInject> faultInject) {
        if (faultInject.isPresent()) {
            instanceLexName = instanceLexName + "_original";
        }
        return MableAstFactory.newExpressionStm(MableAstFactory
                .newACallExp(MableAstFactory.newAIdentifierExp(fmuLexName), MableAstFactory.newAIdentifier("freeInstance"),
                        Arrays.asList(MableAstFactory.newAIdentifierExp(instanceLexName))));
    }

    private static Collection<? extends PStm> generateUnloadStms() {
        return null;
    }

    private static PStm createComponentsArray(String lexName, Set<String> keySet) {
        return MableAstFactory.newALocalVariableStm(MableAstFactory.newAVariableDeclaration(MableAstFactory.newAIdentifier(lexName),
                MableAstFactory.newAArrayType(MableAstFactory.newANameType(FMI2COMPONENT_TYPE)), keySet.size(),
                MableAstFactory.newAArrayInitializer(keySet.stream().map(x -> AIdentifierExpFromString(x)).collect(Collectors.toList()))));
    }

    private static PStm createUnloadStatement(String moduleName) {
        return MableAstFactory.newExpressionStm(MableAstFactory.newUnloadExp(Arrays.asList(MableAstFactory.newAIdentifierExp(moduleName))));
    }

    private static Collection<? extends PStm> generateLoadUnloadStms(Function<String, PStm> function) {
        return Arrays.asList(MATH_MODULE_NAME, LOGGER_MODULE_NAME, DATAWRITER_MODULE_NAME, BOOLEANLOGIC_MODULE_NAME).stream()
                .map(x -> function.apply(x)).collect(Collectors.toList());
    }

    private static PStm createLoadStatement(String moduleName, List<PExp> pexp) {
        List<PExp> arguments = new ArrayList<>();
        arguments.add(MableAstFactory.newAStringLiteralExp(moduleName));
        if (pexp != null && pexp.size() > 0) {
            arguments.addAll(pexp);
        }
        return MableAstFactory.newALocalVariableStm(MableAstFactory
                .newAVariableDeclaration(MableAstFactory.newAIdentifier(StringUtils.uncapitalize(moduleName)),
                        MableAstFactory.newANameType(moduleName), MableAstFactory.newAExpInitializer(MableAstFactory.newALoadExp(arguments))));
    }

    private static PStm createLoadStatement(String moduleName) {
        return createLoadStatement(moduleName, null);
    }

    public static PStm createExpandInitialize(String componentsArrayLexName, String startTimeLexName, String endTimeLexName) {
        return MableAstFactory.newExpressionStm(MableAstFactory
                .newACallExp(newExpandToken(), newAIdentifierExp(MableAstFactory.newAIdentifier(INITIALIZE_EXPANSION_MODULE_NAME)),
                        MableAstFactory.newAIdentifier(INITIALIZE_EXPANSION_FUNCTION_NAME),
                        Arrays.asList(AIdentifierExpFromString(componentsArrayLexName), AIdentifierExpFromString(startTimeLexName),
                                AIdentifierExpFromString(endTimeLexName))));
    }

    public static PStm createExpandFixedStep(String componentsArrayLexName, String stepSizeLexName, String startTimeLexName, String endTimeLexName) {
        return MableAstFactory.newExpressionStm(MableAstFactory
                .newACallExp(newExpandToken(), newAIdentifierExp(MableAstFactory.newAIdentifier(FIXEDSTEP_EXPANSION_MODULE_NAME)),
                        MableAstFactory.newAIdentifier(FIXEDSTEP_EXPANSION_FUNCTION_NAME),
                        Arrays.asList(AIdentifierExpFromString(componentsArrayLexName), AIdentifierExpFromString(stepSizeLexName),
                                AIdentifierExpFromString(startTimeLexName), AIdentifierExpFromString(endTimeLexName))));
    }

    public static AIdentifierExp AIdentifierExpFromString(String x) {
        return MableAstFactory.newAIdentifierExp(MableAstFactory.newAIdentifier(x));
    }

    public static class ExpandStatements {
        public List<PStm> variablesToTopOfMabl;
        public List<PStm> body;

        public ExpandStatements(List<PStm> variablesToTopOfMabl, List<PStm> body) {
            this.variablesToTopOfMabl = variablesToTopOfMabl;
            this.body = body;
        }
    }

    public static class StatementMaintainer {
        List<PStm> statements = new ArrayList<>();
        List<PStm> ifBlock;
        List<PStm> cleanup = new ArrayList<>();
        boolean wrapInIfBlock = false;

        public void addCleanup(PStm stm) {
            cleanup.add(stm);
        }

        public void addAllCleanup(Collection<? extends PStm> stms) {
            cleanup.addAll(stms);
        }

        public List<PStm> getStatements() {
            List<PStm> stms = new ArrayList<>();
            stms.addAll(statements);
            if (wrapInIfBlock) {
                stms.add(newIf(newAIdentifierExp(IMaestroPlugin.GLOBAL_EXECUTION_CONTINUE), newABlockStm(ifBlock), null));
            }
            stms.addAll(cleanup);

            return stms;
        }

        public void wrapInIfBlock() {
            this.wrapInIfBlock = true;
            this.ifBlock = new ArrayList<>();
        }

        public void add(PStm stm) {
            if (wrapInIfBlock) {
                this.ifBlock.add(stm);
            } else {
                this.statements.add(stm);
            }
        }

        public void addAll(Collection<? extends PStm> stms) {
            if (wrapInIfBlock) {
                this.ifBlock.addAll(stms);
            } else {
                this.statements.addAll(stms);
            }
        }
    }
}
