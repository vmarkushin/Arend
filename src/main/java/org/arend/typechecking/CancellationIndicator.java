package org.arend.typechecking;

import org.arend.util.ComputationInterruptedException;

public interface CancellationIndicator {
  boolean isCanceled();

  default void checkCanceled() throws ComputationInterruptedException {
    if (isCanceled()) {
      throw new ComputationInterruptedException();
    }
  }
}
