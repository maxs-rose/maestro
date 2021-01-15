package org.intocps.maestro.framework.fmi2.api.mabl.variables;

import org.intocps.maestro.ast.node.*;
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder;
import org.intocps.maestro.framework.fmi2.api.mabl.AMablPort;
import org.intocps.maestro.framework.fmi2.api.mabl.MablApiBuilder;
import org.intocps.maestro.framework.fmi2.api.mabl.ModelDescriptionContext;
import org.intocps.maestro.framework.fmi2.api.mabl.PortIdentifier;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.IMablScope;
import org.intocps.maestro.framework.fmi2.api.mabl.values.PortValueMapImpl;
import org.intocps.orchestration.coe.modeldefinition.ModelDescription;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.intocps.maestro.ast.MableAstFactory.*;
import static org.intocps.maestro.ast.MableBuilder.call;


public class AMablFmi2ComponentVariable extends AMablVariable<Fmi2Builder.NamedVariable<PStm>> implements Fmi2Builder.Fmi2ComponentVariable<PStm> {


    final List<AMablPort> outputPorts;
    final List<AMablPort> inputPorts;
    final List<AMablPort> ports;
    private final AMablFmu2Variable owner;
    private final String name;
    private final MablApiBuilder builder;
    private final Map<PType, ArrayVariable<Object>> ioBuffer = new HashMap<>();
    private final Map<PType, ArrayVariable<Object>> sharedBuffer = new HashMap<>();
    ModelDescriptionContext modelDescriptionContext;
    private ArrayVariable<Object> valueRefBuffer;

    public AMablFmi2ComponentVariable(PStm declaration, AMablFmu2Variable parent, String name, ModelDescriptionContext modelDescriptionContext,
            MablApiBuilder builder, IMablScope declaringScope, PStateDesignator designator, PExp referenceExp) {
        super(declaration, newANameType("FMI2Component"), declaringScope, builder.getDynamicScope(), designator, referenceExp);
        this.owner = parent;
        this.name = name;

        this.modelDescriptionContext = modelDescriptionContext;
        this.builder = builder;

        ports = modelDescriptionContext.nameToSv.values().stream().map(this::createPort)
                .sorted(Comparator.comparing(AMablPort::getPortReferenceValue)).collect(Collectors.toUnmodifiableList());

        outputPorts = ports.stream().filter(p -> p.scalarVariable.causality == ModelDescription.Causality.Output)
                .sorted(Comparator.comparing(AMablPort::getPortReferenceValue)).collect(Collectors.toUnmodifiableList());

        inputPorts = ports.stream().filter(p -> p.scalarVariable.causality == ModelDescription.Causality.Input)
                .sorted(Comparator.comparing(AMablPort::getPortReferenceValue)).collect(Collectors.toUnmodifiableList());
    }

    private ArrayVariable<Object> getValueReferenceBuffer() {
        if (this.valueRefBuffer == null) {
            this.valueRefBuffer = createBuffer(newUIntType(), "VRef", modelDescriptionContext.valRefToSv.size());
        }
        return this.valueRefBuffer;
    }

    private ArrayVariable<Object> getIOBuffer(PType type) {
        if (!this.ioBuffer.containsKey(type)) {
            this.ioBuffer.put(type, createBuffer(type, "IO", modelDescriptionContext.valRefToSv.size()));
        }
        return this.ioBuffer.get(type);
    }

    private ArrayVariable<Object> getSharedBuffer(PType type) {
        if (!this.sharedBuffer.containsKey(type)) {
            this.sharedBuffer.put(type, createBuffer(type, "Share", 0));
        }
        return this.sharedBuffer.get(type);
    }

    private ArrayVariable<Object> createBuffer(PType type, String prefixPostfix, int length) {

        //lets find a good place to store the buffer.
        String ioBufName = builder.getNameGenerator().getName(this.name, type + "", prefixPostfix);
        PInitializer initializer = null;
        if (length > 0) {
            //Bug in the interpreter it relies on values being there
            if (type instanceof ARealNumericPrimitiveType) {
                initializer = newAArrayInitializer(IntStream.range(0, length).mapToObj(i -> newARealLiteralExp(0d)).collect(Collectors.toList()));
            } else if (type instanceof AIntNumericPrimitiveType || type instanceof AUIntNumericPrimitiveType) {
                initializer = newAArrayInitializer(IntStream.range(0, length).mapToObj(i -> newAIntLiteralExp(0)).collect(Collectors.toList()));

            } else if (type instanceof ABooleanPrimitiveType) {
                initializer = newAArrayInitializer(IntStream.range(0, length).mapToObj(i -> newABoolLiteralExp(false)).collect(Collectors.toList()));

            } else if (type instanceof AStringPrimitiveType) {
                initializer = newAArrayInitializer(IntStream.range(0, length).mapToObj(i -> newAStringLiteralExp("")).collect(Collectors.toList()));

            }
        }
        PStm var = newALocalVariableStm(newAVariableDeclaration(newAIdentifier(ioBufName), type, length, initializer));

        getDeclaredScope().addAfter(getDeclaringStm(), var);

        List<AMablVariable<Object>> items = IntStream.range(0, length).mapToObj(
                i -> new AMablVariable<>(var, type, this.getDeclaredScope(), builder.getDynamicScope(),
                        newAArayStateDesignator(newAIdentifierStateDesignator(newAIdentifier(ioBufName)), newAIntLiteralExp(i)),
                        newAArrayIndexExp(newAIdentifierExp(ioBufName), Collections.singletonList(newAIntLiteralExp(i)))))
                .collect(Collectors.toList());

        return new ArrayVariable<>(var, type, getDeclaredScope(), builder.getDynamicScope(), newAIdentifierStateDesignator(newAIdentifier(ioBufName)),
                newAIdentifierExp(ioBufName), items);

    }

    @Override
    public List<? extends Fmi2Builder.Port> getPorts() {
        return ports;
    }

    @Override
    public List<Fmi2Builder.Port> getPorts(String... names) {
        List<String> accept = Arrays.asList(names);
        return ports.stream().filter(p -> accept.contains(p.getName())).collect(Collectors.toList());
    }

    @Override
    public List<Fmi2Builder.Port> getPorts(int... valueReferences) {
        List<Integer> accept = Arrays.stream(valueReferences).boxed().collect(Collectors.toList());
        return ports.stream().filter(p -> accept.contains(p.getPortReferenceValue().intValue())).collect(Collectors.toList());
    }

    @Override
    public AMablPort getPort(String name) {
        return (AMablPort) this.getPorts(name).get(0);
    }

    @Override
    public AMablPort getPort(int valueReference) {
        return (AMablPort) this.getPorts(valueReference).get(0);
    }

    @Override
    public Map<Fmi2Builder.Port, Fmi2Builder.Variable> get(Fmi2Builder.Port... ports) {
        return get(builder.getDynamicScope(), ports);
    }

    @Override
    public Map<Fmi2Builder.Port, Fmi2Builder.Variable> get(Fmi2Builder.Scope<PStm> scope, Fmi2Builder.Port... ports) {

        List<AMablPort> selectedPorts;
        if (ports == null || ports.length == 0) {
            selectedPorts = outputPorts;
        } else {
            selectedPorts = Arrays.stream(ports).map(AMablPort.class::cast).collect(Collectors.toList());
        }

        List<AMablPort> sortedPorts =
                selectedPorts.stream().sorted(Comparator.comparing(Fmi2Builder.Port::getPortReferenceValue)).collect(Collectors.toList());
        ArrayVariable<Object> vrefBuf = getValueReferenceBuffer();

        for (int i = 0; i < sortedPorts.size(); i++) {
            Fmi2Builder.Port p = sortedPorts.get(i);
            PStateDesignator designator = vrefBuf.items().get(i).getDesignator().clone();
            scope.add(newAAssignmentStm(designator, newAIntLiteralExp(p.getPortReferenceValue().intValue())));
        }

        PType type = sortedPorts.get(0).getType();
        ArrayVariable<Object> valBuf = getIOBuffer(type);
        AAssigmentStm stm = newAAssignmentStm(builder.getGlobalFmiStatus().getDesignator().clone(),
                call(this.getReferenceExp().clone(), createFunctionName(FmiFunctionType.GET, sortedPorts.get(0)), vrefBuf.getReferenceExp().clone(),
                        newAUIntLiteralExp((long) sortedPorts.size()), valBuf.getReferenceExp().clone()));
        scope.add(stm);

        Map<Fmi2Builder.Port, Fmi2Builder.Variable> results = new HashMap<>();

        for (int i = 0; i < sortedPorts.size(); i++) {
            results.put(sortedPorts.get(i), valBuf.items().get(i));
        }

        return results;
    }


    @Override
    public Map<Fmi2Builder.Port, Fmi2Builder.Variable> get() {
        return get(builder.getDynamicScope(), outputPorts.stream().toArray(Fmi2Builder.Port[]::new));
    }

    @Override
    public Map<Fmi2Builder.Port, Fmi2Builder.Variable> get(int... valueReferences) {
        List<Integer> accept = Arrays.stream(valueReferences).boxed().collect(Collectors.toList());
        return get(builder.getDynamicScope(),
                outputPorts.stream().filter(p -> accept.contains(p.getPortReferenceValue().intValue())).toArray(Fmi2Builder.Port[]::new));
    }

    @Override
    public Map<Fmi2Builder.Port, Fmi2Builder.Variable> get(String... names) {
        List<String> accept = Arrays.asList(names);
        return get(builder.getDynamicScope(), outputPorts.stream().filter(p -> accept.contains(p.getName())).toArray(Fmi2Builder.Port[]::new));

    }

    private AMablPort createPort(ModelDescription.ScalarVariable sv) {
        Supplier<AMablPort> portCreator = () -> new AMablPort(this, sv);
        PortIdentifier pi = PortIdentifier.of(this, sv);
        return MablApiBuilder.getOrCreatePort(pi, portCreator);
    }

    /**
     * Stores the final value in rootScope
     * Uses the rootScope for valueReferences
     */
    @Override
    public Map<Fmi2Builder.Port, Fmi2Builder.Variable> getAndShare(String... names) {

        Map<Fmi2Builder.Port, Fmi2Builder.Variable> values = get(names);
        share(values);
        return values;
    }


    private String createFunctionName(FmiFunctionType fun, AMablPort p) {
        return createFunctionName(fun, p.scalarVariable.getType().type);
    }

    private String createFunctionName(FmiFunctionType f, ModelDescription.Types type) {
        String functionName = "";
        switch (f) {
            case GET:
                functionName += "get";
                break;
            case SET:
                functionName += "set";
                break;
        }
        functionName += type.name();
        return functionName;
    }

    @Override
    public Fmi2Builder.Value getSingle(String name) {
        return null;
    }

    @Override
    public void set(Fmi2Builder.Scope<PStm> scope, PortValueMap value) {


        if (value == null || value.isEmpty()) {
            return;
        }

        List<AMablPort> selectedPorts = value.keySet().stream().map(AMablPort.class::cast).collect(Collectors.toList());


        set(scope, selectedPorts, port -> {
            Object val = (value.get(port)).get();
            if (val instanceof Double) {
                return newARealLiteralExp((Double) val);
            }
            if (val instanceof Long) {
                return newAUIntLiteralExp((Long) val);
            }
            if (val instanceof Integer) {
                return newAIntLiteralExp((Integer) val);
            }
            if (val instanceof Boolean) {
                return newABoolLiteralExp((Boolean) val);
            }
            if (val instanceof String) {
                return newAStringLiteralExp((String) val);
            }
            return null;
        });
    }

    @Override
    public void set(Fmi2Builder.Scope<PStm> scope, PortVariableMap value) {

        List<AMablPort> selectedPorts;
        if (value == null || value.isEmpty()) {
            selectedPorts = inputPorts;
            value = findSourceValues(selectedPorts);
        } else {
            selectedPorts = value.keySet().stream().map(AMablPort.class::cast).collect(Collectors.toList());
        }

        final PortVariableMap valueFinal = value;
        set(scope, selectedPorts, port -> ((AMablVariable) valueFinal.get(port)).getReferenceExp().clone());
    }

    public void set(Fmi2Builder.Scope<PStm> scope, List<AMablPort> selectedPorts, Function<AMablPort, PExp> portToValue) {

        List<AMablPort> sortedPorts =
                selectedPorts.stream().sorted(Comparator.comparing(Fmi2Builder.Port::getPortReferenceValue)).collect(Collectors.toList());
        ArrayVariable<Object> vrefBuf = getValueReferenceBuffer();


        for (int i = 0; i < sortedPorts.size(); i++) {
            Fmi2Builder.Port p = sortedPorts.get(i);
            PStateDesignator designator = vrefBuf.items().get(i).getDesignator().clone();
            scope.add(newAAssignmentStm(designator, newAIntLiteralExp(p.getPortReferenceValue().intValue())));
        }

        PType type = sortedPorts.get(0).getType();
        ArrayVariable<Object> valBuf = getIOBuffer(type);
        for (int i = 0; i < sortedPorts.size(); i++) {
            AMablPort p = sortedPorts.get(i);
            PStateDesignator designator = valBuf.items().get(i).getDesignator();
            scope.add(newAAssignmentStm(designator.clone(), portToValue.apply(p).clone()));
        }

        //String statusName = builder.getNameGenerator().getName("status");

        AAssigmentStm stm = newAAssignmentStm(builder.getGlobalFmiStatus().getDesignator().clone(),
                call(this.getReferenceExp().clone(), createFunctionName(FmiFunctionType.SET, sortedPorts.get(0)), vrefBuf.getReferenceExp().clone(),
                        newAUIntLiteralExp((long) sortedPorts.size()), valBuf.getReferenceExp().clone()));
        scope.add(stm);
    }

    private PortVariableMap findSourceValues(List<AMablPort> selectedPorts) {
        //FIXME we need to figure out where the port's source is
        for (AMablPort port : selectedPorts) {
            if (port.getSourcePort() == null) {
                throw new RuntimeException(
                        "Attempting to obtain shared value from a port that is not linked. This port is missing a required " + "link: " + port);
            }

            if (port.getSourcePort().getSharedAsVariable() == null) {
                throw new RuntimeException(
                        "Attempting to obtain shared values from a port that is linked but has no value shared. Share a value " + "first. " + port);

            }
        }

        return new PortVariableMapImpl(selectedPorts.stream()
                .collect(Collectors.toMap(java.util.function.Function.identity(), port -> port.getSourcePort().getSharedAsVariable())));
    }

    @Override
    public void set(PortValueMap value) {
        set(builder.getDynamicScope(), value);
    }

    @Override
    public void set(Fmi2Builder.Port port, Fmi2Builder.Value value) {
        PortValueMap map = new PortValueMapImpl();
        map.put(port, value);
        set(map);
    }

    @Override
    public void set(PortVariableMap value) {
        set(builder.getDynamicScope(), value);
    }


    @Override
    public void set(String... names) {


        //FIXME get shared
        PortVariableMap value = null;

        set(value);

        /*
        List<Fmi2Builder.Port> ports = this.getPorts(names);
        ports.forEach(p -> {
            // Find the port that is the source of the given value
            AMablPort p_ = (AMablPort) p;
            AMablPort companionPort = p_.getSourcePort();
            // Create valuereference set array
            Pair<LexIdentifier, List<PStm>> valRefArray =
                    this.builder.getDynamicScope().findOrCreateValueReferenceArrayAndAssign(new long[]{p.getPortReferenceValue()});



            //AMablBuilder.rootScope.findOrCreateArrayOfSize(p_);


        });
*/


    }

    @Override
    public void setInt(Map<Integer, Fmi2Builder.Value<Integer>> values) {

    }

    @Override
    public void setString(Map<String, Fmi2Builder.Value<String>> value) {

    }

    @Override
    public void share(Map<Fmi2Builder.Port, Fmi2Builder.Variable> values) {
        //TODO share
        values.entrySet().stream().collect(Collectors.groupingBy(map -> ((AMablPort) map.getKey()).getType())).entrySet().stream().forEach(map -> {
            PType type = map.getKey();
            Map<Fmi2Builder.Port, Fmi2Builder.Variable> data =
                    map.getValue().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            ArrayVariable<Object> buffer = getSharedBuffer(type);

            data.keySet().stream().map(AMablPort.class::cast).sorted(Comparator.comparing(AMablPort::getPortReferenceValue)).forEach(port -> {
                //this is the sorted set of assignments, these can be replaced by a memcopy later

                if (port.getSharedAsVariable() == null) {
                    ArrayVariable<Object> newBuf = growBuffer(buffer, 1);
                    AMablVariable<Object> newShared = newBuf.items().get(newBuf.items().size() - 1);
                    port.setSharedAsVariable(newShared);
                }

                PStateDesignator designator = port.getSharedAsVariable().getDesignator();
                builder.getDynamicScope().add(newAAssignmentStm(designator.clone(), ((AMablVariable) data.get(port)).getReferenceExp().clone()));
            });

        });
    }

    private ArrayVariable<Object> growBuffer(ArrayVariable<Object> buffer, int increaseByCount) {

        String ioBufName = ((AIdentifierExp) buffer.getReferenceExp()).getName().getText();

        int length = buffer.size() + increaseByCount;
        PStm var = newALocalVariableStm(newAVariableDeclaration(newAIdentifier(ioBufName), buffer.type, length, null));

        buffer.getDeclaringStm().parent().replaceChild(buffer.getDeclaringStm(), var);
        // getDeclaredScope().addAfter(getDeclaringStm(), var);

        List<AMablVariable<Object>> items = IntStream.range(buffer.size(), length).mapToObj(
                i -> new AMablVariable<>(var, type, this.getDeclaredScope(), builder.getDynamicScope(),
                        newAArayStateDesignator(newAIdentifierStateDesignator(newAIdentifier(ioBufName)), newAIntLiteralExp(i)),
                        newAArrayIndexExp(newAIdentifierExp(ioBufName), Collections.singletonList(newAIntLiteralExp(i)))))
                .collect(Collectors.toList());

        //we can not replace these as some of them may be used and could potential have reference problems (they should not but just to be sure)
        items.addAll(0, buffer.items());

        return new ArrayVariable<>(var, type, getDeclaredScope(), builder.getDynamicScope(), newAIdentifierStateDesignator(newAIdentifier(ioBufName)),
                newAIdentifierExp(ioBufName), items);

    }

    @Override
    public void share(Fmi2Builder.Port port, Fmi2Builder.Variable value) {
        Map<Fmi2Builder.Port, Fmi2Builder.Variable> map = new HashMap<>();
        map.put(port, value);
        share(map);
    }

    @Override
    public Fmi2Builder.TimeDeltaValue step(Fmi2Builder.TimeDeltaValue deltaTime) {
        return null;
    }

    @Override
    public Fmi2Builder.TimeDeltaValue step(Fmi2Builder.Variable<PStm, Fmi2Builder.TimeDeltaValue> deltaTime) {
        return null;
    }

    @Override
    public Fmi2Builder.TimeDeltaValue step(double deltaTime) {
        return null;
    }

    @Override
    public Fmi2Builder.TimeTaggedState getState() {
        return null;
    }

    @Override
    public Fmi2Builder.Time setState(Fmi2Builder.TimeTaggedState state) {
        return null;
    }

    @Override
    public Fmi2Builder.Time setState() {
        return null;
    }

    public AMablFmu2Variable getOwner() {
        return this.owner;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public enum FmiFunctionType {
        GET,
        SET
    }


}
