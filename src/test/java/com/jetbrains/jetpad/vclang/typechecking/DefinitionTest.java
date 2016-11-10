package com.jetbrains.jetpad.vclang.typechecking;

import com.jetbrains.jetpad.vclang.term.Concrete;
import com.jetbrains.jetpad.vclang.term.context.LinkList;
import com.jetbrains.jetpad.vclang.term.context.binding.Binding;
import com.jetbrains.jetpad.vclang.term.context.binding.TypedBinding;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.definition.Constructor;
import com.jetbrains.jetpad.vclang.term.definition.DataDefinition;
import com.jetbrains.jetpad.vclang.term.definition.Definition;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.term.expr.subst.LevelArguments;
import com.jetbrains.jetpad.vclang.term.expr.type.Type;
import com.jetbrains.jetpad.vclang.term.expr.type.TypeMax;
import com.jetbrains.jetpad.vclang.term.expr.visitor.CheckTypeVisitor.Result;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.ConcreteExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class DefinitionTest extends TypeCheckingTestCase {
  @Test
  public void function() {
    FunctionDefinition typedDef = (FunctionDefinition) typeCheckDef("\\function f : Nat => 0");
    assertNotNull(typedDef);
    assertTrue(typedDef.hasErrors() == Definition.TypeCheckingStatus.NO_ERRORS);
  }

  @Test
  public void functionUntyped() {
    FunctionDefinition typedDef = (FunctionDefinition) typeCheckDef("\\function f => 0");
    assertNotNull(typedDef);
    assertTrue(typedDef.hasErrors() == Definition.TypeCheckingStatus.NO_ERRORS);
    assertEquals(Nat(), typedDef.getTypeWithParams(new ArrayList<DependentLink>(), new LevelArguments()));
  }

  @Test
  public void functionWithArgs() {
    FunctionDefinition typedDef = (FunctionDefinition) typeCheckDef("\\function f (x : Nat) (y : Nat -> Nat) => y");
    assertNotNull(typedDef);
    assertTrue(typedDef.hasErrors() == Definition.TypeCheckingStatus.NO_ERRORS);
    List<DependentLink> params = new ArrayList<>();
    TypeMax type = typedDef.getTypeWithParams(params, new LevelArguments());
    assertEquals(Pi(Nat(), Pi(Pi(Nat(), Nat()), Pi(Nat(), Nat()))), type.fromPiParameters(params));
  }

  @Test
  public void dataType() {
    DataDefinition typedDef = (DataDefinition) typeCheckDef("\\data D {A B : \\Type0} (I : A -> B -> \\Type0) (a : A) (b : B) | con1 (x : A) (I x b) | con2 {y : B} (I a y)");
    List<DependentLink> params = new ArrayList<>();
    TypeMax type = typedDef.getTypeWithParams(params, new LevelArguments());

    LinkList parameters = new LinkList();
    parameters.append(param(false, vars("A", "B"), Universe(0)));
    DependentLink A = parameters.getFirst();
    DependentLink B = A.getNext();
    parameters.append(param("I", Pi(Reference(A), Pi(Reference(B), Universe(0)))));
    DependentLink I = B.getNext();
    parameters.append(param("a", Reference(A)));
    parameters.append(param("b", Reference(B)));
    DependentLink a = I.getNext();
    DependentLink b = a.getNext();

    LinkList parameters1 = new LinkList();
    parameters1.append(param("x", Reference(A)));
    parameters1.append(param(Apps(Reference(I), Reference(parameters1.getFirst()), Reference(b))));

    LinkList parameters2 = new LinkList();
    parameters2.append(param(false, "y", Reference(B)));
    parameters2.append(param(Apps(Reference(I), Reference(a), Reference(parameters2.getFirst()))));

    assertNotNull(typedDef);
    assertTrue(typedDef.hasErrors() == Definition.TypeCheckingStatus.NO_ERRORS);
    assertEquals(Pi(parameters.getFirst(), Universe(0)), type.fromPiParameters(params).toExpression());
    assertEquals(2, typedDef.getConstructors().size());

    ExprSubstitution substitution = new ExprSubstitution();
    DependentLink link = typedDef.getParameters();
    substitution.add(link, Reference(A));
    link = link.getNext();
    substitution.add(link, Reference(B));
    link = link.getNext();
    substitution.add(link, Reference(I));
    link = link.getNext();
    substitution.add(link, Reference(a));
    link = link.getNext();
    substitution.add(link, Reference(b));
    List<DependentLink> con1Params = new ArrayList<>();
    Type con1Type = typedDef.getConstructors().get(0).getTypeWithParams(con1Params, new LevelArguments());
    assertEquals(Pi(parameters.getFirst(), Pi(parameters1.getFirst(), DataCall(typedDef, new LevelArguments(),
        Reference(A),
        Reference(B),
        Reference(I),
        Reference(a),
        Reference(b)))), con1Type.fromPiParameters(con1Params));
    List<DependentLink> con2Params = new ArrayList<>();
    Type con2Type = typedDef.getConstructors().get(1).getTypeWithParams(con2Params, new LevelArguments());
    assertEquals(Pi(parameters.getFirst(), Pi(parameters2.getFirst(), DataCall(typedDef, new LevelArguments(),
        Reference(A),
        Reference(B),
        Reference(I),
        Reference(a),
        Reference(b)))), con2Type.fromPiParameters(con2Params));
  }

  @Test
  public void dataType2() {
    DataDefinition typedDef = (DataDefinition) typeCheckDef("\\data D (A : \\7-Type2) | con1 (X : \\1-Type5) X | con2 (Y : \\2-Type3) A Y");
    DependentLink A = typedDef.getParameters();
    List<DependentLink> params = new ArrayList<>();
    TypeMax type = typedDef.getTypeWithParams(params, new LevelArguments());
    List<DependentLink> con1Params = new ArrayList<>();
    Type con1Type = typedDef.getConstructors().get(0).getTypeWithParams(con1Params, new LevelArguments());
    List<DependentLink> con2Params = new ArrayList<>();
    Type con2Type = typedDef.getConstructors().get(1).getTypeWithParams(con2Params, new LevelArguments());

    LinkList parameters1 = new LinkList();
    parameters1.append(param("X", Universe(5, 1)));
    parameters1.append(param(Reference(parameters1.getFirst())));

    LinkList parameters2 = new LinkList();
    parameters2.append(param("Y", Universe(3, 2)));
    parameters2.append(param(Reference(A)));
    parameters2.append(param(Reference(parameters2.getFirst())));

    assertNotNull(typedDef);
    assertTrue(typedDef.hasErrors() == Definition.TypeCheckingStatus.NO_ERRORS);
    assertEquals(Pi(A, Universe(6, 7)), type.fromPiParameters(params).toExpression());
    assertEquals(2, typedDef.getConstructors().size());

    assertEquals(Pi(A, Pi(parameters1.getFirst(), DataCall(typedDef, new LevelArguments(), Reference(A)))), con1Type.fromPiParameters(con1Params));
    assertEquals(Pi(A, Pi(parameters2.getFirst(), DataCall(typedDef, new LevelArguments(), Reference(A)))), con2Type.fromPiParameters(con2Params));
  }

  /* Not valid test anymore since we don't have explicit universes for data
  @Test
  public void dataExplicitUniverse() {
    typeCheckDef("\\data Either {lp : Lvl} {lh : CNat} (A B : \\Type (lp,lh)) : \\Type (lp, max lh 1)\n" +
            "    | inl A\n" +
            "    | inr B");
  } /**/

  @Test
  public void constructor() {
    DataDefinition def = (DataDefinition) typeCheckDef("\\data D (A : \\Type0) | con (B : \\Type1) A B");

    Constructor con = def.getConstructor("con");
    Concrete.Expression expr = cApps(cDefCall(null, con), cNat(), cZero(), cZero());

    Result result = typeCheckExpr(expr, null);
    assertThat(result.getType(), is((TypeMax) DataCall(def, new LevelArguments(), Nat())));
  }

  @Test
  public void constructorInfer() {
    DataDefinition def = (DataDefinition) typeCheckDef("\\data D (A : \\Type0) | con (B : \\Type1) A B");

    Constructor con = def.getConstructor("con");
    Concrete.Expression expr = cApps(cVar("f"), cApps(cDefCall(null, con), cNat(), cLam("x", cVar("x")), cZero()));
    List<Binding> localContext = new ArrayList<>(1);
    localContext.add(new TypedBinding("f", Pi(DataCall(def, new LevelArguments(), Pi(Nat(), Nat())), Nat())));

    Result result = typeCheckExpr(localContext, expr, null);
    assertThat(result.getType(), is((TypeMax) Nat()));
  }

  @Test
  public void constructorConst() {
    DataDefinition def = (DataDefinition) typeCheckDef("\\data D (A : \\Type0) | con A");

    Constructor con = def.getConstructor("con");
    Concrete.Expression expr = cApps(cVar("f"), cDefCall(null, con));
    List<Binding> localContext = new ArrayList<>(1);
    localContext.add(new TypedBinding("f", Pi(Pi(Nat(), DataCall(def, new LevelArguments(), Nat())), Pi(Nat(), Nat()))));

    Result result = typeCheckExpr(localContext, expr, null);
    assertThat(result.getType(), is((TypeMax) Pi(Nat(), Nat())));
  }

  @Test
  public void errorInParameters() {
    typeCheckClass(
        "\\data E (n : Nat) | e\n" +
        "\\data D (n : Nat -> Nat) (E n) | d\n" +
        "\\function test => D", 2);
  }

  @Test
  public void errorInParametersCon() {
    typeCheckClass(
        "\\data E (n : Nat) | e\n" +
        "\\data D (n : Nat -> Nat) (E n) | d\n" +
        "\\function test => d", 2);
  }

  @Test
  public void patternVector() {
    typeCheckDef("\\data Vec \\Type0 Nat | Vec _ zero => Nil | Vec A (suc m) => Cons A (Vec A m)");
  }

  @Test
  public void patternDepParams() {
    typeCheckClass(
        "\\data D (n : Nat) (n = n) | D zero _ => d\n" +
        "\\data C {n : Nat} {p : n = n} (D n p) | C {zero} {p} d => c (p = p)");
  }

  @Test
  public void patternDepParamsError() {
    typeCheckClass(
        "\\data D (n : Nat) (n = n) | D zero _ => d\n" +
        "\\data C {n : Nat} {p : n = n} (D n p) | C {_} {p} d => c (p = p)", 1);
  }

  @Test
  public void patternNested() {
    typeCheckDef("\\data C (n : Nat) | C (suc (suc n)) => c2 (n = n)");
  }

  @Test
  public void patternDataLE() {
    typeCheckDef("\\data LE (n m : Nat) | LE zero m => LE-zero | LE (suc n) (suc m) => LE-suc (LE n m)");
  }

  @Test
  public void patternImplicitError() {
    typeCheckDef("\\data D (A : Nat) | D {A} => d", 1);
  }

  @Test
  public void patternConstructorCall() {
    typeCheckClass(
        "\\data D {n : Nat} | D {zero} => d\n" +
        "\\function test => d");
  }

  @Test
  public void patternAbstract() {
    typeCheckClass(
        "\\data Wheel | wheel\n" +
        "\\data VehicleType | bikeType | carType\n" +
        "\\data Vehicle (t : VehicleType)\n" +
        "  | Vehicle (carType) => car Wheel Wheel Wheel Wheel" +
        "  | Vehicle (bikeType) => bike Wheel Wheel");
  }

  @Test
  public void patternUnknownConstructorError() {
    typeCheckDef("\\data D (n : Nat) | D (suc (luc m)) => d", 1);
  }

  @Test
  public void patternLift() {
    typeCheckClass(
        "\\data D (n : Nat) | D (zero) => d\n" +
        "\\data C (m : Nat) (n : Nat) (D m) | C (zero) (zero) (d) => c");
  }

  @Test
  public void patternLiftError() {
    typeCheckClass(
        "\\data D (n : Nat) | D (zero) => d\n" +
        "\\data C (m : Nat) (n : Nat) (D m) | C _ (zero) (d) => c", 1);
  }

  @Test
  public void patternMultipleSubst() {
    typeCheckClass(
        "\\data D (n : Nat) (m : Nat) | d (n = n) (m = m)\n" +
        "\\data C | c (n m : Nat) (D n m)\n" +
        "\\data E C | E (c (zero) (suc (zero)) (d _ _)) => e\n" +
        "\\function test => (E (c 0 1 (d (path (\\lam _ => 0)) (path (\\lam _ => 1))))).e");
  }

  @Test
  public void patternConstructorDefCall() {
    typeCheckClass(
        "\\data D (n : Nat) (m : Nat) | D (suc n) (suc m) => d (n = n) (m = m)\n" +
        "\\function test => d (path (\\lam _ => 1)) (path (\\lam _ => 0))");
  }

  @Test
  public void patternConstructorDefCallError() {
    typeCheckClass(
        "\\data D (n : Nat) | D (zero) => d\n" +
        "\\function test (n : Nat) : D n => d", 1);
  }

  @Test
  public void patternSubstTest() {
    typeCheckClass(
        "\\data E (n : Nat) | E (zero) => e\n" +
        "\\data D (n : Nat) (E n) | D (zero) (e) => d\n" +
        "\\function test => d");
  }

  @Test
  public void patternExpandArgsTest() {
    typeCheckClass(
        "\\data D (n : Nat) | d (n = n)\n" +
        "\\data C (D 1) | C (d p) => c\n" +
        "\\function test : C (d (path (\\lam _ => 1))) => c");
  }

  @Test
  public void patternNormalizeTest() {
    typeCheckClass(
        "\\data E (x : 0 = 0) | e\n" +
        "\\data C (n m : Nat) | C (suc n) (suc (suc n)) => c (n = n)\n" +
        "\\data D ((\\lam (x : \\Type0) => x) (C 1 2)) | D (c p) => x (E p)\n" +
        "\\function test => x (E (path (\\lam _ => 0))).e");
  }

  @Test
  public void patternNormalizeTest1() {
    typeCheckClass(
        "\\data E (x : 0 = 0) | e\n" +
        "\\data C (n m : Nat) | C (suc n) (suc (suc n)) => c (n = n)\n" +
        "\\data D ((\\lam (x : \\Type0) => x) (C 1 1)) | D (c p) => x (E p)", 1);
  }

  @Test
  public void patternTypeCheck() {
    typeCheckClass(
        "\\function f (x : Nat -> Nat) => x 0\n" +
        "\\data Test (A : \\Set0)\n" +
        "  | Test (suc n) => foo (f n)", 1);
  }


  @Test
  public void constructorTest() {
    typeCheckClass(
        "\\data D (n : Nat) (f : Nat -> Nat) | con1 (f n = n) | con2 (f 0 = n)\n" +
        "\\function f (x : Nat) : D x (\\lam y => y) => con1 (path (\\lam _ => x))\n" +
        "\\function g : D 0 (\\lam y => y) => con2 (path (\\lam _ => 0))");
  }

  @Test
  public void indexedWithConditionsError() {
    typeCheckClass(
        "\\data S | base | loop I \\with | loop right => base | loop left => base\n" +
        "\\data Q S | Q (base) => cq", 1);
  }
}
