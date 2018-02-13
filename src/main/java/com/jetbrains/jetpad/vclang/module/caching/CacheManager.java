package com.jetbrains.jetpad.vclang.module.caching;

import com.jetbrains.jetpad.vclang.core.definition.Definition;
import com.jetbrains.jetpad.vclang.module.caching.serialization.*;
import com.jetbrains.jetpad.vclang.module.source.SourceId;
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;
import com.jetbrains.jetpad.vclang.term.DefinitionLocator;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CacheManager<SourceIdT extends SourceId> {
  private final PersistenceProvider<SourceIdT> myPersistenceProvider;
  private final CacheStorageSupplier<SourceIdT> myCacheSupplier;
  private final SourceVersionTracker<SourceIdT> myVersionTracker;
  private final DefinitionLocator<SourceIdT> myDefLocator;

  private final LocalizedTypecheckerState<SourceIdT> myTcState;
  private final Set<SourceIdT> myStubsLoaded = new HashSet<>();

  public CacheManager(PersistenceProvider<SourceIdT> persistenceProvider, CacheStorageSupplier<SourceIdT> cacheSupplier,
                      DefinitionLocator<SourceIdT> defLocator, SourceVersionTracker<SourceIdT> versionTracker) {
    myPersistenceProvider = persistenceProvider;
    myCacheSupplier = cacheSupplier;
    myVersionTracker = versionTracker;
    myDefLocator = defLocator;
    myTcState = new LocalizedTypecheckerState<>(defLocator);
  }

  public TypecheckerState getTypecheckerState() {
    return myTcState;
  }

  public Set<SourceIdT> getCachedModules() {
    return myTcState.getCachedModules();
  }

  /**
   * Normally, {@link com.jetbrains.jetpad.vclang.module.caching.sourceless.CacheModuleScopeProvider} will call this
   * method, so you don't have to.
   */
  public boolean loadCache(@Nonnull SourceIdT sourceId) throws CacheLoadingException {
    if (myStubsLoaded.contains(sourceId)) return true;

    LocalizedTypecheckerState<SourceIdT>.LocalTypecheckerState localState = myTcState.getLocal(sourceId);

    InputStream cacheStream = myCacheSupplier.getCacheInputStream(sourceId);
    if (cacheStream == null) return false;

    try {
      try {
        GZIPInputStream compressedCacheStream = new GZIPInputStream(cacheStream);
        readModule(sourceId, localState, ModuleProtos.Module.parseFrom(compressedCacheStream));
        compressedCacheStream.close();
      } catch (IOException e) {
        throw new CacheLoadingException(sourceId, e);
      }
    } catch (CacheLoadingException e) {
      myStubsLoaded.remove(sourceId);
      // TODO: is this enough?
      myTcState.wipe(sourceId);
      throw e;
    }
    localState.sync();
    return true;
  }

  private void readModule(SourceIdT sourceId, LocalizedTypecheckerState<SourceIdT>.LocalTypecheckerState localState, ModuleProtos.Module moduleProto) throws CacheLoadingException {
    try {
      DefinitionDeserialization<SourceIdT> defStateDeserialization = new DefinitionDeserialization<>(sourceId, myPersistenceProvider);
      defStateDeserialization.readStubs(moduleProto.getDefinitionState(), localState);
      myStubsLoaded.add(sourceId);
      ReadCalltargets calltargets = new ReadCalltargets(sourceId, moduleProto.getReferredDefinitionList());
      defStateDeserialization.fillInDefinitions(moduleProto.getDefinitionState(), localState, calltargets);
    } catch (DeserializationError deserializationError) {
      throw new CacheLoadingException(sourceId, deserializationError);
    }
  }

  class ReadCalltargets implements CallTargetProvider {
    private final List<Definition> myCalltargets = new ArrayList<>();

    ReadCalltargets(SourceIdT sourceId, List<ModuleProtos.Module.DefinitionReference> refDefProtos) throws CacheLoadingException {
      for (ModuleProtos.Module.DefinitionReference proto : refDefProtos) {
        final SourceIdT targetSourceId;
        if (!proto.getSourceUrl().isEmpty()) {
          String moduleCacheId = proto.getSourceUrl();
          targetSourceId = myPersistenceProvider.getModuleId(moduleCacheId);
          if (targetSourceId == null) {
            throw new CacheLoadingException(sourceId, "Unresolvable module ID: " + moduleCacheId);
          }
          boolean targetLoaded = loadCache(targetSourceId);
          if (!targetLoaded) {
            throw new CacheLoadingException(sourceId, "Dependency does not support persistence: ");
          }
        } else {
          targetSourceId = sourceId;
        }
        GlobalReferable absDef = myPersistenceProvider.getFromId(targetSourceId, proto.getDefinitionId());
        Definition typechecked = myTcState.getLocal(targetSourceId).getTypechecked(absDef);
        if (typechecked == null) {
          throw new CacheLoadingException(sourceId, "Referred definition was not in cache");
        }
        myCalltargets.add(typechecked);
      }
    }

    @Override
    public Definition getCalltarget(int index) {
      return myCalltargets.get(index);
    }
  }


  /**
   * Persist cache for a source.
   * <p>
   * Also persists caches for all the dependencies of the requested source.
   * <p>
   * Definitions persisted are those and only those that were typechecked and can potentially be referred by compiled references.
   *
   * @param sourceId  ID of the source to persist cache of
   *
   * @return <code>true</code> if persisting succeeded;
   *         <code>false</code> otherwise.
   * @throws CachePersistenceException if an <code>IOException</code> occurs or if a dependency cannot be persisted.
   */
  public boolean persistCache(@Nonnull SourceIdT sourceId) throws CachePersistenceException {
    LocalizedTypecheckerState<SourceIdT>.LocalTypecheckerState localState = myTcState.getLocal(sourceId);
    if (!localState.isOutOfSync()) {
      return true;
    }

    OutputStream cacheStream = myCacheSupplier.getCacheOutputStream(sourceId);
    if (cacheStream == null) {
      return false;
    }

    try {
      try {
        GZIPOutputStream compressedCacheStream = new GZIPOutputStream(cacheStream);
        writeModule(sourceId, localState).writeTo(compressedCacheStream);
        compressedCacheStream.close();
      } catch (IOException e) {
        throw new CachePersistenceException(sourceId, e);
      }
    } catch (CachePersistenceException e) {
      localState.unsync();
      throw e;
    }
    return true;
  }

  private ModuleProtos.Module writeModule(SourceIdT sourceId, LocalizedTypecheckerState<SourceIdT>.LocalTypecheckerState localState) throws CachePersistenceException {
    ModuleProtos.Module.Builder out = ModuleProtos.Module.newBuilder();
    final WriteCallTargets calltargets = new WriteCallTargets(sourceId);
    // Serialize the module first in order to populate the call-target registry
    DefinitionSerialization defStateSerialization = new DefinitionSerialization(myPersistenceProvider, calltargets);
    out.setDefinitionState(defStateSerialization.writeDefinitionState(localState));
    localState.sync();
    // now write the call-target registry
    out.addAllReferredDefinition(calltargets.write());

    return out.build();
  }

  class WriteCallTargets implements CallTargetIndexProvider {
    private final LinkedHashMap<Definition, Integer> myCallTargets = new LinkedHashMap<>();
    private final SourceId mySourceId;

    WriteCallTargets(SourceId sourceId) {
      mySourceId = sourceId;
    }

    @Override
    public int getDefIndex(Definition definition) {
      return myCallTargets.computeIfAbsent(definition, k -> myCallTargets.size());
    }

    private List<ModuleProtos.Module.DefinitionReference> write() throws CachePersistenceException {
      List<ModuleProtos.Module.DefinitionReference> out = new ArrayList<>();
      for (Definition calltarget : myCallTargets.keySet()) {
        ModuleProtos.Module.DefinitionReference.Builder entry = ModuleProtos.Module.DefinitionReference.newBuilder();
        SourceIdT targetSourceId = myDefLocator.sourceOf(calltarget.getReferable());
        if (!mySourceId.equals(targetSourceId)) {
          boolean targetPersisted = persistCache(targetSourceId);
          if (!targetPersisted) {
            throw new CachePersistenceException(mySourceId, "Dependency does not support persistence " + targetSourceId);
          }
          entry.setSourceUrl(myPersistenceProvider.getCacheId(targetSourceId));
        }
        entry.setDefinitionId(myPersistenceProvider.getIdFor(calltarget.getReferable()));
        out.add(entry.build());
      }
      return out;
    }
  }
}
