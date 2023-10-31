/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import com.google.inject.Inject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class EnforcementSummaryProjectParamMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  private final int BATCH_SIZE = 100;

  @Override
  public void migrate() {
    log.info("Starting Enforcement Summary Entity Project Identifiers Migration");
    Criteria criteria = Criteria.where(EnforcementSummaryEntity.EnforcementSummaryEntityKeys.accountId).is(null);
    Query query = new Query(criteria);
    FindIterable<Document> iterable =
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(EnforcementSummaryEntity.class))
            .find(query.getQueryObject())
            .batchSize(BATCH_SIZE);

    List<EnforcementSummaryEntity> entityToBeUpdated = new ArrayList<>();
    for (Document document : iterable) {
      try {
        EnforcementSummaryEntity summaryEntity =
            mongoTemplate.getConverter().read(EnforcementSummaryEntity.class, document);
        entityToBeUpdated.add(summaryEntity);
        if (entityToBeUpdated.size() == BATCH_SIZE) {
          bulkUpdate(entityToBeUpdated);
          entityToBeUpdated = new ArrayList<>();
        }
      } catch (Exception e) {
        log.error(String.format("Skipping Migration for Enforcement Summaries {id: %s}, {Exception: %s}",
            entityToBeUpdated.stream().map(entity -> entity.getId()).collect(Collectors.joining(", ")), e));
      }

      try {
        if (entityToBeUpdated.size() > 0) {
          bulkUpdate(entityToBeUpdated);
        }
      } catch (Exception e) {
        log.error(String.format("Skipping Migration for Enforcement Summaries {id: %s}, {Exception: %s}",
            entityToBeUpdated.stream().map(entity -> entity.getId()).collect(Collectors.joining(", ")), e));
      }
    }
    log.info("Enforcement Summary Entity Migration Project Identifiers Successful");
  }

  private void bulkUpdate(List<EnforcementSummaryEntity> enforcementSummaryEntities) {
    List<String> orchestrationIds =
        enforcementSummaryEntities.stream().map(entity -> entity.getOrchestrationId()).collect(Collectors.toList());

    Criteria criteria = Criteria.where(ArtifactEntity.ArtifactEntityKeys.orchestrationId).in(orchestrationIds);
    Query query = new Query(criteria);

    Map<String, ArtifactEntity> orchestrationIdToartifactMap =
        mongoTemplate.find(query, ArtifactEntity.class)
            .stream()
            .collect(Collectors.toMap(ArtifactEntity::getOrchestrationId, Function.identity()));
    List<EnforcementSummaryEntity> updatedEntityList = new ArrayList<>();

    for (EnforcementSummaryEntity summaryEntity : enforcementSummaryEntities) {
      ArtifactEntity artifact = orchestrationIdToartifactMap.get(summaryEntity.getOrchestrationId());
      EnforcementSummaryEntity newSummaryEntity =
          EnforcementSummaryEntity.builder()
              .id(summaryEntity.getId())
              .artifact(summaryEntity.getArtifact())
              .enforcementId(summaryEntity.getEnforcementId())
              .orchestrationId(summaryEntity.getOrchestrationId())
              .denyListViolationCount(summaryEntity.getDenyListViolationCount())
              .allowListViolationCount(summaryEntity.getAllowListViolationCount())
              .status(summaryEntity.getStatus())
              .createdAt(summaryEntity.getCreatedAt())
              .accountId(artifact.getAccountId())
              .orgIdentifier(artifact.getOrgId())
              .projectIdentifier(artifact.getProjectId())
              .build();
      updatedEntityList.add(newSummaryEntity);
    }

    List<WriteModel<Document>> bulkOperations = new ArrayList<>();

    for (EnforcementSummaryEntity entity : updatedEntityList) {
      Document document = new Document();
      mongoTemplate.getConverter().write(entity, document);
      ReplaceOneModel<Document> replaceOneModel = new ReplaceOneModel<>(Filters.eq("_id", entity.getId()), document);
      bulkOperations.add(replaceOneModel);
    }

    mongoTemplate.getCollection(mongoTemplate.getCollectionName(EnforcementSummaryEntity.class))
        .bulkWrite(bulkOperations);
  }
}
