package com.jetbrains.jetpad.vclang.editor;

import com.jetbrains.jetpad.vclang.model.Module;
import com.jetbrains.jetpad.vclang.model.definition.Definition;
import com.jetbrains.jetpad.vclang.model.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.UniverseExpression;
import com.jetbrains.jetpad.vclang.term.typechecking.TypeCheckingError;
import com.jetbrains.jetpad.vclang.term.visitor.CheckTypeVisitor;
import jetbrains.jetpad.cell.indent.IndentCell;
import jetbrains.jetpad.cell.trait.CellTrait;
import jetbrains.jetpad.cell.util.CellFactory;
import jetbrains.jetpad.event.Key;
import jetbrains.jetpad.event.KeyEvent;
import jetbrains.jetpad.event.ModifierKey;
import jetbrains.jetpad.mapper.Mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.jetbrains.jetpad.vclang.editor.Synchronizers.forDefinitions;

public class ModuleMapper extends Mapper<Module, ModuleMapper.Cell> {
  public ModuleMapper(Module source) {
    super(source, new Cell());

    getTarget().addTrait(new CellTrait() {
      @Override
      public void onKeyPressed(jetbrains.jetpad.cell.Cell cell, KeyEvent event) {
        if (event.is(Key.E, ModifierKey.CONTROL) || event.is(Key.E, ModifierKey.META)) {
          List<TypeCheckingError> errors = new ArrayList<>();
          for (Definition def : getSource().definitions) {
            if (def instanceof FunctionDefinition) {
              FunctionDefinition funDef = (FunctionDefinition) def;
              CheckTypeVisitor.Result typeResult = funDef.getResultType().accept(new CheckTypeVisitor(new HashMap<String, com.jetbrains.jetpad.vclang.term.definition.Definition>(), new ArrayList<com.jetbrains.jetpad.vclang.term.definition.Definition>(), errors), new UniverseExpression());
              funDef.getTerm().accept(new CheckTypeVisitor(new HashMap<String, com.jetbrains.jetpad.vclang.term.definition.Definition>(), new ArrayList<com.jetbrains.jetpad.vclang.term.definition.Definition>(), errors), typeResult == null ? null : typeResult.expression);
            }
          }
          event.consume();
        }
        super.onKeyPressed(cell, event);
      }
    });
  }

  @Override
  protected void registerSynchronizers(SynchronizersConfiguration conf) {
    super.registerSynchronizers(conf);
    conf.add(forDefinitions(this, getSource().definitions, getTarget().definitions));
  }

  public static class Cell extends IndentCell {
    public final IndentCell definitions = new IndentCell();

    public Cell() {
      CellFactory.to(this, definitions);
      focusable().set(true);
    }
  }
}
