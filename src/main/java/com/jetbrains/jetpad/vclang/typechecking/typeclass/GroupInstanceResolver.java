package com.jetbrains.jetpad.vclang.typechecking.typeclass;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.naming.NameResolver;
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;
import com.jetbrains.jetpad.vclang.naming.resolving.GroupResolver;
import com.jetbrains.jetpad.vclang.naming.scope.Scope;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.provider.InstanceProviderSet;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.provider.SimpleInstanceProvider;

public class GroupInstanceResolver extends GroupResolver {
  private final InstanceProviderSet myInstanceProviderSet;

  public GroupInstanceResolver(NameResolver nameResolver, ErrorReporter errorReporter, InstanceProviderSet instanceProviderSet) {
    super(nameResolver, errorReporter);
    myInstanceProviderSet = instanceProviderSet;
  }

  @Override
  protected void processReferable(GlobalReferable referable, Scope scope) {
    myInstanceProviderSet.setProvider(referable, new SimpleInstanceProvider(scope));
  }
}
