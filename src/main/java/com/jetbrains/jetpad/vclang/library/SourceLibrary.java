package com.jetbrains.jetpad.vclang.library;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.module.ModulePath;
import com.jetbrains.jetpad.vclang.source.PersistableSource;
import com.jetbrains.jetpad.vclang.source.Source;
import com.jetbrains.jetpad.vclang.source.SourceLoader;
import com.jetbrains.jetpad.vclang.source.error.PersistingError;
import com.jetbrains.jetpad.vclang.term.group.Group;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState;
import com.jetbrains.jetpad.vclang.typechecking.Typechecking;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a library which can load modules in the binary format (see {@link #getBinarySource})
 * as well as ordinary modules (see {@link #getRawSource}).
 */
public abstract class SourceLibrary extends LibraryBase {
  public enum Flag { RECOMPILE, PARTIAL_LOAD }
  private final EnumSet<Flag> myFlags = EnumSet.noneOf(Flag.class);

  /**
   * Creates a new {@code SourceLibrary}
   *
   * @param typecheckerState  the underling typechecker state of this library.
   */
  protected SourceLibrary(TypecheckerState typecheckerState) {
    super(typecheckerState);
  }

  /**
   * Adds a flag.
   */
  public void addFlag(Flag flag) {
    myFlags.add(flag);
  }

  /**
   * Removes a flag.
   */
  public void removeFlag(Flag flag) {
    myFlags.remove(flag);
  }

  /**
   * Gets the raw source (that is, the source containing not typechecked data) for a given module path.
   *
   * @param modulePath  a path to the source.
   *
   * @return the raw source corresponding to the given path or null if the source is not found.
   */
  @Nullable
  public abstract Source getRawSource(ModulePath modulePath);

  /**
   * Gets the binary source (that is, the source containing typechecked data) for a given module path.
   *
   * @param modulePath  a path to the source.
   *
   * @return the binary source corresponding to the given path or null if the source is not found.
   */
  @Nullable
  public abstract PersistableSource getBinarySource(ModulePath modulePath);

  /**
   * Loads the header of this library.
   *
   * @param errorReporter a reporter for all errors that occur during the loading process.
   *
   * @return loaded library header, or null if some error occurred.
   */
  @Nullable
  protected abstract LibraryHeader loadHeader(ErrorReporter errorReporter);

  /**
   * Invoked by a source after it is loaded.
   *
   * @param modulePath  the path to the loaded module.
   * @param group       the group of the loaded module or null if the module failed to load.
   */
  public void onModuleLoaded(ModulePath modulePath, @Nullable Group group) {

  }

  @Override
  public boolean load(LibraryManager libraryManager) {
    if (isLoaded()) {
      return true;
    }

    LibraryHeader header = loadHeader(libraryManager.getErrorReporter());
    if (header == null) {
      return false;
    }

    for (LibraryDependency dependency : header.dependencies) {
      Library loadedDependency = libraryManager.loadLibrary(dependency.name);
      if (loadedDependency == null) {
        return false;
      }
      libraryManager.registerDependency(this, loadedDependency);
    }

    SourceLoader sourceLoader = new SourceLoader(this, libraryManager, myFlags.contains(Flag.RECOMPILE));
    for (ModulePath module : header.modules) {
      if (!sourceLoader.load(module) && !myFlags.contains(Flag.PARTIAL_LOAD)) {
        unload();
        return false;
      }
    }

    return super.load(libraryManager);
  }

  public Collection<? extends ModulePath> getUpdatedModules() {
    return Collections.emptyList();
  }

  @Override
  public boolean typecheck(Typechecking typechecking, ErrorReporter errorReporter) {
    Collection<? extends ModulePath> updatedModules = getUpdatedModules();
    List<Group> groups = new ArrayList<>(updatedModules.size());
    for (ModulePath module : updatedModules) {
      Group group = getModuleGroup(module);
      if (group != null) {
        groups.add(group);
      }
    }

    boolean result = typechecking.typecheckModules(groups);
    for (ModulePath updatedModule : updatedModules) {
      persistModule(updatedModule, errorReporter);
    }
    return result;
  }

  public boolean persistModule(ModulePath modulePath, ErrorReporter errorReporter) {
    PersistableSource source = getBinarySource(modulePath);
    if (source == null) {
      errorReporter.report(new PersistingError(modulePath));
      return false;
    } else {
      return source.persist(this, errorReporter);
    }
  }
}
