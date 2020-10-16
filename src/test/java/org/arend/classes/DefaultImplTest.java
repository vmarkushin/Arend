package org.arend.classes;

import org.arend.Matchers;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class DefaultImplTest extends TypeCheckingTestCase {
  @Test
  public void defaultTest() {
    typeCheckModule(
      "\\record C\n" +
      "  | x : Nat\n" +
      "  | y : x = 0\n" +
      "\\record D \\extends C {\n" +
      "  \\default x => 0\n" +
      "}\n" +
      "\\func f => \\new D { | y => idp }\n" +
      "\\func g : D \\cowith\n" +
      "  | y => idp");
  }

  @Test
  public void redefineTest() {
    typeCheckModule(
      "\\record C\n" +
      "  | x : Nat\n" +
      "  | y : x = 1\n" +
      "\\record D \\extends C {\n" +
      "  \\default x => 0\n" +
      "}\n" +
      "\\func f => \\new D { | x => 1 | y => idp }\n" +
      "\\func g : D \\cowith\n" +
      "  | x => 1\n" +
      "  | y => idp\n" +
      "\\record E \\extends D\n" +
      "  | x => 1\n" +
      "  | y => idp");
  }

  @Test
  public void redefineDefaultTest() {
    typeCheckModule(
      "\\record C\n" +
      "  | x : Nat\n" +
      "  | y : x = 1\n" +
      "\\record D \\extends C {\n" +
      "  \\default x => 0\n" +
      "}\n" +
      "\\record E \\extends D {\n" +
      "  \\default x => 1\n" +
      "}\n" +
      "\\func f => \\new E { | y => idp }\n" +
      "\\func g : E \\cowith\n" +
      "  | y => idp");
  }

  @Test
  public void defaultAssumptionError() {
    typeCheckModule(
      "\\record C\n" +
      "  | x : Nat\n" +
      "  | y : x = 0\n" +
      "\\record D \\extends C {\n" +
      "  \\default x => 0\n" +
      "  \\default y => idp\n" +
      "}", 1);
    assertThatErrorsAre(Matchers.typeMismatchError());
  }
}
