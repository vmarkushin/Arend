package org.arend.typechecking.order.listener;

import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.context.param.TypedSingleDependentLink;
import org.arend.core.definition.*;
import org.arend.core.elimtree.Clause;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.ErrorExpression;
import org.arend.core.expr.PiExpression;
import org.arend.core.sort.Sort;
import org.arend.error.CompositeErrorReporter;
import org.arend.error.CountingErrorReporter;
import org.arend.error.ErrorReporter;
import org.arend.library.Library;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCClassReferable;
import org.arend.naming.reference.TCReferable;
import org.arend.naming.reference.converter.ReferableConverter;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.Group;
import org.arend.typechecking.CancellationIndicator;
import org.arend.typechecking.ThreadCancellationIndicator;
import org.arend.typechecking.TypecheckerState;
import org.arend.typechecking.error.CycleError;
import org.arend.typechecking.error.TerminationCheckError;
import org.arend.typechecking.error.local.LocalErrorReporter;
import org.arend.typechecking.error.local.TypecheckingError;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.arend.typechecking.instance.provider.InstanceProviderSet;
import org.arend.typechecking.order.Ordering;
import org.arend.typechecking.order.PartialComparator;
import org.arend.typechecking.order.SCC;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.typechecking.order.dependency.DummyDependencyListener;
import org.arend.typechecking.termination.DefinitionCallGraph;
import org.arend.typechecking.termination.RecursiveBehavior;
import org.arend.typechecking.typecheckable.TypecheckingUnit;
import org.arend.typechecking.typecheckable.provider.ConcreteProvider;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.DefinitionTypechecker;
import org.arend.typechecking.visitor.DesugarVisitor;
import org.arend.typechecking.visitor.FindDefCallVisitor;
import org.arend.util.ComputationInterruptedException;
import org.arend.util.Pair;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;

public class TypecheckingOrderingListener implements OrderingListener {
  private final TypecheckerState myState;
  private final DependencyListener myDependencyListener;
  private final Map<GlobalReferable, Pair<CheckTypeVisitor,Boolean>> mySuspensions = new HashMap<>();
  private final ErrorReporter myErrorReporter;
  private final InstanceProviderSet myInstanceProviderSet;
  private final ConcreteProvider myConcreteProvider;
  private final ReferableConverter myReferableConverter;
  private final PartialComparator<TCReferable> myComparator;
  private boolean myTypecheckingHeaders = false;
  private TCReferable myCurrentDefinition;
  private boolean myTypecheckingWithUse = true;

  private static CancellationIndicator CANCELLATION_INDICATOR = ThreadCancellationIndicator.INSTANCE;

  public TypecheckingOrderingListener(InstanceProviderSet instanceProviderSet, TypecheckerState state, ConcreteProvider concreteProvider, ReferableConverter referableConverter, ErrorReporter errorReporter, DependencyListener dependencyListener, PartialComparator<TCReferable> comparator) {
    myState = state;
    myErrorReporter = errorReporter;
    myDependencyListener = dependencyListener;
    myInstanceProviderSet = instanceProviderSet;
    myConcreteProvider = concreteProvider;
    myReferableConverter = referableConverter;
    myComparator = comparator;
  }

  public TypecheckingOrderingListener(InstanceProviderSet instanceProviderSet, TypecheckerState state, ConcreteProvider concreteProvider, ReferableConverter referableConverter, ErrorReporter errorReporter, PartialComparator<TCReferable> comparator) {
    this(instanceProviderSet, state, concreteProvider, referableConverter, errorReporter, DummyDependencyListener.INSTANCE, comparator);
  }

  public TypecheckingOrderingListener(Ordering ordering, ErrorReporter errorReporter) {
    myState = ordering.getTypecheckerState();
    myErrorReporter = errorReporter;
    myDependencyListener = ordering.getDependencyListener();
    myInstanceProviderSet = ordering.getInstanceProviderSet();
    myConcreteProvider = ordering.getConcreteProvider();
    myReferableConverter = ordering.getReferableConverter();
    myComparator = ordering.getComparator();
  }

  public static void checkCanceled() throws ComputationInterruptedException {
    CANCELLATION_INDICATOR.checkCanceled();
  }

  public static CancellationIndicator getCancellationIndicator() {
    return CANCELLATION_INDICATOR;
  }

  public static void resetCancellationIndicator() {
    CANCELLATION_INDICATOR = ThreadCancellationIndicator.INSTANCE;
  }

  public ConcreteProvider getConcreteProvider() {
    return myConcreteProvider;
  }

  public ReferableConverter getReferableConverter() {
    return myReferableConverter;
  }

  public boolean runTypechecking(CancellationIndicator cancellationIndicator, BooleanSupplier runnable) {
    synchronized (TypecheckingOrderingListener.class) {
      if (cancellationIndicator != null) {
        CANCELLATION_INDICATOR = cancellationIndicator;
      }

      try {
        return runnable.getAsBoolean();
      } catch (ComputationInterruptedException ignored) {
        if (myCurrentDefinition != null) {
          typecheckingInterrupted(myCurrentDefinition, myState.reset(myCurrentDefinition));
        }
        return false;
      } finally {
        if (cancellationIndicator != null) {
          CANCELLATION_INDICATOR = ThreadCancellationIndicator.INSTANCE;
        }
      }
    }
  }

  public boolean typecheckDefinitions(final Collection<? extends Concrete.Definition> definitions, CancellationIndicator cancellationIndicator) {
    return runTypechecking(cancellationIndicator, () -> {
      Ordering ordering = new Ordering(myInstanceProviderSet, myConcreteProvider, this, myDependencyListener, myReferableConverter, myState, myComparator);
      for (Concrete.Definition definition : definitions) {
        ordering.orderDefinition(definition);
      }
      return true;
    });
  }

  public boolean typecheckModules(final Collection<? extends Group> modules, CancellationIndicator cancellationIndicator) {
    return runTypechecking(cancellationIndicator, () -> {
      new Ordering(myInstanceProviderSet, myConcreteProvider, this, myDependencyListener, myReferableConverter, myState, myComparator).orderModules(modules);
      return true;
    });
  }

  public boolean typecheckLibrary(Library library, CancellationIndicator cancellationIndicator) {
    return runTypechecking(cancellationIndicator, () -> library.orderModules(new Ordering(myInstanceProviderSet, myConcreteProvider, this, myDependencyListener, myReferableConverter, myState, myComparator)));
  }

  public boolean typecheckLibrary(Library library) {
    return typecheckLibrary(library, null);
  }

  public boolean typecheckCollected(CollectingOrderingListener collector, CancellationIndicator cancellationIndicator) {
    return runTypechecking(cancellationIndicator, () -> {
      collector.feed(this);
      return true;
    });
  }

  public void typecheckingHeaderStarted(TCReferable definition) {

  }

  public void typecheckingBodyStarted(TCReferable definition) {

  }

  public void typecheckingUnitStarted(TCReferable definition) {

  }

  public void typecheckingHeaderFinished(TCReferable referable, Definition definition) {

  }

  public void typecheckingBodyFinished(TCReferable referable, Definition definition) {

  }

  public void typecheckingUnitFinished(TCReferable referable, Definition definition) {

  }

  public void typecheckingInterrupted(TCReferable definition, @Nullable Definition typechecked) {

  }

  private Definition newDefinition(Concrete.Definition definition) {
    Definition typechecked;
    if (definition instanceof Concrete.DataDefinition) {
      typechecked = new DataDefinition(definition.getData());
      ((DataDefinition) typechecked).setSort(Sort.SET0);
      typechecked.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
      for (Concrete.ConstructorClause constructorClause : ((Concrete.DataDefinition) definition).getConstructorClauses()) {
        for (Concrete.Constructor constructor : constructorClause.getConstructors()) {
          Constructor tcConstructor = new Constructor(constructor.getData(), (DataDefinition) typechecked);
          tcConstructor.setParameters(EmptyDependentLink.getInstance());
          tcConstructor.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
          ((DataDefinition) typechecked).addConstructor(tcConstructor);
          myState.record(constructor.getData(), tcConstructor);
        }
      }
    } else if (definition instanceof Concrete.FunctionDefinition) {
      typechecked = new FunctionDefinition(definition.getData());
      ((FunctionDefinition) typechecked).setResultType(new ErrorExpression(null, null));
      typechecked.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
    } else if (definition instanceof Concrete.ClassDefinition) {
      typechecked = new ClassDefinition((TCClassReferable) definition.getData());
      typechecked.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
      for (Concrete.ClassField field : ((Concrete.ClassDefinition) definition).getFields()) {
        ClassField classField = new ClassField(field.getData(), (ClassDefinition) typechecked);
        classField.setType(new PiExpression(Sort.PROP, new TypedSingleDependentLink(false, "this", new ClassCallExpression((ClassDefinition) typechecked, Sort.STD), true), new ErrorExpression(null, null)));
        classField.setStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
        ((ClassDefinition) typechecked).addPersonalField(classField);
        myState.record(classField.getReferable(), classField);
      }
    } else {
      throw new IllegalStateException();
    }
    typechecked.setStatus(Definition.TypeCheckingStatus.HEADER_NEEDS_TYPE_CHECKING);
    myState.record(definition.getData(), typechecked);
    return typechecked;
  }

  @Override
  public void sccFound(SCC scc) {
    if (myTypecheckingWithUse) {
      loop:
      for (TypecheckingUnit unit : scc.getDefinitions()) {
        if (unit.getDefinition() instanceof Concrete.FunctionDefinition && ((Concrete.FunctionDefinition) unit.getDefinition()).getKind().isUse()) {
          TCReferable useParent = ((Concrete.FunctionDefinition) unit.getDefinition()).getUseParent();
          for (TypecheckingUnit unit1 : scc.getDefinitions()) {
            if (unit1.getDefinition().getData().equals(useParent)) {
              myTypecheckingWithUse = false;
              break loop;
            }
          }
        }
      }

      if (!myTypecheckingWithUse) {
        Ordering ordering = new Ordering(myInstanceProviderSet, myConcreteProvider, this, myDependencyListener, myReferableConverter, myState, myComparator, false, false);
        for (TypecheckingUnit unit : scc.getDefinitions()) {
          ordering.orderDefinition(unit.getDefinition());
        }
        myTypecheckingWithUse = true;
        return;
      }
    }

    for (TypecheckingUnit unit : scc.getDefinitions()) {
      if (!TypecheckingUnit.hasHeader(unit.getDefinition())) {
        List<TCReferable> cycle = new ArrayList<>();
        for (TypecheckingUnit unit1 : scc.getDefinitions()) {
          Concrete.Definition definition = unit1.getDefinition();
          if (cycle.isEmpty() || cycle.get(cycle.size() - 1) != definition.getData()) {
            cycle.add(definition.getData());
          }

          Definition typechecked = myState.getTypechecked(definition.getData());
          if (typechecked == null) {
            typechecked = newDefinition(definition);
          }
          typechecked.addStatus(Definition.TypeCheckingStatus.HAS_ERRORS);

          typecheckingUnitStarted(definition.getData());
          if (TypecheckingUnit.hasHeader(definition)) {
            mySuspensions.remove(definition.getData());
          }
          typecheckingUnitFinished(definition.getData(), typechecked);
        }
        myErrorReporter.report(new CycleError(cycle));
        return;
      }
    }

    typecheckBodies(scc.getDefinitions(), typecheckHeaders(scc));
  }

  @Override
  public void definitionFound(Concrete.Definition definition, boolean isHeaderOnly, boolean isRecursive) {
    if (recursion == Recursion.IN_HEADER) {
      typecheckingUnitStarted(unit.getDefinition().getData());
      myErrorReporter.report(new CycleError(Collections.singletonList(unit.getDefinition().getData())));
      typecheckingUnitFinished(unit.getDefinition().getData(), newDefinition(unit.getDefinition()));
    } else {
      unit.getDefinition().setRecursive(recursion == Recursion.IN_BODY);
      typecheck(unit);
    }
  }

  private boolean typecheckHeaders(SCC scc) {
    int numberOfHeaders = 0;
    TypecheckingUnit unit = null;
    for (TypecheckingUnit unit1 : scc.getDefinitions()) {
      if (unit1.isHeader()) {
        unit = unit1;
        numberOfHeaders++;
      }
    }

    if (numberOfHeaders == 0) {
      return true;
    }

    if (numberOfHeaders == 1) {
      Concrete.Definition definition = unit.getDefinition();
      myCurrentDefinition = definition.getData();
      typecheckingHeaderStarted(myCurrentDefinition);

      CountingErrorReporter countingErrorReporter = new CountingErrorReporter();
      CheckTypeVisitor visitor = new CheckTypeVisitor(myState, new LinkedHashMap<>(), new LocalErrorReporter(definition.getData(), new CompositeErrorReporter(myErrorReporter, countingErrorReporter)), null);
      if (definition.hasErrors()) {
        visitor.setHasErrors();
      }
      DesugarVisitor.desugar(definition, myConcreteProvider, visitor.getErrorReporter());
      Definition oldTypechecked = visitor.getTypecheckingState().getTypechecked(definition.getData());
      definition.setRecursive(true);
      Definition typechecked = new DefinitionTypechecker(visitor).typecheckHeader(oldTypechecked, new GlobalInstancePool(myState, myInstanceProviderSet.get(definition.getData()), visitor), definition);
      if (typechecked.status() == Definition.TypeCheckingStatus.BODY_NEEDS_TYPE_CHECKING) {
        mySuspensions.put(definition.getData(), new Pair<>(visitor, oldTypechecked == null));
      }

      typecheckingHeaderFinished(definition.getData(), typechecked);
      myCurrentDefinition = null;
      return typechecked.status().headerIsOK();
    }

    if (myTypecheckingHeaders) {
      List<Concrete.Definition> cycle = new ArrayList<>(scc.getDefinitions().size());
      for (TypecheckingUnit unit1 : scc.getDefinitions()) {
        cycle.add(unit1.getDefinition());
      }

      for (Concrete.Definition definition : cycle) {
        typecheckingHeaderStarted(definition.getData());
        typecheckingHeaderFinished(definition.getData(), newDefinition(definition));
      }
      myErrorReporter.report(CycleError.fromConcrete(cycle));
      return false;
    }

    myTypecheckingHeaders = true;
    Ordering ordering = new Ordering(myInstanceProviderSet, myConcreteProvider, this, myDependencyListener, myReferableConverter, myState, myComparator, true, true);
    boolean ok = true;
    for (TypecheckingUnit unit1 : scc.getDefinitions()) {
      if (unit1.isHeader()) {
        Concrete.Definition definition = unit1.getDefinition();
        ordering.orderDefinition(definition);
        if (ok && !myState.getTypechecked(definition.getData()).status().headerIsOK()) {
          ok = false;
        }
      }
    }
    myTypecheckingHeaders = false;
    return ok;
  }

  private void typecheckBodies(Collection<? extends TypecheckingUnit> units, boolean headersAreOK) {
    Map<FunctionDefinition,Concrete.Definition> functionDefinitions = new HashMap<>();
    Map<FunctionDefinition, List<Clause>> clausesMap = new HashMap<>();
    Set<DataDefinition> dataDefinitions = new HashSet<>();
    List<Concrete.Definition> orderedDefinitions = new ArrayList<>(units.size());
    List<Concrete.Definition> otherDefs = new ArrayList<>();
    for (TypecheckingUnit unit : units) {
      Concrete.Definition definition = unit.getDefinition();
      Definition typechecked = myState.getTypechecked(definition.getData());
      if (typechecked instanceof DataDefinition) {
        dataDefinitions.add((DataDefinition) typechecked);
        orderedDefinitions.add(definition);
      } else {
        otherDefs.add(definition);
      }
    }
    orderedDefinitions.addAll(otherDefs);

    DefinitionTypechecker typechecking = new DefinitionTypechecker(null);
    for (Concrete.Definition definition : orderedDefinitions) {
      myCurrentDefinition = definition.getData();
      typecheckingBodyStarted(myCurrentDefinition);

      Definition def = myState.getTypechecked(definition.getData());
      Pair<CheckTypeVisitor, Boolean> pair = mySuspensions.remove(definition.getData());
      if (headersAreOK && pair != null) {
        typechecking.setTypechecker(pair.proj1);
        List<Clause> clauses = typechecking.typecheckBody(def, definition, dataDefinitions, pair.proj2);
        if (clauses != null) {
          functionDefinitions.put((FunctionDefinition) def, definition);
          clausesMap.put((FunctionDefinition) def, clauses);
        }
      }

      myCurrentDefinition = null;
    }

    if (!functionDefinitions.isEmpty()) {
      FindDefCallVisitor<DataDefinition> visitor = new FindDefCallVisitor<>(dataDefinitions, false);
      Iterator<Map.Entry<FunctionDefinition, Concrete.Definition>> it = functionDefinitions.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<FunctionDefinition, Concrete.Definition> entry = it.next();
        visitor.findDefinition(entry.getKey().getBody());
        Definition found = visitor.getFoundDefinition();
        if (found != null) {
          entry.getKey().setBody(null);
          entry.getKey().addStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
          myErrorReporter.report(new TypecheckingError("Mutually recursive function refers to data type '" + found.getName() + "'", entry.getValue()).withDefinition(entry.getKey().getReferable()));
          it.remove();
          visitor.clear();
        }
      }

      if (!functionDefinitions.isEmpty()) {
        checkRecursiveFunctions(functionDefinitions, clausesMap);
      }
    }

    for (Concrete.Definition definition : orderedDefinitions) {
      typecheckingBodyFinished(definition.getData(), myState.getTypechecked(definition.getData()));
    }
  }

  private void typecheck(TypecheckingUnit unit) {
    List<Clause> clauses;
    Definition typechecked;
    Concrete.Definition definition = unit.getDefinition();
    CheckTypeVisitor checkTypeVisitor = new CheckTypeVisitor(myState, new LinkedHashMap<>(), new LocalErrorReporter(definition.getData(), myErrorReporter), null);
    checkTypeVisitor.setInstancePool(new GlobalInstancePool(myState, myInstanceProviderSet.get(definition.getData()), checkTypeVisitor));
    DesugarVisitor.desugar(definition, myConcreteProvider, checkTypeVisitor.getErrorReporter());
    myCurrentDefinition = definition.getData();
    typecheckingUnitStarted(myCurrentDefinition);
    clauses = definition.accept(new DefinitionTypechecker(checkTypeVisitor), null);
    typechecked = myState.getTypechecked(myCurrentDefinition);

    if (definition.isRecursive() && typechecked instanceof FunctionDefinition && clauses != null) {
      checkRecursiveFunctions(Collections.singletonMap((FunctionDefinition) typechecked, definition), Collections.singletonMap((FunctionDefinition) typechecked, clauses));
    }

    typecheckingUnitFinished(definition.getData(), typechecked);
    myCurrentDefinition = null;
  }

  private void checkRecursiveFunctions(Map<FunctionDefinition,Concrete.Definition> definitions, Map<FunctionDefinition,List<Clause>> clauses) {
    DefinitionCallGraph definitionCallGraph = new DefinitionCallGraph();
    for (Map.Entry<FunctionDefinition, Concrete.Definition> entry : definitions.entrySet()) {
      List<Clause> functionClauses = clauses.get(entry.getKey());
      if (functionClauses != null) {
        definitionCallGraph.add(entry.getKey(), functionClauses, definitions.keySet());
      }
      for (DependentLink link = entry.getKey().getParameters(); link.hasNext(); link = link.getNext()) {
        link = link.getNextTyped(null);
        if (FindDefCallVisitor.findDefinition(link.getTypeExpr(), definitions.keySet()) != null) {
          myErrorReporter.report(new TypecheckingError("Mutually recursive functions are not allowed in parameters", entry.getValue()).withDefinition(entry.getKey().getReferable()));
        }
      }
    }

    DefinitionCallGraph callCategory = new DefinitionCallGraph(definitionCallGraph);
    if (!callCategory.checkTermination()) {
      for (FunctionDefinition definition : definitions.keySet()) {
        definition.addStatus(Definition.TypeCheckingStatus.HAS_ERRORS);
        definition.setBody(null);
      }
      for (Map.Entry<Definition, Set<RecursiveBehavior<Definition>>> entry : callCategory.myErrorInfo.entrySet()) {
        myErrorReporter.report(new TerminationCheckError(entry.getKey(), entry.getValue()));
      }
    }
  }
}
