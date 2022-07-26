/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetails.EntityGitDetailsKeys;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineMetadata;
import io.harness.pms.pipeline.PipelineMetadata.PipelineMetadataKeys;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineMetadataRepositoryCustomImpl implements PipelineMetadataRepositoryCustom {
  MongoTemplate mongoTemplate;
  PmsGitSyncHelper pmsGitSyncHelper;

  @Override
  public PipelineMetadata incCounter(
      String accountId, String orgId, String projectIdentifier, String pipelineId, ByteString gitSyncBranchContext) {
    Update update = new Update();
    Criteria criteria = Criteria.where(PipelineMetadataKeys.accountIdentifier)
                            .is(accountId)
                            .and(PipelineMetadataKeys.orgIdentifier)
                            .is(orgId)
                            .and(PipelineMetadataKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineMetadataKeys.identifier)
                            .is(pipelineId);
    Criteria gitSyncCriteria = null;
    if (gitSyncBranchContext != null) {
      EntityGitDetails entityGitDetails = pmsGitSyncHelper.getEntityGitDetailsFromBytes(gitSyncBranchContext);
      if (entityGitDetails != null) {
        gitSyncCriteria = Criteria.where(PipelineMetadataKeys.entityGitDetails + "." + EntityGitDetailsKeys.branch)
                              .is(entityGitDetails.getBranch())
                              .and(PipelineMetadataKeys.entityGitDetails + "." + EntityGitDetailsKeys.repoIdentifier)
                              .is(entityGitDetails.getRepoIdentifier());
      }
    }
    if (gitSyncCriteria != null) {
      criteria = new Criteria().andOperator(criteria, gitSyncCriteria);
    }

    update.inc(PipelineMetadataKeys.runSequence);
    return mongoTemplate.findAndModify(
        new Query(criteria), update, new FindAndModifyOptions().returnNew(true), PipelineMetadata.class);
  }

  @Override
  public Optional<PipelineMetadata> getPipelineMetadata(
      String accountId, String orgId, String projectIdentifier, String identifier, ByteString gitSyncBranchContext) {
    Criteria criteria = Criteria.where(PipelineMetadataKeys.accountIdentifier)
                            .is(accountId)
                            .and(PipelineMetadataKeys.orgIdentifier)
                            .is(orgId)
                            .and(PipelineMetadataKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineMetadataKeys.identifier)
                            .is(identifier);
    Criteria gitSyncCriteria = null;
    if (gitSyncBranchContext != null) {
      EntityGitDetails entityGitDetails = pmsGitSyncHelper.getEntityGitDetailsFromBytes(gitSyncBranchContext);
      if (entityGitDetails != null) {
        gitSyncCriteria = Criteria.where(PipelineMetadataKeys.entityGitDetails + "." + EntityGitDetailsKeys.branch)
                              .is(entityGitDetails.getBranch())
                              .and(PipelineMetadataKeys.entityGitDetails + "." + EntityGitDetailsKeys.repoIdentifier)
                              .is(entityGitDetails.getRepoIdentifier());
      }
    }
    if (gitSyncCriteria != null) {
      criteria = new Criteria().andOperator(criteria, gitSyncCriteria);
    }
    return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), PipelineMetadata.class));
  }
}
