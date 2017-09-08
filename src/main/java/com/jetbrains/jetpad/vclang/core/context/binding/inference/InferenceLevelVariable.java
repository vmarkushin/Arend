package com.jetbrains.jetpad.vclang.core.context.binding.inference;

import com.jetbrains.jetpad.vclang.core.context.binding.LevelVariable;
import com.jetbrains.jetpad.vclang.term.Concrete;

public class InferenceLevelVariable implements LevelVariable {
  private final LvlType myType;
  private final Concrete.SourceNode mySourceNode;

  public InferenceLevelVariable(LvlType type, Concrete.SourceNode sourceNode) {
    myType = type;
    mySourceNode = sourceNode;
  }

  @Override
  public LvlType getType() {
    return myType;
  }

  public Concrete.SourceNode getSourceNode() {
    return mySourceNode;
  }

  @Override
  public String toString() {
    return myType == LvlType.PLVL ? "?p" : "?h";
  }
}
