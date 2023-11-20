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
import io.harness.repositories.ArtifactRepository;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;

import com.google.inject.Inject;
import com.mongodb.client.FindIterable;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class ArtifactEnvCountMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  @Inject ArtifactRepository artifactRepository;

  @Override
  public void migrate() {
    log.info("Starting Artifact Entity Env Counts Migration");
    List<CdInstanceSummary> cdInstanceSummaryList = mongoTemplate.findAll(CdInstanceSummary.class);
    Criteria criteria = Criteria.where(ArtifactEntityKeys.artifactCorrelationId)
                            .in(cdInstanceSummaryList.stream()
                                    .map(CdInstanceSummary::getArtifactCorrelationId)
                                    .collect(Collectors.toSet()));
    Query query = new Query(criteria);
    FindIterable<Document> iterable =
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(ArtifactEntity.class)).find(query.getQueryObject());

    for (Document document : iterable) {
      try {
        ArtifactEntity artifact = mongoTemplate.getConverter().read(ArtifactEntity.class, document);

        Criteria prodEnvCriteria = Criteria.where(CdInstanceSummaryKeys.accountIdentifier)
                                       .is(artifact.getAccountId())
                                       .and(CdInstanceSummaryKeys.orgIdentifier)
                                       .is(artifact.getOrgId())
                                       .and(CdInstanceSummaryKeys.projectIdentifier)
                                       .is(artifact.getProjectId())
                                       .and(CdInstanceSummaryKeys.artifactCorrelationId)
                                       .is(artifact.getArtifactCorrelationId())
                                       .and(CdInstanceSummaryKeys.envType)
                                       .is(EnvType.Production);

        Criteria nonProdEnvCriteria = Criteria.where(CdInstanceSummaryKeys.accountIdentifier)
                                          .is(artifact.getAccountId())
                                          .and(CdInstanceSummaryKeys.orgIdentifier)
                                          .is(artifact.getOrgId())
                                          .and(CdInstanceSummaryKeys.projectIdentifier)
                                          .is(artifact.getProjectId())
                                          .and(CdInstanceSummaryKeys.artifactCorrelationId)
                                          .is(artifact.getArtifactCorrelationId())
                                          .and(CdInstanceSummaryKeys.envType)
                                          .is(EnvType.PreProduction);

        artifact.setProdEnvCount(mongoTemplate.count(new Query(prodEnvCriteria), CdInstanceSummary.class));
        artifact.setNonProdEnvCount(mongoTemplate.count(new Query(nonProdEnvCriteria), CdInstanceSummary.class));

        artifactRepository.save(artifact);

      } catch (Exception e) {
        log.error(String.format("Failed to update env counts for artifact: %s, Exception: %s", document, e));
      }
    }
  }
}
