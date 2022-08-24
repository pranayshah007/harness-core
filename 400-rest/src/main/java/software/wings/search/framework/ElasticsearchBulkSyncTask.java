/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeSubscriber;
import io.harness.persistence.HIterator;

import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.search.framework.ElasticsearchBulkMigrationJob.ElasticsearchBulkMigrationJobBuilder;
import software.wings.search.framework.SearchEntityIndexState.SearchEntityIndexStateKeys;
import software.wings.timescale.framework.TimeScaleEntityIndexState;
import software.wings.timescale.migrations.DeploymentsMigrationHelper;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * The task responsible for carrying out the bulk sync
 * from the persistence layer to elasticsearch.
 *
 * @author utkarsh
 */

@OwnedBy(PL)
@Slf4j
public class ElasticsearchBulkSyncTask {
  public static final String PARENT_PIPELNE_UPDATE_STATEMENT =
      "UPDATE DEPLOYMENT SET PARENT_PIPELINE_ID=?, WORKFLOWS=?, CREATED_BY_TYPE=? WHERE EXECUTIONID=?";
  public static final String EXECUTION_FAILURE_UPDATE_STATEMENT =
      "UPDATE DEPLOYMENT SET FAILURE_DETAILS=?,FAILED_STEP_NAMES=?,FAILED_STEP_TYPES=? WHERE EXECUTIONID=?";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ElasticsearchSyncHelper elasticsearchSyncHelper;
  @Inject private ElasticsearchBulkMigrationHelper elasticsearchBulkMigrationHelper;
  @Inject private ElasticsearchIndexManager elasticsearchIndexManager;
  @Inject private Set<SearchEntity<?>> searchEntities;
  @Inject private Set<TimeScaleEntity<?>> timeScaleEntities;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ExecutorService executorService;
  private Queue<ChangeEvent<?>> changeEventsDuringBulkSync;
  private Map<Class, Boolean> isFirstChangeReceived;

  @Inject private DeploymentsMigrationHelper deploymentsMigrationHelper;

  private void cleanupFailedBulkMigrationJobs() {
    try (HIterator<ElasticsearchBulkMigrationJob> iterator =
             new HIterator<>(wingsPersistence.createQuery(ElasticsearchBulkMigrationJob.class).fetch())) {
      while (iterator.hasNext()) {
        ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob = iterator.next();
        String indexName = elasticsearchBulkMigrationJob.getNewIndexName();
        elasticsearchIndexManager.deleteIndex(indexName);
        wingsPersistence.delete(ElasticsearchBulkMigrationJob.class, elasticsearchBulkMigrationJob.getEntityClass());
      }
    }
  }

  private boolean updateSearchEntityIndexState(ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob) {
    Query<SearchEntityIndexState> query = wingsPersistence.createQuery(SearchEntityIndexState.class)
                                              .field(SearchEntityIndexStateKeys.entityClass)
                                              .equal(elasticsearchBulkMigrationJob.getEntityClass());

    UpdateOperations<SearchEntityIndexState> updateOperations =
        wingsPersistence.createUpdateOperations(SearchEntityIndexState.class)
            .set(SearchEntityIndexStateKeys.indexName, elasticsearchBulkMigrationJob.getNewIndexName())
            .set(SearchEntityIndexStateKeys.syncVersion, elasticsearchBulkMigrationJob.getToVersion())
            .set(SearchEntityIndexStateKeys.recreateIndex, false);

    SearchEntityIndexState searchEntityIndexState =
        wingsPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
    if (searchEntityIndexState == null
        || !searchEntityIndexState.getIndexName().equals(elasticsearchBulkMigrationJob.getNewIndexName())) {
      log.error("Search entitiy {} index state did not update", elasticsearchBulkMigrationJob.getEntityClass());
      return false;
    }
    return true;
  }

  private boolean cleanupSuccessfulBulkMigrationJobs(Set<SearchEntity<?>> syncedBulkEntites) {
    boolean isRunningSuccessfully = true;
    Iterator<SearchEntity<?>> iterator = syncedBulkEntites.iterator();
    while (iterator.hasNext() && isRunningSuccessfully) {
      SearchEntity<?> syncedBulkEntity = iterator.next();
      ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob =
          wingsPersistence.get(ElasticsearchBulkMigrationJob.class, syncedBulkEntity.getClass().getCanonicalName());
      isRunningSuccessfully = updateSearchEntityIndexState(elasticsearchBulkMigrationJob)
          && wingsPersistence.delete(
              ElasticsearchBulkMigrationJob.class, syncedBulkEntity.getClass().getCanonicalName());
    }
    return isRunningSuccessfully;
  }

  private Set<SearchEntity<?>> getEntitiesToBulkSync(Collection<SearchEntity<?>> searchEntityList) {
    Set<SearchEntity<?>> entitiesToBulkSync = new HashSet<>();
    for (SearchEntity<?> searchEntity : searchEntityList) {
      SearchEntityIndexState searchEntityIndexState =
          wingsPersistence.get(SearchEntityIndexState.class, searchEntity.getClass().getCanonicalName());
      if (searchEntityIndexState != null && !searchEntityIndexState.shouldBulkSync()) {
        log.info("Entity {} is already migrated to elasticsearch", searchEntity.getClass());
      } else {
        log.info("Entity {} is to be migrated to elasticsearch", searchEntity.getClass());
        entitiesToBulkSync.add(searchEntity);
      }
    }
    return entitiesToBulkSync;
  }

  private String generateIndexName(String type, String version) {
    long timestamp = Instant.now().toEpochMilli();
    return type.concat("_").concat(version).concat("_").concat(Long.toString(timestamp));
  }

  private SearchEntityIndexState getSearchEntityIndexState(SearchEntity<?> searchEntity) {
    return wingsPersistence.get(SearchEntityIndexState.class, searchEntity.getClass().getCanonicalName());
  }

  private boolean createSearchBulkMigrationJob(SearchEntityIndexState searchEntityIndexState, String newIndexName,
      String newVersion, Class<? extends SearchEntity> searchEntityClass) {
    ElasticsearchBulkMigrationJobBuilder elasticsearchBulkMigrationJobBuilder =
        ElasticsearchBulkMigrationJob.builder()
            .entityClass(searchEntityClass.getCanonicalName())
            .newIndexName(newIndexName)
            .toVersion(newVersion);
    if (searchEntityIndexState != null) {
      elasticsearchBulkMigrationJobBuilder.oldIndexName(searchEntityIndexState.getIndexName())
          .fromVersion(searchEntityIndexState.getSyncVersion());
    }
    ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob = elasticsearchBulkMigrationJobBuilder.build();
    return wingsPersistence.save(elasticsearchBulkMigrationJob) != null;
  }

  private boolean createElasticsearchBulkMigrationJobs(Set<SearchEntity<?>> entitiesToBulkSync) {
    boolean areJobsCreated = true;
    for (SearchEntity<?> searchEntity : entitiesToBulkSync) {
      SearchEntityIndexState searchEntityIndexState = getSearchEntityIndexState(searchEntity);
      String newIndexName = generateIndexName(searchEntity.getType(), searchEntity.getVersion());
      areJobsCreated = areJobsCreated
          && createSearchBulkMigrationJob(
              searchEntityIndexState, newIndexName, searchEntity.getVersion(), searchEntity.getClass());
    }
    return areJobsCreated;
  }

  private ChangeSubscriber<?> getChangeSubscriber() {
    return changeEvent -> {
      if (isFirstChangeReceived.get(changeEvent.getEntityType()) == null) {
        changeEventsDuringBulkSync.add(changeEvent);
        isFirstChangeReceived.put(changeEvent.getEntityType(), true);
      }
    };
  }

  // TIMESCALE
  private Set<TimeScaleEntity<?>> getTimeScaleEntitiesToMigrate(Collection<TimeScaleEntity<?>> timeScaleEntities) {
    Set<TimeScaleEntity<?>> entitiesToMigrate = new HashSet<>();
    for (TimeScaleEntity<?> timeScaleEntity : timeScaleEntities) {
      entitiesToMigrate.add(timeScaleEntity);
    }
    return entitiesToMigrate;
  }

  private boolean doTimeScaleMigration(Set<TimeScaleEntity<?>> entitiesToMigrate) {
    boolean hasTimeScaleMigrationSucceeded = true;

    List<String> accountIds =
        featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC).stream().collect(Collectors.toList());

    boolean deploymentTimescaleMigrated = false;
    for (TimeScaleEntity timeScaleEntity : entitiesToMigrate) {
      log.info("Migrating {} to timescale", timeScaleEntity.getClass().getCanonicalName());

      // timeScaleEntity could be one of [APPLICATION,SERVICE,PIPELINE,WORKFLOW,TAGLINKS]

      TimeScaleEntityIndexState timeScaleEntityIndexState =
          wingsPersistence.get(TimeScaleEntityIndexState.class, timeScaleEntity.getClass().getCanonicalName());
      if (timeScaleEntityIndexState == null) {
        TimeScaleEntityIndexState initial_entity = new TimeScaleEntityIndexState(
            timeScaleEntity.getClass().getCanonicalName(), System.currentTimeMillis(), new LinkedList<>(), accountIds);
        wingsPersistence.save(initial_entity);
        continue;
      }
      List<String> alreadyMigratedAccountIds = timeScaleEntityIndexState.getAlreadyMigratedAccountIds() != null
          ? timeScaleEntityIndexState.getAlreadyMigratedAccountIds()
          : new LinkedList<>();
      List<String> toMigrateAccountIds = timeScaleEntityIndexState.getToMigrateAccountIds() != null
          ? timeScaleEntityIndexState.getToMigrateAccountIds()
          : new LinkedList<>(accountIds);
      toMigrateAccountIds = toMigrateAccountIds.stream()
                                .filter(id -> !alreadyMigratedAccountIds.contains(id))
                                .collect(Collectors.toList());
      int n = 0;
      if (toMigrateAccountIds != null) {
        n = toMigrateAccountIds.size();
      }
      if (!deploymentTimescaleMigrated) {
        deploymentTimescaleMigrated = true;
      }
      for (int i = 0; i < n; i++) {
        String accountId = toMigrateAccountIds.get(0);
        hasTimeScaleMigrationSucceeded = timeScaleEntity.runMigration(accountId);
        if (hasTimeScaleMigrationSucceeded) {
          alreadyMigratedAccountIds.add(accountId);
          toMigrateAccountIds.remove(accountId);
        }
      }
      TimeScaleEntityIndexState app_entity =
          new TimeScaleEntityIndexState(timeScaleEntity.getClass().getCanonicalName(), System.currentTimeMillis(),
              alreadyMigratedAccountIds, toMigrateAccountIds);
      wingsPersistence.save(app_entity);

      if (hasTimeScaleMigrationSucceeded) {
        log.info("{} migrated to timescale", timeScaleEntity.getClass().getCanonicalName());
      } else {
        log.error(String.format("Failed to migrate %s to timescale", timeScaleEntity.getClass().getCanonicalName()));
        break;
      }
    }

    TimeScaleEntityIndexState workflowExecutionEntityIndexState =
        wingsPersistence.get(TimeScaleEntityIndexState.class, WorkflowExecution.class.getCanonicalName());
    executorService.submit(() -> {
      if (workflowExecutionEntityIndexState == null) {
        runDeploymentMigrations(accountIds);
        TimeScaleEntityIndexState execution_entity = new TimeScaleEntityIndexState(
            WorkflowExecution.class.getCanonicalName(), System.currentTimeMillis(), accountIds, new ArrayList<>());
        wingsPersistence.save(execution_entity);
      } else {
        List<String> toMigrateAccountIds = workflowExecutionEntityIndexState.getToMigrateAccountIds() != null
            ? workflowExecutionEntityIndexState.getToMigrateAccountIds()
            : new LinkedList<>(accountIds);
        List<String> alreadyMigratedAccountIds =
            workflowExecutionEntityIndexState.getAlreadyMigratedAccountIds() != null
            ? workflowExecutionEntityIndexState.getAlreadyMigratedAccountIds()
            : new LinkedList<>();
        runDeploymentMigrations(toMigrateAccountIds);
        alreadyMigratedAccountIds.addAll(toMigrateAccountIds);
        TimeScaleEntityIndexState execution_entity =
            new TimeScaleEntityIndexState(WorkflowExecution.class.getCanonicalName(), System.currentTimeMillis(),
                alreadyMigratedAccountIds, toMigrateAccountIds);
        wingsPersistence.save(execution_entity);
      }
    });

    return hasTimeScaleMigrationSucceeded;
  }

  private void runDeploymentMigrations(List<String> accountIds) {
    deploymentsMigrationHelper.setFailureDetailsForAccountIds(
        accountIds, "EXECUTION_FAILURE_TIMESCALE MIGRATION: ", 500, EXECUTION_FAILURE_UPDATE_STATEMENT);
    deploymentsMigrationHelper.setParentPipelineForAccountIds(
        accountIds, "PARENT_PIPELINE_TIMESCALE MIGRATION: ", 1000, PARENT_PIPELNE_UPDATE_STATEMENT);
  }

  public ElasticsearchBulkSyncTaskResult run() {
    changeEventsDuringBulkSync = new LinkedList<>();
    isFirstChangeReceived = new HashMap<>();

    log.info("Clean up failed migration jobs");
    cleanupFailedBulkMigrationJobs();

    log.info("Initializing change listeners for search entities for bulk sync.");
    elasticsearchSyncHelper.startChangeListeners(getChangeSubscriber());

    log.info("Getting the entities that have to bulk synced");
    Set<SearchEntity<?>> entitiesToBulkSync = getEntitiesToBulkSync(searchEntities);

    log.info("Create jobs for bulk migration of search entities");
    boolean areJobsCreated = createElasticsearchBulkMigrationJobs(entitiesToBulkSync);

    // TIMESCALE
    log.info("Getting the timescale entities to migrate");
    Set<TimeScaleEntity<?>> timeScaleEntitiesToMigrate = getTimeScaleEntitiesToMigrate(timeScaleEntities);

    // TIMESCALE
    log.info("Starting migration of timescale entities");
    boolean hasTimeScaleMigrationSucceeded = doTimeScaleMigration(timeScaleEntitiesToMigrate);

    if (!areJobsCreated) {
      return new ElasticsearchBulkSyncTaskResult(false, changeEventsDuringBulkSync);
    }

    log.info("Starting migration of entities from persistence to search database");
    boolean hasMigrationSucceeded = elasticsearchBulkMigrationHelper.doBulkSync(entitiesToBulkSync);

    log.info("Calling change tracker to close change listeners after bulk sync was completed");
    elasticsearchSyncHelper.stopChangeListeners();

    if (hasMigrationSucceeded) {
      hasMigrationSucceeded = cleanupSuccessfulBulkMigrationJobs(entitiesToBulkSync);
      return new ElasticsearchBulkSyncTaskResult(hasMigrationSucceeded, changeEventsDuringBulkSync);
    }

    return new ElasticsearchBulkSyncTaskResult(false, changeEventsDuringBulkSync);
  }
}
