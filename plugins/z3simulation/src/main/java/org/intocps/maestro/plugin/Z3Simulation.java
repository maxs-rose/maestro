package org.intocps.maestro.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.intocps.maestro.ast.*;
import org.intocps.maestro.ast.node.*;
import org.intocps.maestro.core.Framework;
import org.intocps.maestro.core.messages.IErrorReporter;
import org.intocps.maestro.framework.core.ISimulationEnvironment;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.intocps.maestro.ast.MableAstFactory.*;

@SimulationFramework(framework = Framework.FMI2)
public class Z3Simulation implements IMaestroExpansionPlugin {

    final static Logger logger = LoggerFactory.getLogger(Z3Simulation.class);

    final AFunctionDeclaration fun = newAFunctionDeclaration(newAIdentifier("simulate"),
            Arrays.asList(newAFormalParameter(newAArrayType(newANameType("FMI2Component")), newAIdentifier("component")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("startTime")),
                    newAFormalParameter(newARealNumericPrimitiveType(), newAIdentifier("endTime"))), newAVoidType());


    public Set<AFunctionDeclaration> getDeclaredUnfoldFunctions() {
        return Stream.of(fun).collect(Collectors.toSet());
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
            throw new ExpandException("No components found cannot simulate 0 components");
        }

        final List<LexIdentifier> componentNames = knownComponentNames;

        Set<Fmi2SimulationEnvironment.Relation> relations =
                env.getRelations(componentNames).stream().filter(r -> r.getOrigin() == Fmi2SimulationEnvironment.Relation.InternalOrExternal.External)
                        .collect(Collectors.toSet());

        PExp stepSize = formalArguments.get(1).clone();
        PExp startTime = formalArguments.get(2).clone();
        PExp endTime = formalArguments.get(3).clone();

        //TODO used relations and config to generate z3 code and produce a meta simulation description
        Object info = new Z3Handler().generateSimulationUsingZ3(relations, (Z3ConstraintInfo) config);

        //TODO use the meta simulation description and convert it to a mabl block

        return Arrays.asList(MableAstFactory.newABlockStm(new Vector<>()));
    }


    @Override
    public boolean requireConfig() {
        return true;
    }

    @Override
    public IPluginConfiguration parseConfig(InputStream is) throws IOException {
        return new ObjectMapper().readValue(is, Z3ConstraintInfo.class);
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


    class Z3ConstraintInfo implements IPluginConfiguration {

        //todo add the fields as public


        public Z3ConstraintInfo() {
        }
    }

}