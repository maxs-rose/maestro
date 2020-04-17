package org.intocps.maestro.ast;

import org.antlr.v4.runtime.Token;

import java.util.Objects;

public class LexIdentifier implements ExternalNode {
    private final String text;

    public Token getSymbol() {
        return symbol;
    }

    private final Token symbol;

    public LexIdentifier(String text, org.antlr.v4.runtime.Token symbol) {
        this.text = text;
        this.symbol = symbol;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public Object clone() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LexIdentifier that = (LexIdentifier) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
