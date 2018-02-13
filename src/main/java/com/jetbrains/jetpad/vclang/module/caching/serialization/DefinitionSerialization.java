package com.jetbrains.jetpad.vclang.module.caching.serialization;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.definition.*;
import com.jetbrains.jetpad.vclang.core.elimtree.Body;
import com.jetbrains.jetpad.vclang.core.elimtree.ClauseBase;
import com.jetbrains.jetpad.vclang.core.elimtree.ElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.IntervalElim;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.pattern.BindingPattern;
import com.jetbrains.jetpad.vclang.core.pattern.ConstructorPattern;
import com.jetbrains.jetpad.vclang.core.pattern.EmptyPattern;
import com.jetbrains.jetpad.vclang.core.pattern.Pattern;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;
import com.jetbrains.jetpad.vclang.term.Precedence;
import com.jetbrains.jetpad.vclang.util.Pair;

import javax.annotation.Nonnull;
import java.util.Map;

public class DefinitionSerialization {
  private final CallTargetIndexProvider myCallTargetIndexProvider;

  public DefinitionSerialization(CallTargetIndexProvider callTargetIndexProvider) {
    myCallTargetIndexProvider = callTargetIndexProvider;
  }

  static String getNameIdFor(GlobalReferable referable, String name) {
    Precedence precedence = referable.getPrecedence();
    char fixityChar = precedence.isInfix ? 'i' : 'n';
    final char assocChr;
    switch (precedence.associativity) {
      case LEFT_ASSOC:
        assocChr = 'l';
        break;
      case RIGHT_ASSOC:
        assocChr = 'r';
        break;
      default:
        assocChr = 'n';
    }
    return "" + fixityChar + assocChr + precedence.priority + ';' + name;
  }

  DefinitionProtos.Definition writeDefinition(Definition definition) {
    final DefinitionProtos.Definition.Builder out = DefinitionProtos.Definition.newBuilder();

    switch (definition.status()) {
      case HEADER_HAS_ERRORS:
        out.setStatus(DefinitionProtos.Definition.Status.HEADER_HAS_ERRORS);
        break;
      case BODY_HAS_ERRORS:
        out.setStatus(DefinitionProtos.Definition.Status.BODY_HAS_ERRORS);
        break;
      case HEADER_NEEDS_TYPE_CHECKING:
        out.setStatus(DefinitionProtos.Definition.Status.HEADER_NEEDS_TYPE_CHECKING);
        break;
      case BODY_NEEDS_TYPE_CHECKING:
        out.setStatus(DefinitionProtos.Definition.Status.BODY_NEEDS_TYPE_CHECKING);
        break;
      case HAS_ERRORS:
        out.setStatus(DefinitionProtos.Definition.Status.HAS_ERRORS);
        break;
      case NO_ERRORS:
        out.setStatus(DefinitionProtos.Definition.Status.NO_ERRORS);
        break;
      default:
        throw new IllegalStateException("Unknown typechecking status");
    }

    if (definition.getThisClass() != null) {
      out.setThisClassRef(myCallTargetIndexProvider.getDefIndex(definition.getThisClass()));
    }

    final ExpressionSerialization defSerializer = new ExpressionSerialization(myCallTargetIndexProvider);

    if (definition instanceof ClassDefinition) {
      // type cannot possibly have errors
      out.setClass_(writeClassDefinition(defSerializer, (ClassDefinition) definition));
    } else if (definition instanceof DataDefinition) {
      out.setData(writeDataDefinition(defSerializer, (DataDefinition) definition));
    } else if (definition instanceof FunctionDefinition) {
      out.setFunction(writeFunctionDefinition(defSerializer, (FunctionDefinition) definition));
    } else {
      throw new IllegalStateException();
    }

    return out.build();
  }

  private DefinitionProtos.Definition.ClassData writeClassDefinition(ExpressionSerialization defSerializer, ClassDefinition definition) {
    DefinitionProtos.Definition.ClassData.Builder builder = DefinitionProtos.Definition.ClassData.newBuilder();

    for (ClassField field : definition.getPersonalFields()) {
      DefinitionProtos.Definition.ClassData.Field.Builder fBuilder = DefinitionProtos.Definition.ClassData.Field.newBuilder();
      fBuilder.setName(getNameIdFor(field.getReferable(), field.getReferable().textRepresentation()));
      fBuilder.setThisParam(defSerializer.writeParameter(field.getThisParameter()));
      Expression baseType = field.getBaseType(Sort.STD);
      if (baseType != null) fBuilder.setType(defSerializer.writeExpr(baseType));
      builder.addPersonalField(fBuilder.build());
    }

    for (ClassField classField : definition.getFields()) {
      builder.addFieldRef(myCallTargetIndexProvider.getDefIndex(classField));
    }
    for (Map.Entry<ClassField, ClassDefinition.Implementation> impl : definition.getImplemented()) {
      DefinitionProtos.Definition.ClassData.Implementation.Builder iBuilder = DefinitionProtos.Definition.ClassData.Implementation.newBuilder();
      iBuilder.setThisParam(defSerializer.writeParameter(impl.getValue().thisParam));
      iBuilder.setTerm(defSerializer.writeExpr(impl.getValue().term));
      builder.putImplementations(myCallTargetIndexProvider.getDefIndex(impl.getKey()), iBuilder.build());
    }
    builder.setSort(defSerializer.writeSort(definition.getSort()));
    if (definition.getEnclosingThisField() != null) {
      builder.setEnclosingThisFieldRef(myCallTargetIndexProvider.getDefIndex(definition.getEnclosingThisField()));
    }

    for (ClassDefinition classDefinition : definition.getSuperClasses()) {
      builder.addSuperClassRef(myCallTargetIndexProvider.getDefIndex(classDefinition));
    }

    if (definition.getCoercingField() != null) {
      builder.setCoercingFieldRef(myCallTargetIndexProvider.getDefIndex(definition.getCoercingField()));
    } else {
      builder.setCoercingFieldRef(-1);
    }
    return builder.build();
  }

  private DefinitionProtos.Definition.DataData writeDataDefinition(ExpressionSerialization defSerializer, DataDefinition definition) {
    DefinitionProtos.Definition.DataData.Builder builder = DefinitionProtos.Definition.DataData.newBuilder();

    builder.addAllParam(defSerializer.writeParameters(definition.getParameters()));
    if (definition.status().headerIsOK()) {
      builder.setSort(defSerializer.writeSort(definition.getSort()));
    }

    for (Constructor constructor : definition.getConstructors()) {
      DefinitionProtos.Definition.DataData.Constructor.Builder cBuilder = DefinitionProtos.Definition.DataData.Constructor.newBuilder();
      if (constructor.getPatterns() != null) {
        for (Pattern pattern : constructor.getPatterns().getPatternList()) {
          cBuilder.addPattern(writePattern(defSerializer, pattern));
        }
      }
      for (ClauseBase clause : constructor.getClauses()) {
        cBuilder.addClause(writeClause(defSerializer, clause));
      }
      cBuilder.addAllParam(defSerializer.writeParameters(constructor.getParameters()));
      if (constructor.getBody() != null) {
        cBuilder.setConditions(writeBody(defSerializer, constructor.getBody()));
      }

      builder.putConstructors(getNameIdFor(constructor.getReferable(), constructor.getReferable().textRepresentation()), cBuilder.build());
    }

    builder.setMatchesOnInterval(definition.matchesOnInterval());
    int i = 0;
    for (DependentLink link = definition.getParameters(); link.hasNext(); link = link.getNext()) {
      builder.addCovariantParameter(definition.isCovariant(i++));
    }

    return builder.build();
  }

  private DefinitionProtos.Definition.Clause writeClause(ExpressionSerialization defSerializer, ClauseBase clause) {
    DefinitionProtos.Definition.Clause.Builder builder = DefinitionProtos.Definition.Clause.newBuilder();
    for (Pattern pattern : clause.patterns) {
      builder.addPattern(writePattern(defSerializer, pattern));
    }
    builder.setExpression(defSerializer.writeExpr(clause.expression));
    return builder.build();
  }

  private DefinitionProtos.Definition.Pattern writePattern(ExpressionSerialization defSerializer, Pattern pattern) {
    DefinitionProtos.Definition.Pattern.Builder builder = DefinitionProtos.Definition.Pattern.newBuilder();
    if (pattern instanceof BindingPattern) {
      builder.setBinding(DefinitionProtos.Definition.Pattern.Binding.newBuilder()
        .setVar(defSerializer.writeParameter(((BindingPattern) pattern).getBinding())));
    } else if (pattern instanceof EmptyPattern) {
      builder.setEmpty(DefinitionProtos.Definition.Pattern.Empty.newBuilder());
    } else if (pattern instanceof ConstructorPattern) {
      DefinitionProtos.Definition.Pattern.ConstructorRef.Builder pBuilder = DefinitionProtos.Definition.Pattern.ConstructorRef.newBuilder();
      pBuilder.setConstructorRef(myCallTargetIndexProvider.getDefIndex(((ConstructorPattern) pattern).getConstructor()));
      for (Pattern patternArgument : ((ConstructorPattern) pattern).getArguments()) {
        pBuilder.addPattern(writePattern(defSerializer, patternArgument));
      }
      builder.setConstructor(pBuilder.build());
    } else {
      throw new IllegalArgumentException();
    }
    return builder.build();
  }

  private DefinitionProtos.Definition.FunctionData writeFunctionDefinition(ExpressionSerialization defSerializer, FunctionDefinition definition) {
    DefinitionProtos.Definition.FunctionData.Builder builder = DefinitionProtos.Definition.FunctionData.newBuilder();

    if (definition.status().headerIsOK()) {
      builder.addAllParam(defSerializer.writeParameters(definition.getParameters()));
      if (definition.getResultType() != null) builder.setType(defSerializer.writeExpr(definition.getResultType()));
    }
    if (definition.status().bodyIsOK() && definition.getBody() != null) {
      builder.setBody(writeBody(defSerializer, definition.getBody()));
    }

    return builder.build();
  }

  private DefinitionProtos.Body writeBody(ExpressionSerialization defSerializer, @Nonnull Body body) {
    DefinitionProtos.Body.Builder bodyBuilder = DefinitionProtos.Body.newBuilder();
    if (body instanceof IntervalElim) {
      IntervalElim intervalElim = (IntervalElim) body;
      DefinitionProtos.Body.IntervalElim.Builder intervalBuilder = DefinitionProtos.Body.IntervalElim.newBuilder();
      intervalBuilder.addAllParam(defSerializer.writeParameters(intervalElim.getParameters()));
      for (Pair<Expression, Expression> pair : intervalElim.getCases()) {
        DefinitionProtos.Body.ExpressionPair.Builder pairBuilder = DefinitionProtos.Body.ExpressionPair.newBuilder();
        if (pair.proj1 != null) {
          pairBuilder.setLeft(defSerializer.writeExpr(pair.proj1));
        }
        if (pair.proj2 != null) {
          pairBuilder.setRight(defSerializer.writeExpr(pair.proj2));
        }
        intervalBuilder.addCase(pairBuilder);
      }
      if (intervalElim.getOtherwise() != null) {
        intervalBuilder.setOtherwise(defSerializer.writeElimTree(intervalElim.getOtherwise()));
      }
      bodyBuilder.setIntervalElim(intervalBuilder);
    } else if (body instanceof ElimTree) {
      bodyBuilder.setElimTree(defSerializer.writeElimTree((ElimTree) body));
    } else {
      throw new IllegalStateException();
    }
    return bodyBuilder.build();
  }
}
