package com.jetbrains.jetpad.vclang.typechecking.error;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.visitor.PrettyPrintVisitor;

import java.util.ArrayList;

public class ArgInferenceError extends TypeCheckingError {
  public final Expression[] candidates;
  public final Expression expected;
  public final Expression actual;

  @Deprecated
  public ArgInferenceError(String message, Abstract.SourceNode expression, Expression... candidates) {
    super(null, message, expression);
    this.candidates = candidates;
    this.expected = null;
    this.actual = null;
  }

  @Deprecated
  public ArgInferenceError(String message, Expression expected, Expression actual, Abstract.SourceNode expression, Expression candidate) {
    super(null, message, expression);
    this.candidates = new Expression[1];
    this.candidates[0] = candidate;
    this.expected = expected;
    this.actual = actual;
  }

  public static String functionArg(int index) {
    return "Cannot infer the " + ordinal(index) + " argument to function";
  }

  public static String typeOfFunctionArg(int index) {
    return "Cannot infer type of the " + ordinal(index) + " argument of function";
  }

  public static String lambdaArg(int index) {
    return "Cannot infer type of the " + ordinal(index) + " parameter of lambda";
  }

  public static String levelOfLambdaArg(int index) {
    return "Cannot infer level of the type of the " + ordinal(index) + " parameter of lambda";
  }

  public static String parameter(int index) {
    return "Cannot infer the " + ordinal(index) + " parameter to constructor";
  }

  public static String expression() {
    return "Cannot infer an expression";
  }

  public static String suffix(int n) {
    if (n >= 10 && n < 20) {
      return "th";
    }
    switch (n % 10) {
      case 1:
        return "st";
      case 2:
        return "nd";
      case 3:
        return "rd";
      default:
        return "th";
    }
  }

  public static String ordinal(int n) {
    return n + suffix(n);
  }
}
