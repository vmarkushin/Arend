package com.jetbrains.jetpad.vclang.term.definition;

import com.jetbrains.jetpad.vclang.naming.namespace.Namespace;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.term.expr.ClassCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.sort.SortMax;
import com.jetbrains.jetpad.vclang.term.expr.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.term.expr.type.PiUniverseType;
import com.jetbrains.jetpad.vclang.term.expr.type.TypeMax;
import com.jetbrains.jetpad.vclang.term.internal.FieldSet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.ClassCall;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.param;

public class ClassDefinition extends Definition {
  private final Namespace myOwnNamespace;
  private final Namespace myInstanceNamespace;
  private final FieldSet myFieldSet;
  private final Set<ClassDefinition> mySuperClasses;
  private final Map<ClassField, Abstract.ReferableSourceNode> myAliases;
  private boolean myHasErrors;

  private ClassField myEnclosingThisField = null;

  public ClassDefinition(Abstract.ClassDefinition abstractDef, Namespace ownNamespace, Namespace instanceNamespace, Map<ClassField, Abstract.ReferableSourceNode> aliases) {
    this(abstractDef, new FieldSet(), new HashSet<ClassDefinition>(), ownNamespace, instanceNamespace, aliases);
  }

  public ClassDefinition(Abstract.ClassDefinition abstractDef, FieldSet fieldSet, Set<ClassDefinition> superClasses, Namespace ownNamespace, Namespace instanceNamespace, Map<ClassField, Abstract.ReferableSourceNode> aliases) {
    super(abstractDef);
    myFieldSet = fieldSet;
    mySuperClasses = superClasses;
    myOwnNamespace = ownNamespace;
    myInstanceNamespace = instanceNamespace;
    myAliases = aliases;
    myHasErrors = false;
  }

  @Override
  public Abstract.ClassDefinition getAbstractDefinition() {
    return (Abstract.ClassDefinition) super.getAbstractDefinition();
  }

  public FieldSet getFieldSet() {
    return myFieldSet;
  }

  public SortMax getSorts() {
    return myFieldSet.getSorts(ClassCall(this, myFieldSet));
  }

  public boolean isSubClassOf(ClassDefinition classDefinition) {
    if (this.equals(classDefinition)) return true;
    for (ClassDefinition superClass : mySuperClasses) {
      if (superClass.isSubClassOf(classDefinition)) return true;
    }
    return false;
  }

  @Override
  public TypeMax getTypeWithParams(List<DependentLink> params, LevelSubstitution polyParams) {
    DependentLink link = EmptyDependentLink.getInstance();
    if (getThisClass() != null) {
      link = param(ClassCall(getThisClass()));
    }
    return new PiUniverseType(link, getSorts().subst(polyParams));
  }

  @Override
  public ClassCallExpression getDefCall(LevelSubstitution polyParams) {
    return ClassCall(this, polyParams, myFieldSet);
  }

  @Override
  public ClassCallExpression getDefCall(LevelSubstitution polyParams, List<Expression> args) {
    return new ClassCallExpression(this, polyParams, myFieldSet);
  }

  @Override
  public void setThisClass(ClassDefinition enclosingClass) {
    assert myEnclosingThisField == null;
    super.setThisClass(enclosingClass);
    if (enclosingClass != null) {
      myEnclosingThisField = new ClassField(null, ClassCall(enclosingClass), this, param("\\this", ClassCall(this)));
      myEnclosingThisField.setThisClass(this);
      myFieldSet.addField(myEnclosingThisField, ClassCall(this, myFieldSet));
    }
  }

  @Override
  public boolean typeHasErrors() {
    return false;
  }

  @Override
  public TypeCheckingStatus hasErrors() {
    return myHasErrors ? TypeCheckingStatus.HAS_ERRORS : TypeCheckingStatus.NO_ERRORS;
  }

  public void hasErrors(boolean has) {
    myHasErrors = has;
  }

  public ClassField getEnclosingThisField() {
    return myEnclosingThisField;
  }

  @Override
  public Namespace getOwnNamespace() {
    return myOwnNamespace;
  }

  @Override
  public Namespace getInstanceNamespace() {
    return myInstanceNamespace;
  }

  public Abstract.ReferableSourceNode getFieldAlias(ClassField field) {
    Abstract.ReferableSourceNode alias = myAliases.get(field);
    return alias != null ? alias : field.getAbstractDefinition();
  }
}
