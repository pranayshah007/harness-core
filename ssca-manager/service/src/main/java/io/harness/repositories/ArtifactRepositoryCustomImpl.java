/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(SSCA)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class ArtifactRepositoryCustomImpl implements ArtifactRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public void invalidateOldArtifact(ArtifactEntity artifact) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(artifact.getAccountId())
                            .and(ArtifactEntityKeys.orgId)
                            .is(artifact.getOrgId())
                            .and(ArtifactEntityKeys.projectId)
                            .is(artifact.getProjectId())
                            .and(ArtifactEntityKeys.artifactId)
                            .is(artifact.getArtifactId())
                            .and(ArtifactEntityKeys.tag)
                            .is(artifact.getTag())
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ArtifactEntityKeys.invalid, true);
    mongoTemplate.updateMulti(query, update, ArtifactEntity.class);
  }
}
