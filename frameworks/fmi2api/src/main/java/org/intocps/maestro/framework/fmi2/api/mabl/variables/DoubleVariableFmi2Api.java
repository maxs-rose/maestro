package org.intocps.maestro.framework.fmi2.api.mabl.variables;

import org.intocps.maestro.ast.node.PExp;
import org.intocps.maestro.ast.node.PStateDesignator;
import org.intocps.maestro.ast.node.PStm;
import org.intocps.maestro.ast.node.PType;
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.IMablScope;
import org.intocps.maestro.framework.fmi2.api.mabl.values.DoubleExpressionValue;
import org.intocps.maestro.framework.fmi2.api.mabl.values.DoubleValueFmi2Api;

import static org.intocps.maestro.ast.MableAstFactory.newARealNumericPrimitiveType;

public class DoubleVariableFmi2Api extends VariableFmi2Api<Fmi2Builder.DoubleValue> implements Fmi2Builder.DoubleVariable<PStm> {
    public DoubleVariableFmi2Api(PStm declaration, IMablScope declaredScope, Fmi2Builder.DynamicActiveScope<PStm> dynamicScope,
            PStateDesignator designator, PExp referenceExp) {
        super(declaration, newARealNumericPrimitiveType(), declaredScope, dynamicScope, designator, referenceExp);
    }


    @Override
    public void set(Double value) {
        super.setValue(new DoubleValueFmi2Api(value));
    }


    public void setValue(DoubleExpressionValue addition) {
        super.setValue(addition.getExp());
    }


    public DoubleExpressionValue toMath() {
        return new DoubleExpressionValue(this.getExp());
    }


    @Override
    public PType getType() {
        return newARealNumericPrimitiveType();
    }
}
