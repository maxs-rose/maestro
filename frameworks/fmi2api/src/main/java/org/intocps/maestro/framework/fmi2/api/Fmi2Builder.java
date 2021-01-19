package org.intocps.maestro.framework.fmi2.api;

import org.intocps.maestro.ast.node.PExp;
import org.intocps.maestro.ast.node.PStm;
import org.intocps.maestro.framework.fmi2.api.mabl.PredicateFmi2Api;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.BooleanVariableFmi2Api;
import org.intocps.orchestration.coe.modeldefinition.ModelDescription;

import javax.xml.xpath.XPathExpressionException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface Fmi2Builder<S, B> {
    B build() throws Exception;

    PStm buildRaw();


    /**
     * Gets the default scope
     *
     * @return
     */
    Scope<S> getRootScope();
    //    public abstract void pushScope(Scope scope);

    DynamicActiveScope<S> getDynamicScope();

    /**
     * Get handle to the current time
     *
     * @return
     */
    //Time getCurrentTime();

    /**
     * Gets a specific time from a number
     *
     * @param time
     * @return
     */
    //Time getTime(double time);

    /**
     * Gets a tag to the last value obtained for the given port
     *
     * @param port
     * @return
     */
    <V, T> Variable<T, V> getCurrentLinkedValue(Port port);


    VariableCreator<S> variableCreator();

    /**
     * New boolean that can be used as a predicate
     *
     * @param
     * @return
     */

    /**
     * Scoping functions
     */
    interface Scoping<T> {
        WhileScope<T> enterWhile(LogicBuilder.Predicate predicate);

        IfScope<T> enterIf(LogicBuilder.Predicate predicate);

        Scope<T> leave();


        VariableCreator<T> getVariableCreator();

        void add(T... commands);

        void addAll(Collection<T> commands);

        void addBefore(T item, T... commands);

        void addAfter(T item, T... commands);

        Scoping<T> activate();
    }

    /**
     * Basic scope. Allows a value to be stored or override a tag
     */
    interface Scope<T> extends Scoping<T> {
        @Override
        Scope<T> activate();

        /**
         * Store a given value
         *
         * @param value
         * @return
         */
        DoubleVariable<T> store(double value);

        BoolVariable<T> store(boolean value);

        IntVariable<T> store(int value);

        /**
         * Store a given value with a prefix name
         *
         * @param value
         * @return
         */
        DoubleVariable<T> store(String name, double value);

        BoolVariable<T> store(String name, boolean value);

        IntVariable<T> store(String name, int value);

        /**
         * Store the given value and get a tag for it. Copy
         *
         * @param tag
         * @return
         */
        @Deprecated
        <V> Variable<T, V> store(Value<V> tag);


        //   /**
        //   * Override a tags value from an existing value. Copy
        //   *
        //   * @param tag
        //   * @param value
        //   * @return
        //   */
        // <V extends Value> Variable<T, V> store(V tag, V value);
        //TODO add overload with name prefix, tag override is done through variable and not the scope


    }

    /**
     * Dynamic scope which always reflects the current active scope of the builder
     */
    interface DynamicActiveScope<T> extends Scope<T> {

    }

    /**
     * If scope, default scope is then
     */
    interface IfScope<T> {
        /**
         * Switch to then scope
         *
         * @return
         */
        Scope<T> enterThen();

        /**
         * Switch to else scope
         *
         * @return
         */
        Scope<T> enterElse();
    }

    /**
     * While
     */
    interface WhileScope<T> {
    }


    interface LogicBuilder {

        Predicate isEqual(Port a, Port b);


        <T> Predicate isLess(T a, T b);

        <T> Predicate isLessOrEqualTo(ProvidesReferenceExp a, ProvidesReferenceExp b);

        Predicate isGreater(Value<Double> a, double b);

        <T> Predicate fromValue(Value<T> value);

        // Predicate fromExternalFunction(String name, Value... args);


        interface Predicate {
            Predicate and(Predicate p);

            Predicate or(Predicate p);

            Predicate not();

            PExp getExp();

            PredicateFmi2Api and(BooleanVariableFmi2Api booleanVariable);
        }

    }

    interface Type {
    }

    interface Numeric<A extends Number> extends Value<Number>, Type {
        void set(A value);

        @Override
        A get();
    }


    interface Port {

        /**
         * Get the port name
         *
         * @return
         */
        String getName();

        /**
         * Get the port reference value
         *
         * @return
         */
        Long getPortReferenceValue();

        /**
         * Link the current port to the receiving port. After this the receiving port will resolve its linked value to the value of this port
         *
         * @param receiver
         */
        void linkTo(Port... receiver) throws PortLinkException;

        /**
         * Break the source link
         */
        void breakLink() throws PortLinkException;

        class PortLinkException extends Exception {
            Port port;

            public PortLinkException(String message, Port port) {
                super(message);
                this.port = port;
            }
        }
    }

    interface Value<V> {
        static Value<Double> of(double a) {
            return null;
        }

       /* static Value of(Variable var) {
            return null;
        }*/

        V get();
    }

    interface IntValue extends Value<Integer> {
    }

    interface BoolValue extends Value<Boolean> {
    }

    interface DoubleValue extends Value<Double> {
    }

    interface StringValue extends Value<String> {
    }

    interface ReferenceValue extends Value<PExp> {
    }

    interface NamedValue extends Value<Object> {
    }


    interface VariableCreator<T> {

        Fmu2Variable<T> createFMU(String name, ModelDescription modelDescription,
                URI path) throws XPathExpressionException, InvocationTargetException, IllegalAccessException;
    }


    interface IntVariable<T> extends Variable<T, IntValue> {
        void decrement();

        void increment();

        //void set(int value);
    }

    interface ProvidesReferenceExp {
        PExp getReferenceExp();
    }

    interface DoubleVariable<T> extends Variable<T, DoubleValue>, ProvidesReferenceExp {

        void set(Double value);

        DoubleValue plus(DoubleVariable<T> stepSizeVar);
    }

    interface BoolVariable<T> extends Variable<T, BoolValue> {
        LogicBuilder.Predicate getPredicate();
        //void set(Boolean value);
    }

    interface StringVariable<T> extends Variable<T, StringValue> {
        //void set(String value);
    }

    interface NamedVariable<T> extends Variable<T, NamedValue> {
    }

    interface StateVariable<T> extends Variable<T, Object> {
        /**
         * Sets this state on the owning component in the active scope
         */
        void set() throws IllegalStateException;

        /**
         * Sets this state on the owning component in the given scope
         */
        void set(Scope<T> scope) throws IllegalStateException;

        /**
         * Destroys the state in the active scope. After this no other operation on the state is allowed
         */
        void destroy() throws IllegalStateException;

        /**
         * Destroys the state in the active scope. After this no other operation on the state is allowed
         */
        void destroy(Scope<T> scope) throws IllegalStateException;
    }


    /**
     * Handle for an fmu for the creation of component
     */
    interface Fmu2Variable<S> extends Variable<S, NamedVariable<S>> {
        Fmi2ComponentVariable<S> instantiate(String name);

        Fmi2ComponentVariable<S> instantiate(String name, Scope<S> scope);

        void unload();

        void unload(Scope<S> scope);
    }

    /**
     * Interface for an fmi compoennt.
     * <p>
     * Note that all methods that do not take a scope uses the builders dynamic scope and adds the underlying instructions int he active scope.
     */
    interface Fmi2ComponentVariable<T> extends Variable<T, NamedVariable<T>> {

        void setupExperiment(boolean toleranceDefined, double tolerance, DoubleVariable<T> startTime, boolean endTimeDefined,
                DoubleVariable<T> endTime);

        void setupExperiment(boolean toleranceDefined, double tolerance, double startTime, boolean endTimeDefined, double endTime);

        void enterInitializationMode();

        void exitInitializationMode();

        void setupExperiment(Scope<T> scope, boolean toleranceDefined, double tolerance, DoubleVariable<T> startTime, boolean endTimeDefined,
                DoubleVariable<T> endTime);

        void setupExperiment(Scope<T> scope, boolean toleranceDefined, double tolerance, double startTime, boolean endTimeDefined, double endTime);

        void enterInitializationMode(Scope<T> scope);

        void exitInitializationMode(Scope<T> scope);

        /**
         * @param scope
         * @param currentCommunicationPoint
         * @param communicationStepSize
         * @param noSetFMUStatePriorToCurrentPoint a pair representing (full step completed, current time after step)
         * @return
         */
        Map.Entry<BoolVariable<T>, DoubleVariable<T>> step(Scope<T> scope, DoubleVariable<T> currentCommunicationPoint,
                DoubleVariable<T> communicationStepSize, BoolVariable<T> noSetFMUStatePriorToCurrentPoint);

        Map.Entry<BoolVariable<T>, DoubleVariable<T>> step(Scope<T> scope, DoubleVariable<T> currentCommunicationPoint,
                DoubleVariable<T> communicationStepSize);

        Map.Entry<BoolVariable<T>, DoubleVariable<T>> step(DoubleVariable<T> currentCommunicationPoint, DoubleVariable<T> communicationStepSize,
                BoolVariable<T> noSetFMUStatePriorToCurrentPoint);

        Map.Entry<BoolVariable<T>, DoubleVariable<T>> step(DoubleVariable<T> currentCommunicationPoint, DoubleVariable<T> communicationStepSize);


        List<? extends Port> getPorts();

        /**
         * Get ports by name
         *
         * @param names
         * @return
         */
        List<? extends Port> getPorts(String... names);

        /**
         * Get ports by ref val
         *
         * @param valueReferences
         * @return
         */
        List<? extends Port> getPorts(int... valueReferences);

        /**
         * Get port by name
         *
         * @param name
         * @return
         */
        Port getPort(String name);

        /**
         * Get port by ref val
         *
         * @param valueReference
         * @return
         */
        Port getPort(int valueReference);

        /**
         * Get port values aka fmiGet
         *
         * @param ports
         * @return
         */
        <V> Map<Port, Variable<T, V>> get(Port... ports);

        <V> Map<Port, Variable<T, V>> get(Scope<T> scope, Port... ports);

        /**
         * Get all (linked) port values
         *
         * @return
         */
        <V> Map<Port, Variable<T, V>> get();

        /**
         * get filter by value reference
         *
         * @param valueReferences
         * @return
         */
        <V> Map<Port, Variable<T, V>> get(int... valueReferences);

        /**
         * Get filter by names
         *
         * @param names
         * @return
         */
        <V> Map<Port, Variable<T, V>> get(String... names);

        <V> Map<Port, Variable<T, V>> getAndShare(String... names);

        /**
         * Get the value of a single port
         *
         * @param name
         * @return
         */
        <V> Value<V> getSingle(String name);

        <V> void set(Scope<T> scope, PortValueMap<V> value);


        <V> void set(Scope<T> scope, PortVariableMap<T, V> value);

        /**
         * Set port values (if ports is not from this fmu then the links are used to remap)
         *
         * @param value
         */
        <V> void set(PortValueMap<V> value);

        <V> void set(Port port, Value<V> value);

        <V> void set(PortVariableMap<T, V> value);

        /**
         * Set this fmu port by name and link
         */
        void setLinked(Scope<T> scope, Port... filterPorts);

        void setLinked();

        void setLinked(Port... filterPorts);

        void setLinked(String... filterNames);

        void setLinked(long... filterValueReferences);

        /**
         * Set this fmu ports by val ref
         *
         * @param values
         */
        void setInt(Map<Integer, Value<Integer>> values);

        /**
         * Set this fmy ports by name
         *
         * @param value
         */
        void setString(Map<String, Value<String>> value);

        /**
         * Makes the values publicly available to all linked connections. On next set these ports will be resolved to the values given for
         * other fmu
         *
         * @param values
         */
        <V> void share(Map<Port, Variable<T, V>> values);

        /**
         * Makes the value publicly available to all linked connections. On next set these ports will be resolved to the values given for
         * other fmu
         *
         * @param value
         */
        <V> void share(Port port, Variable<T, V> value);

        /**
         * Get the current state
         *
         * @return
         */
        StateVariable<T> getState();

        /**
         * Get the current state
         *
         * @return
         */
        StateVariable<T> getState(Scope<T> scope);


        interface PortVariableMap<S, V> extends Map<Port, Variable<S, V>> {
        }

        interface PortValueMap<V> extends Map<Port, Value<V>> {
        }
    }

    interface Variable<T, V> {
        String getName();

        void setValue(V value);

        //        void setValue(Variable<T, V> variable);

        //        void setValue(Variable<PStm, V> variable, Scope<PStm> scope);

        void setValue(V value, Scope<T> scope);

        void setValue(ProvidesReferenceExp add);

        void setValue(ProvidesReferenceExp add, Scope<PStm> scope);

        Scope<T> getDeclaredScope();
    }
}
