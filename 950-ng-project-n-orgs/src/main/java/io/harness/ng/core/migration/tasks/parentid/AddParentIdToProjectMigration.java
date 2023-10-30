/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.tasks.parentid;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(PL)
public class AddParentIdToProjectMigration implements NGMigration {
  private static final int BATCH_SIZE = 500;
  private static final String PROJECT_PARENT_ID_GENERATION_LOG_CONST = "[NGAddParentIdToProjectMigration]:";
  private static final String LOCAL_MAP_DELIMITER = "|";

  private final MongoTemplate mongoTemplate;
  private final Map<String, String> orgIdentifierUniqueIdMap;

  @Inject
  public AddParentIdToProjectMigration(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    orgIdentifierUniqueIdMap = new HashMap<>();
  }

  @Override
  public void migrate() {
    log.info(format("%s Starting migration for Entity Type: [%s] ", PROJECT_PARENT_ID_GENERATION_LOG_CONST, "Project"));

    int migratedCounter = 0;
    int totalCounter = 0;
    int updateCounter = 0;
    int batchSizeCounter = 0;

    Query documentQuery = new Query(new Criteria());
    log.info(format("%s Entity Type: [%s], Total count: [%s]", PROJECT_PARENT_ID_GENERATION_LOG_CONST, "Project",
        mongoTemplate.count(documentQuery, Project.class)));

    BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);

    // iterate over all Project documents
    try (CloseableIterator<Project> iterator =
             mongoTemplate.stream(documentQuery.limit(NO_LIMIT).maxTimeMsec(MAX_VALUE), Project.class)) {
      while (iterator.hasNext()) {
        totalCounter++;
        Project nextProject = iterator.next();
        if (null != nextProject && isEmpty(nextProject.getParentId())) {
          updateCounter++;
          final String mapKey =
              nextProject.getAccountIdentifier() + LOCAL_MAP_DELIMITER + nextProject.getOrgIdentifier();

          String uniqueIdOfOrg = null;

          // check if Org with uniqueId is present locally
          if (orgIdentifierUniqueIdMap.containsKey(mapKey)) {
            uniqueIdOfOrg = orgIdentifierUniqueIdMap.get(mapKey);
          } else {
            Criteria orgCriteria = Criteria.where("accountIdentifier")
                                       .is(nextProject.getAccountIdentifier())
                                       .and("identifier")
                                       .is(nextProject.getOrgIdentifier());

            Organization organization = mongoTemplate.findOne(new Query(orgCriteria), Organization.class);
            if (organization != null && isNotEmpty(organization.getUniqueId())) {
              uniqueIdOfOrg = organization.getUniqueId();
              orgIdentifierUniqueIdMap.put(mapKey, uniqueIdOfOrg);
            }
          }

          if (isNotEmpty(uniqueIdOfOrg)) {
            batchSizeCounter++;
            Update update = new Update().set(ProjectKeys.parentId, uniqueIdOfOrg);
            bulkOperations.updateOne(new Query(Criteria.where("_id").is(nextProject.getId())), update);

            if (batchSizeCounter == BATCH_SIZE) {
              migratedCounter += bulkOperations.execute().getModifiedCount();
              bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
              batchSizeCounter = 0;
            }
          }
        }
      }
      if (batchSizeCounter > 0) { // for the last remaining batch of entities
        migratedCounter += bulkOperations.execute().getModifiedCount();
      }
    } catch (Exception exc) {
      log.error(format("%s job failed for Entity Type: [%s]", PROJECT_PARENT_ID_GENERATION_LOG_CONST, "Project"), exc);
    }
    log.info(format(
        "%s Migration for Entity Type: [%s]. Total documents: [%d], documents to Update: [%s], Successful: [%d], Failed: [%d]",
        PROJECT_PARENT_ID_GENERATION_LOG_CONST, "Project", totalCounter, updateCounter, migratedCounter,
        updateCounter - migratedCounter));
  }
}
