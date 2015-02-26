package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.visitor.ExpressionVisitor;

import java.io.PrintStream;
import java.util.List;

public class NatExpression extends Expression implements Abstract.NatExpression {
  public final static int PREC = 11;

  @Override
  public void prettyPrint(PrintStream stream, List<String> names, int prec) {
    stream.print(toString());
  }

  @Override
  public boolean equals(Object o) {
    return o == this || o instanceof NatExpression;
  }

  @Override
  public String toString() {
    return "N";
  }

  @Override
  public <T> T accept(ExpressionVisitor<? extends T> visitor) {
    return visitor.visitNat(this);
  }
}
