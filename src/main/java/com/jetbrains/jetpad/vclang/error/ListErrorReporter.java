package com.jetbrains.jetpad.vclang.error;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ListErrorReporter implements ErrorReporter {
  final private List<GeneralError> myErrorList;

  public ListErrorReporter() {
    myErrorList = new ArrayList<>();
  }

  public ListErrorReporter(List<GeneralError> errorList) {
    myErrorList = errorList;
  }

  @Override
  public void report(GeneralError error) {
    myErrorList.add(error);
  }

  public Collection<? extends GeneralError> getErrorList() {
    return myErrorList;
  }

  public void reportTo(ErrorReporter errorReporter) {
    for (GeneralError error : myErrorList) {
      errorReporter.report(error);
    }
  }
}
