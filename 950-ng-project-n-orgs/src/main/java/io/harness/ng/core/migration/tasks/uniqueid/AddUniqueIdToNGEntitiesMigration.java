/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.tasks.uniqueid;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.mongo.MongoConfig;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.persistence.UniqueIdAccess;
import io.harness.persistence.UniqueIdAware;

import com.google.inject.Inject;
import java.lang.reflect.ParameterizedType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PL)
@Slf4j
public class AddUniqueIdToNGEntitiesMigration<T extends UniqueIdAware> {
  public static final int BATCH_SIZE = 500;
  public static final String UNIQUE_ID_GENERATION_LOG_CONST = "[NGUniqueIdGenerationMigration]:";

  private final MongoTemplate mongoTemplate;
  private final Class<T> persistentClass;

  @Inject
  public AddUniqueIdToNGEntitiesMigration(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.persistentClass =
        (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
  }

  public void migrate() {
    log.info(format("%s Starting uniqueId migration for Entity Type: [%s]", UNIQUE_ID_GENERATION_LOG_CONST,
        persistentClass.getSimpleName()));

    int migratedCounter = 0;
    int totalCounter = 0;
    int batchSizeCounter = 0;
    int toUpdateCounter = 0;

    Query documentQuery = new Query(new Criteria());
    log.info(format("%s Entity Type: [%s], total count: [%s]", UNIQUE_ID_GENERATION_LOG_CONST,
        persistentClass.getSimpleName(), mongoTemplate.count(documentQuery, persistentClass)));

    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, persistentClass);
    String idValue = null;
    try (CloseableIterator<? extends UniqueIdAware> iterator =
             mongoTemplate.stream(documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), persistentClass)) {
      while (iterator.hasNext()) {
        totalCounter++;
        UniqueIdAware entity = iterator.next();
        if (isEmpty(entity.getUniqueId())) {
          if (entity instanceof Project) {
            idValue = ((Project) entity).getId();
          } else if (entity instanceof Organization) {
            idValue = ((Organization) entity).getId();
          }
          if (isNotEmpty(idValue)) {
            toUpdateCounter++;
            batchSizeCounter++;
            Update update = new Update().set(UniqueIdAccess.UNIQUE_ID_KEY, UUIDGenerator.generateUuid());
            bulkOperations.updateOne(new Query(Criteria.where("_id").is(idValue)), update);
            if (batchSizeCounter == BATCH_SIZE) {
              migratedCounter += bulkOperations.execute().getModifiedCount();
              bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, persistentClass);
              batchSizeCounter = 0;
            }
          }
        }
      }
      if (batchSizeCounter > 0) { // for the last remaining batch of entities
        migratedCounter += bulkOperations.execute().getModifiedCount();
      }
    } catch (Exception exc) {
      log.error(format("%s job failed for Entity Type [%s], for entity Id: [%s]", UNIQUE_ID_GENERATION_LOG_CONST,
                    persistentClass.getSimpleName(), idValue),
          exc);
    }
    log.info(format(
        "%s Migration for Entity Type: [%s]. Total documents: [%d], documents to Update: [%s], Successful: [%d], Failed: [%d]",
        UNIQUE_ID_GENERATION_LOG_CONST, persistentClass.getSimpleName(), totalCounter, toUpdateCounter, migratedCounter,
        toUpdateCounter - migratedCounter));
  }
}