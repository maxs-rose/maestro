package org.intocps.maestro.framework.fmi2.api.mabl;

import org.intocps.maestro.ast.node.PType;
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder;

public class AMablValue<V> implements Fmi2Builder.Value<V> {
    final V value;
    final PType type;

    public AMablValue(PType type, V value) {
        this.value = value;
        this.type = type;
    }

    public PType getType() {
        return type;
    }

    @Override
    public V get() {
        return value;
    }
}