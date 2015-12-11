package com.jetbrains.jetpad.vclang.term.pattern;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.definition.Binding;
import com.jetbrains.jetpad.vclang.term.expr.DefCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnyConstructorPattern extends ConstructorPattern implements Abstract.AnyConstructorPattern {

  public AnyConstructorPattern() {
    super(null, null);
  }

  @Override
  public Utils.PatternMatchResult match(Expression expr, List<Binding> context) {
    Expression func = (context == null ? expr : expr.normalize(NormalizeVisitor.Mode.WHNF, context)).getFunction(new ArrayList<Expression>());
    if (!(func instanceof DefCallExpression && ((DefCallExpression) func).getDefinition() instanceof Abstract.Constructor)) {
      return new Utils.PatternMatchMaybeResult(this, expr);
    } else {
      return new Utils.PatternMatchOKResult(Collections.singletonList(expr));
    }
  }
}
