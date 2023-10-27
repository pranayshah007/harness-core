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
import io.harness.repositories.SBOMComponentRepo;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import com.google.inject.Inject;
import com.mongodb.client.FindIterable;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class ArtifactEntityMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  @Inject ArtifactRepository artifactRepository;
  @Inject SBOMComponentRepo sbomComponentRepo;

  @Override
  public void migrate() {
    log.info("Starting Artifact Entity Migration");
    Criteria criteria = Criteria.where(ArtifactEntityKeys.invalid).is(null);
    Query query = new Query(criteria);
    FindIterable<Document> iterable =
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(ArtifactEntity.class)).find(query.getQueryObject());

    for (Document document : iterable) {
      try {
        ArtifactEntity artifact = mongoTemplate.getConverter().read(ArtifactEntity.class, document);

        ArtifactEntity recentArtifact = artifactRepository.findOne(Criteria.where(ArtifactEntityKeys.accountId)
                                                                       .is(artifact.getAccountId())
                                                                       .and(ArtifactEntityKeys.orgId)
                                                                       .is(artifact.getOrgId())
                                                                       .and(ArtifactEntityKeys.projectId)
                                                                       .is(artifact.getProjectId())
                                                                       .and(ArtifactEntityKeys.artifactId)
                                                                       .is(artifact.getArtifactId())
                                                                       .and(ArtifactEntityKeys.tag)
                                                                       .is(artifact.getTag())
                                                                       .and(ArtifactEntityKeys.createdOn)
                                                                       .gt(artifact.getCreatedOn()));

        if (Objects.isNull(recentArtifact)) {
          artifact.setInvalid(false);
        } else {
          artifact.setInvalid(true);
        }

        query = new Query(Criteria.where(NormalizedSBOMEntityKeys.accountId)
                              .is(artifact.getAccountId())
                              .and(NormalizedSBOMEntityKeys.orgIdentifier)
                              .is(artifact.getOrgId())
                              .and(NormalizedSBOMEntityKeys.projectIdentifier)
                              .is(artifact.getProjectId())
                              .and(NormalizedSBOMEntityKeys.orchestrationId)
                              .is(artifact.getOrchestrationId()));
        Long count = mongoTemplate.count(query, NormalizedSBOMComponentEntity.class);

        artifact.setComponentsCount(count);
        artifact.setLastUpdatedAt(artifact.getCreatedOn().toEpochMilli());
        artifact.setNonProdEnvCount(0l);
        artifact.setProdEnvCount(0l);
        artifactRepository.save(artifact);
      } catch (Exception e) {
        log.error(String.format(
            "Skipping Migration for Artifact {id: %s}, {Exception: %s}", document.get("_id").toString(), e));
      }
    }
    log.info("Artifact Entity Migration Successful");
  }
}
