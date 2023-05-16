/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.ListUtils.OneAndOnlyOne.MANY;
import static io.harness.data.structure.ListUtils.OneAndOnlyOne.NONE;
import static io.harness.data.structure.ListUtils.oneAndOnlyOne;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.IndexManagerCollectionSession.createCollectionSession;
import static io.harness.mongo.IndexManagerSession.Type.NORMAL_INDEX;
import static io.harness.mongo.IndexManagerSession.Type.SPARSE_INDEX;
import static io.harness.mongo.IndexManagerSession.Type.UNIQUE_INDEX;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.util.stream.Collectors.toList;

import io.harness.annotation.IgnoreUnusedIndex;
import io.harness.annotations.SecondaryStoreIn;
import io.harness.annotations.StoreIn;
import io.harness.annotations.StoreInMultiple;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.ListUtils.OneAndOnlyOne;
import io.harness.govern.Switch;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.IndexManager.Mode;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdSparseIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.migrator.Migrator;
import io.harness.mongo.log.CollectionLogContext;
import io.harness.ng.DbAliases;
import io.harness.persistence.store.Store;
import io.harness.threading.Morpheus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mongodb.AggregationOptions;
import com.mongodb.BasicDBObject;
import com.mongodb.Cursor;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoCommandException;
import com.mongodb.ReadPreference;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.MappedField;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class IndexManagerSession {
  public static final String UNIQUE = "unique";
  public static final String BACKGROUND = "background";
  public static final String SPARSE = "sparse";
  public static final String NAME = "name";
  public static final String EXPIRE_AFTER_SECONDS = "expireAfterSeconds";
  public static final Duration SOAKING_PERIOD = ofDays(1);
  // We do not want to drop temporary index before we created the new one. Make the soaking period smaller.
  public static final Duration REBUILD_SOAKING_PERIOD = SOAKING_PERIOD.minus(ofHours(1));
  public static final List<String> exceptionCollections =
      ImmutableList.of("instance", "verificationServiceConfigurations", "artifactStream", "infrastructureMapping",
          "entityVersions", "commands", "environments", "configFiles", "serviceTemplates", "pipelines", "triggers",
          "applicationManifests", "services", "workflows", "syncStatus");

  private Map<String, Migrator> migrators;
  private AdvancedDatastore datastore;
  private Mode mode;

  @Value
  @AllArgsConstructor
  static class Accesses {
    int operations;
    Date since;
  }

  private static Map<String, Accesses> extractAccesses(Cursor cursor) {
    Map<String, Accesses> accessesMap = new HashMap<>();

    while (cursor.hasNext()) {
      BasicDBObject object = (BasicDBObject) cursor.next();

      String name = (String) object.get(NAME);
      BasicDBObject accessesObject = (BasicDBObject) object.get("accesses");

      Accesses accesses =
          new Accesses(((Long) accessesObject.get("ops")).intValue(), (Date) accessesObject.get("since"));

      accessesMap.put(name, accesses);
    }
    return accessesMap;
  }

  private static Map<String, Accesses> mergeAccesses(
      Map<String, Accesses> accessesPrimary, Map<String, Accesses> accessesSecondary) {
    Map<String, Accesses> accessesMap = new HashMap<>();

    for (Entry<String, Accesses> entry : accessesPrimary.entrySet()) {
      String index = entry.getKey();
      Accesses primary = entry.getValue();
      Accesses secondary = accessesSecondary.get(entry.getKey());
      if (secondary == null) {
        accessesMap.put(index, primary);
      } else {
        accessesMap.put(index,
            new Accesses(primary.getOperations() + secondary.getOperations(),
                primary.getSince().before(secondary.getSince()) ? primary.getSince() : secondary.getSince()));
      }
    }

    for (Entry<String, Accesses> entry : accessesSecondary.entrySet()) {
      accessesMap.putIfAbsent(entry.getKey(), entry.getValue());
    }

    return accessesMap;
  }

  public static Map<String, Accesses> fetchIndexAccesses(DBCollection collection) {
    Map<String, Accesses> accessesPrimary = extractAccesses(
        collection.aggregate(Arrays.<DBObject>asList(new BasicDBObject("$indexStats", new BasicDBObject())),
            AggregationOptions.builder().build(), ReadPreference.primary()));
    Map<String, Accesses> accessesSecondary = extractAccesses(
        collection.aggregate(Arrays.<DBObject>asList(new BasicDBObject("$indexStats", new BasicDBObject())),
            AggregationOptions.builder().build(), ReadPreference.secondary()));

    return mergeAccesses(accessesPrimary, accessesSecondary);
  }

  interface IndexesProcessor {
    void process(MappedClass mc, DBCollection collection);
  }

  public static Set<String> processIndexes(
      AdvancedDatastore datastore, Morphia morphia, Store store, IndexesProcessor processor) {
    return processIndexesInternal(datastore, morphia, store, processor);
  }

  private static Set<String> processIndexesInternal(
      AdvancedDatastore datastore, Morphia morphia, Store store, IndexesProcessor processor) {
    Set<String> processedCollections = new HashSet<>();

    Collection<MappedClass> mappedClasses = morphia.getMapper().getMappedClasses();

    mappedClasses.forEach(mc -> {
      Entity entity = mc.getEntityAnnotation();
      if (entity == null) {
        return;
      }

      DBCollection collection = datastore.getCollection(mc.getClazz());

      Set<String> storeInSet = new HashSet<>();
      addStoreInInSet(mc, storeInSet);
      if (isNotEmpty(storeInSet)) {
        if (!storeInSet.contains(DbAliases.ALL) && (store == null || !storeInSet.contains(store.getName()))) {
          return;
        }
      }
      try (AutoLogContext ignore = new CollectionLogContext(collection.getName(), OVERRIDE_ERROR)) {
        if (processedCollections.contains(collection.getName())) {
          return;
        }
        processedCollections.add(collection.getName());

        if (entity.noClassnameStored() && !Modifier.isFinal(mc.getClazz().getModifiers())) {
          log.warn(
              "No class store collection {} with not final class {}", collection.getName(), mc.getClazz().getName());
        }
        if (!entity.noClassnameStored() && Modifier.isFinal(mc.getClazz().getModifiers())) {
          log.warn("Class store collection {} with final class {}", collection.getName(), mc.getClazz().getName());
        }

        processor.process(mc, collection);
      }
    });

    return processedCollections;
  }

  private static void addStoreInInSet(MappedClass mc, Set<String> storeInSet) {
    final StoreIn storeIn = mc.getClazz().getAnnotation(StoreIn.class);
    final StoreInMultiple storeInMultiple = mc.getClazz().getAnnotation(StoreInMultiple.class);
    final SecondaryStoreIn secondaryStoreIn = mc.getClazz().getAnnotation(SecondaryStoreIn.class);
    if (storeIn != null) {
      storeInSet.add(storeIn.value());
    }
    if (storeInMultiple != null) {
      storeInSet.addAll(
          emptyIfNull(Arrays.stream(storeInMultiple.value()).map(StoreIn::value).collect(Collectors.toList())));
    }
    if (secondaryStoreIn != null) {
      storeInSet.add(secondaryStoreIn.value());
    }
  }

  public static List<IndexCreator> allIndexes(AdvancedDatastore datastore, Morphia morphia, Store store) {
    List<IndexCreator> result = new ArrayList<>();
    processIndexes(
        datastore, morphia, store, (mc, collection) -> result.addAll(indexCreators(mc, collection).values()));
    return result;
  }

  static Date tooNew() {
    return new Date(currentTimeMillis() - SOAKING_PERIOD.toMillis());
  }

  public static Map<String, IndexCreator> indexCreators(MappedClass mc, DBCollection collection) {
    Map<String, IndexCreator> creators = new HashMap<>();

    try {
      Method indexes = mc.getClazz().getMethod("mongoIndexes");
      List<MongoIndex> mongoIndexList = (List<MongoIndex>) indexes.invoke(null);
      String id = indexedFieldName(mc);
      for (MongoIndex mongoIndex : mongoIndexList) {
        IndexCreator creator = mongoIndex.createBuilder(id).collection(collection).build();
        checkWithTheOthers(creators, creator);
        putCreator(creators, creator);
      }
    } catch (NoSuchMethodException exception) {
      ignoredOnPurpose(exception);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      log.error("", exception);
    }

    creatorsForFieldIndexes(mc, collection, creators);
    return creators;
  }

  private static void creatorsForFieldIndexes(
      MappedClass mc, DBCollection collection, Map<String, IndexCreator> creators) {
    // Read field level "Indexed" annotation
    for (MappedField mf : mc.getPersistenceFields()) {
      FdIndex fdIndex = mf.getField().getAnnotation(FdIndex.class);
      FdUniqueIndex fdUniqueIndex = mf.getField().getAnnotation(FdUniqueIndex.class);
      FdSparseIndex fdSparseIndex = mf.getField().getAnnotation(FdSparseIndex.class);
      FdTtlIndex fdTtlIndex = mf.getField().getAnnotation(FdTtlIndex.class);
      OneAndOnlyOne oneAndOnlyOne = oneAndOnlyOne(fdIndex, fdUniqueIndex, fdSparseIndex, fdTtlIndex);
      if (oneAndOnlyOne == NONE) {
        continue;
      } else if (oneAndOnlyOne == MANY) {
        throw new IndexManagerInspectException("Only one field index can be used");
      }

      String name = mf.getNameToStore();
      String indexName = name + "_1";
      BasicDBObject dbObject = new BasicDBObject(name, 1);

      Type type = NORMAL_INDEX;
      if (fdUniqueIndex != null) {
        type = UNIQUE_INDEX;
      } else if (fdSparseIndex != null) {
        type = SPARSE_INDEX;
      }

      BasicDBObject options = indexOptions(indexName, type, null);
      if (fdTtlIndex != null) {
        options.put(EXPIRE_AFTER_SECONDS, fdTtlIndex.value());
      }
      IndexCreator creator = IndexCreator.builder().collection(collection).keys(dbObject).options(options).build();
      checkWithTheOthers(creators, creator);
      putCreator(creators, creator);
    }
  }

  private static String indexedFieldName(MappedClass mc) {
    for (MappedField mf : mc.getPersistenceFields()) {
      if (mf.hasAnnotation(Id.class)) {
        return mf.getJavaFieldName();
      }
    }
    throw new IndexManagerInspectException();
  }

  enum Type { NORMAL_INDEX, UNIQUE_INDEX, SPARSE_INDEX }

  @SneakyThrows
  private static BasicDBObject indexOptions(String indexName, Type type, Annotation index) {
    BasicDBObject options = new BasicDBObject();
    options.put(NAME, indexName);
    if (type == UNIQUE_INDEX) {
      options.put(UNIQUE, Boolean.TRUE);
    } else {
      options.put(BACKGROUND, Boolean.TRUE);
    }
    if (type == SPARSE_INDEX) {
      options.put(SPARSE, Boolean.TRUE);
    }
    return options;
  }

  private static void putCreator(Map<String, IndexCreator> creators, IndexCreator newCreator) {
    String indexName = newCreator.name();
    creators.merge(indexName, newCreator, (old, current) -> {
      log.error(
          "Indexes {} and {} have the same name {}", current.getKeys().toString(), old.getKeys().toString(), indexName);
      throw new IndexManagerInspectException();
    });
  }

  private static void checkWithTheOthers(Map<String, IndexCreator> creators, IndexCreator newCreator) {
    if (exceptionCollections.contains(newCreator.getCollection().getName())
        && newCreator.getOptions().toString().equals("{\"name\": \"appId_1\", \"background\": true}")) {
      return;
    }
    for (IndexCreator creator : creators.values()) {
      if (creator.sameKeysOrderAndValues(newCreator.getKeys())) {
        throw new Error(format("Index %s and %s have the same keys and values", newCreator.getOptions().toString(),
            creator.getOptions().toString()));
      }

      if (creator.isSubsequence(newCreator)) {
        log.error("Index {} is a subsequence of index {}", newCreator.getOptions().toString(),
            creator.getOptions().toString());
      }
      if (newCreator.isSubsequence(creator)) {
        log.error("Index {} is a subsequence of index {}", creator.getOptions().toString(),
            newCreator.getOptions().toString());
      }
    }
  }

  IndexManagerSession(AdvancedDatastore datastore, Map<String, Migrator> migrators, Mode mode) {
    this.datastore = datastore;
    this.migrators = migrators;
    this.mode = mode;
  }

  private static final AtomicInteger step = new AtomicInteger(0);

  public void create(IndexCreator indexCreator) {
    if (migrators != null) {
      String migratorKey = indexCreator.getCollection().getName() + "."
          + (indexCreator.getOriginalName() == null ? indexCreator.name() : indexCreator.getOriginalName());

      Migrator migrator = migrators.get(migratorKey);
      if (migrator != null) {
        log.info("Execute migration {} for index {}", migrator.getClass().getName(), indexCreator.name());
        migrator.execute(datastore);
      }
    }

    switch (mode) {
      case AUTO:
        log.warn("Creating index {} {}", indexCreator.getOptions().toString(), indexCreator.getKeys().toString());
        for (int i = 0; i < 10; i++) {
          try {
            indexCreator.getCollection().createIndex(indexCreator.getKeys(), indexCreator.getOptions());
            break;
          } catch (MongoCommandException exception) {
            // Creating too many indexes at the same time might overwhelm mongo. Give it some time to catch up.
            if (exception.getErrorCode() == 24) {
              Morpheus.sleep(Duration.ofSeconds(1));
            } else {
              throw exception;
            }
          }
        }
        break;
      case MANUAL:
        log.info("{}. Should create index {} {}\n{}", step.incrementAndGet(), indexCreator.getOptions().toString(),
            indexCreator.getKeys().toString());
        break;
      case INSPECT:
        log.error("{}. Should create index {}\nScript: db.{}.createIndex({}, {})", step.incrementAndGet(),
            indexCreator.getOptions().get(NAME), indexCreator.getCollection().getName(),
            indexCreator.getKeys().toString(), indexCreator.getOptions().toString());
        break;
      default:
        Switch.unhandled(mode);
    }
  }

  public void dropIndex(DBCollection collection, String indexName) {
    switch (mode) {
      case AUTO:
        log.warn("Dropping index {}", indexName);
        collection.dropIndex(indexName);
        break;
      case MANUAL:
        log.info("{}. Should drop index {}", step.incrementAndGet(), indexName);
        break;
      case INSPECT:
        log.error("{}. Should drop index {}\nScript: db.{}.dropIndex('{}')", step.incrementAndGet(), indexName,
            collection.getName(), indexName);
        break;
      default:
        Switch.unhandled(mode);
    }
  }

  public boolean rebuildIndex(
      IndexManagerCollectionSession collectionSession, IndexCreator indexCreator, Duration waitFor) {
    if (!collectionSession.isRebuildNeeded(indexCreator)) {
      return false;
    }

    // Mongo does not support index updates - we have to recreate the index

    // We do not want to have to make queries without an index. We cannot create another one though, because mongo
    // will detect that we already have such with the same set of fields.
    // We are going to add an extra dummy field. this is safe because fields at the end will not prevent the index to
    // be used for other purposes.
    BasicDBObject tempKeys = (BasicDBObject) indexCreator.getKeys().copy();
    tempKeys.put("very_dummy_field", 1);

    // We also have to pick another name
    BasicDBObject tempOptions = (BasicDBObject) indexCreator.getOptions().copy();
    tempOptions.put(NAME, "TRI_" + currentTimeMillis());

    IndexCreator tempCreator = IndexCreator.builder()
                                   .originalName(indexCreator.name())
                                   .collection(indexCreator.getCollection())
                                   .keys(tempKeys)
                                   .options(tempOptions)
                                   .build();

    // Lets see if we already have this one created from before
    DBObject triIndex = collectionSession.findIndexByFieldsAndDirection(tempCreator);
    if (triIndex == null) {
      create(tempCreator);
      collectionSession.reset(tempCreator.getCollection());
      triIndex = collectionSession.findIndexByFieldsAndDirection(tempCreator);
      if (triIndex == null) {
        return true;
      }
    }

    // Check if the temporary index soaked enough
    long indexTime = Long.parseLong(triIndex.get(NAME).toString().substring(4));
    if (indexTime + waitFor.toMillis() > currentTimeMillis()) {
      return false;
    }

    // now it is safe to drop the colliding indexes
    DBObject indexByFields = collectionSession.findIndexByFields(indexCreator);
    if (indexByFields != null) {
      String currentName = (String) indexByFields.get(NAME);
      dropIndex(indexCreator.getCollection(), currentName);
    }

    String currentName = (String) indexCreator.getOptions().get(NAME);
    DBObject indexByName = collectionSession.findIndexByName(currentName);
    if (indexByName != null && indexByName != indexByFields) {
      dropIndex(indexCreator.getCollection(), currentName);
    }

    // Lets create the target index
    create(indexCreator);
    return true;
  }

  int createNewIndexes(IndexManagerCollectionSession collectionSession, Map<String, IndexCreator> creators) {
    int created = 0;
    for (Entry<String, IndexCreator> creator : creators.entrySet()) {
      String name = creator.getKey();
      try (AutoLogContext ignore = new IndexLogContext(name, OVERRIDE_ERROR)) {
        IndexCreator indexCreator = creator.getValue();
        try {
          if (collectionSession.isRebuildNeeded(indexCreator)) {
            if (rebuildIndex(collectionSession, indexCreator, REBUILD_SOAKING_PERIOD)) {
              created++;
            }
          } else if (collectionSession.isCreateNeeded(indexCreator)) {
            create(indexCreator);
            created++;
          }
        } catch (MongoCommandException mex) {
          // 86 - Index must have unique name.
          if (mex.getErrorCode() == 85 || mex.getErrorCode() == 86) {
            if (rebuildIndex(collectionSession, indexCreator, REBUILD_SOAKING_PERIOD)) {
              created++;
            }
          } else {
            log.error("Failed to create index", mex);
          }
        }
      } catch (DuplicateKeyException exception) {
        log.error("Because of deployment, a new index with uniqueness flag was introduced. "
                + "Current data does not meet this expectation."
                + "Create a migration to align the data with expectation or delete the uniqueness criteria from index",
            exception);
      } catch (RuntimeException exception) {
        log.error("Unexpected exception when trying to create index", exception);
      }
    }
    return created;
  }

  public boolean processCollection(MappedClass mc, DBCollection collection) {
    AtomicBoolean actionPerformed = new AtomicBoolean(false);
    try {
      Map<String, IndexCreator> creators = indexCreators(mc, collection);
      IndexManagerCollectionSession collectionSession = createCollectionSession(collection);
      // We should be attempting to drop indexes only if we successfully created all new ones

      int created = createNewIndexes(collectionSession, creators);
      if (created > 0) {
        actionPerformed.set(true);
      }

      boolean okToDropIndexes = created == 0;

      Map<String, Accesses> accesses = fetchIndexAccesses(collection);
      if (okToDropIndexes) {
        List<String> obsoleteIndexes = collectionSession.obsoleteIndexes(creators.keySet());
        if (isNotEmpty(obsoleteIndexes)) {
          // Make sure that all indexes that we have are operational, we check that they have being seen since
          // at least a day
          Date tooNew = tooNew();
          okToDropIndexes = collectionSession.isOkToDropIndexes(tooNew, accesses);

          if (okToDropIndexes) {
            obsoleteIndexes.forEach(name -> {
              try (AutoLogContext ignore2 = new IndexLogContext(name, OVERRIDE_ERROR)) {
                dropIndex(collection, name);
                actionPerformed.set(true);
              } catch (RuntimeException ex) {
                log.error("Failed to drop index", ex);
              }
            });
          }
        }
      }

      if (mc.getClazz().getAnnotation(IgnoreUnusedIndex.class) == null) {
        collectionSession.checkForUnusedIndexes(accesses);
      }
    } catch (MongoCommandException exception) {
      if (exception.getErrorCode() == 13) {
        throw new IndexManagerReadOnlyException();
      }
      log.error("", exception);
    } catch (RuntimeException exception) {
      log.error("", exception);
    }

    return actionPerformed.get();
  }

  public boolean ensureIndexes(Morphia morphia, Store store) {
    // Morphia auto creates embedded/nested Entity indexes with the parent Entity indexes.
    // There is no way to override this behavior.
    // https://github.com/mongodb/morphia/issues/706

    AtomicBoolean actionPerformed = new AtomicBoolean(false);

    Set<String> processedCollections = processIndexes(datastore, morphia, store, (mc, collection) -> {
      if (processCollection(mc, collection)) {
        actionPerformed.set(true);
      }
    });

    Set<String> whitelistCollections = ImmutableSet.<String>of(
        // Files and chunks
        "artifacts.chunks", "artifacts.files", "audits.chunks", "audits.files", "configs.chunks", "configs.files",
        "platforms.chunks", "platforms.files", "profile_results.chunks", "profile_results.files",
        "terraform_state.chunks", "terraform_state.files",
        // Quartz
        "quartz_calendars", "quartz_jobs", "quartz_locks", "quartz_schedulers", "quartz_triggers",
        // Quartz Verification
        "quartz_verification_calendars", "quartz_verification_jobs", "quartz_verification_locks",
        "quartz_verification_schedulers", "quartz_verification_triggers",
        // Persistent locks
        "locks",
        // verification service
        "timeSeriesAnomaliesRecords", "timeSeriesCumulativeSums",
        // telemetry
        "ciTelemetrySentStatus", "ciAccountExecutionMetadata", "pluginMetadataConfig", "pluginMetadataStatus",
        // cd-telemetry
        "cdTelemetrySentStatus", "cdAccountExecutionMetadata");

    List<String> obsoleteCollections = datastore.getDB()
                                           .getCollectionNames()
                                           .stream()
                                           .filter(name -> !processedCollections.contains(name))
                                           .filter(name -> !whitelistCollections.contains(name))
                                           .filter(name -> !name.startsWith("!!!test"))
                                           .collect(toList());

    if (isNotEmpty(obsoleteCollections)) {
      log.error("Unknown mongo collections detected: {}\n"
              + "Please create migration to delete them or add them to the whitelist.",
          join(", ", obsoleteCollections));
    }

    return actionPerformed.get();
  }
}
