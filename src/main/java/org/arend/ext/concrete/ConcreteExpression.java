package org.arend.ext.concrete;

import javax.annotation.Nonnull;

public interface ConcreteExpression extends ConcreteSourceNode {
  @Nonnull ConcreteExpression app(@Nonnull ConcreteExpression argument);
  @Nonnull ConcreteExpression appImp(@Nonnull ConcreteExpression argument);
}
