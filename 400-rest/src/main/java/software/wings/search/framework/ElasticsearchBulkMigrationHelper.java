/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@OwnedBy(PL)
@Slf4j
class ElasticsearchBulkMigrationHelper {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ElasticsearchClient elasticsearchClient;
  @Inject private ElasticsearchIndexManager elasticsearchIndexManager;
  private static final int NUMBER_OF_BULK_SYNC_THREADS = 5;
  private static final int BULK_SYNC_TASK_QUEUE_SIZE = 200;
  private static final int BULK_SYNC_ITERATOR_BATCH_SIZE = 100;
  private static final String BULK_THREAD_SUFFIX = "-bulk-migration-%d";
  private static final String BASE_CONFIGURATION_PATH = "/elasticsearch/framework/BaseViewSchema.json";
  private static final String ENTITY_CONFIGURATION_PATH_BASE = "/elasticsearch/entities/";

  private String getSearchConfiguration(String configurationPath) {
    try {
      String fullConfigurationPath = String.format("%s%s", ENTITY_CONFIGURATION_PATH_BASE, configurationPath);
      String entitySettingsString =
          IOUtils.toString(getClass().getResourceAsStream(fullConfigurationPath), StandardCharsets.UTF_8);
      String baseSettingsString =
          IOUtils.toString(getClass().getResourceAsStream(BASE_CONFIGURATION_PATH), StandardCharsets.UTF_8);
      return SearchEntityUtils.mergeSettings(baseSettingsString, entitySettingsString);
    } catch (IOException e) {
      log.error("Failed to create index", e);
      return null;
    }
  }

  private boolean createIndex(
      SearchEntity<?> searchEntity, ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob) {
    String newConfiguration = getSearchConfiguration(searchEntity.getConfigurationPath());
    return elasticsearchIndexManager.createIndex(elasticsearchBulkMigrationJob.getNewIndexName(), newConfiguration);
  }

  private <T extends PersistentEntity> boolean migrateDocuments(SearchEntity<T> searchEntity, String newIndexName) {
    String threadPoolNameFormat = searchEntity.getClass().getSimpleName().concat(BULK_THREAD_SUFFIX);
    ExecutorService executorService = Executors.newFixedThreadPool(
        NUMBER_OF_BULK_SYNC_THREADS, new ThreadFactoryBuilder().setNameFormat(threadPoolNameFormat).build());
    BoundedExecutorService boundedExecutorService =
        new BoundedExecutorService(executorService, BULK_SYNC_TASK_QUEUE_SIZE);
    List<Future<Boolean>> taskFutures = new ArrayList<>();

    Class<T> sourceEntityClass = searchEntity.getSourceEntityClass();

    try (HIterator<T> iterator = new HIterator<>(wingsPersistence.createQuery(sourceEntityClass)
                                                     .fetch(new FindOptions()
                                                                .maxTime(Integer.MAX_VALUE, TimeUnit.MILLISECONDS)
                                                                .batchSize(getIteratorBatchSize())
                                                                .limit(NO_LIMIT)))) {
      while (iterator.hasNext()) {
        final T object = iterator.next();
        SearchEntityBulkMigrationTask<T> searchEntityBulkMigrationTask =
            new SearchEntityBulkMigrationTask<>(elasticsearchClient, searchEntity, object, newIndexName);
        taskFutures.add(boundedExecutorService.submit(searchEntityBulkMigrationTask));
      }
      for (Future<Boolean> taskFuture : taskFutures) {
        boolean result = taskFuture.get();
        if (!result) {
          return false;
        }
      }
      return true;
    } catch (InterruptedException e) {
      log.error("Bulk migration interrupted", e);
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      log.error("Bulk migration errored due to error", e.getCause());
    } finally {
      executorService.shutdownNow();
    }
    return false;
  }

  private boolean reconfigureAlias(ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob, String type) {
    String aliasName = elasticsearchIndexManager.getAliasName(type);
    boolean isIndexAttached =
        elasticsearchIndexManager.attachIndexToAlias(aliasName, elasticsearchBulkMigrationJob.getNewIndexName());
    String oldIndexName = elasticsearchBulkMigrationJob.getOldIndexName();
    if (isIndexAttached && oldIndexName != null) {
      return elasticsearchIndexManager.removeIndexFromAlias(oldIndexName);
    }
    return isIndexAttached;
  }

  private boolean runBulkMigration(SearchEntity<?> searchEntity) {
    ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob =
        wingsPersistence.get(ElasticsearchBulkMigrationJob.class, searchEntity.getClass().getCanonicalName());
    boolean isMigrationFailed = !createIndex(searchEntity, elasticsearchBulkMigrationJob);

    if (!isMigrationFailed) {
      isMigrationFailed = !migrateDocuments(searchEntity, elasticsearchBulkMigrationJob.getNewIndexName());
    }

    return !isMigrationFailed;
  }

  private boolean runPostMigrationStep(SearchEntity<?> searchEntity) {
    ElasticsearchBulkMigrationJob elasticsearchBulkMigrationJob =
        wingsPersistence.get(ElasticsearchBulkMigrationJob.class, searchEntity.getClass().getCanonicalName());
    return reconfigureAlias(elasticsearchBulkMigrationJob, searchEntity.getType());
  }

  boolean doBulkSync(Set<SearchEntity<?>> entitiesToBulkSync) {
    boolean hasMigrationSucceeded = true;
    for (SearchEntity<?> searchEntity : entitiesToBulkSync) {
      log.info("Migrating {} to elasticsearch", searchEntity.getClass().getCanonicalName());
      hasMigrationSucceeded = runBulkMigration(searchEntity);

      if (hasMigrationSucceeded) {
        log.info("{} migrated to elasticsearch", searchEntity.getClass().getCanonicalName());
      } else {
        log.error(String.format("Failed to migrate %s to elasticsearch", searchEntity.getClass().getCanonicalName()));
        break;
      }
    }

    if (hasMigrationSucceeded) {
      for (SearchEntity<?> searchEntity : entitiesToBulkSync) {
        hasMigrationSucceeded = hasMigrationSucceeded && runPostMigrationStep(searchEntity);
      }
    }

    return hasMigrationSucceeded;
  }

  private int getIteratorBatchSize() {
    return BULK_SYNC_ITERATOR_BATCH_SIZE;
  }
}
