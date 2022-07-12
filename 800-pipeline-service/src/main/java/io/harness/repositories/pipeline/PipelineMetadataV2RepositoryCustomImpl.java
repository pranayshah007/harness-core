/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineMetadata;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineMetadataV2RepositoryCustomImpl implements PipelineMetadataV2RepositoryCustom {
  MongoTemplate mongoTemplate;
  PmsGitSyncHelper pmsGitSyncHelper;

  @Override
  public PipelineMetadataV2 incCounter(String accountId, String orgId, String projectIdentifier, String pipelineId) {
    Update update = new Update();
    Criteria criteria = Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
                            .is(accountId)
                            .and(PipelineMetadataV2Keys.orgIdentifier)
                            .is(orgId)
                            .and(PipelineMetadataV2Keys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineMetadataV2Keys.identifier)
                            .is(pipelineId);
    update.inc(PipelineMetadataV2Keys.runSequence);
    return mongoTemplate.findAndModify(
        new Query(criteria), update, new FindAndModifyOptions().returnNew(true), PipelineMetadataV2.class);
  }

  @Override
  public Optional<PipelineMetadataV2> getPipelineMetadata(
      String accountId, String orgId, String projectIdentifier, String identifier) {
    Criteria criteria = Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
                            .is(accountId)
                            .and(PipelineMetadataV2Keys.orgIdentifier)
                            .is(orgId)
                            .and(PipelineMetadataV2Keys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineMetadataV2Keys.identifier)
                            .is(identifier);
    return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), PipelineMetadataV2.class));
  }

  @Override
  public List<PipelineMetadataV2> getMetadataForGivenPipelineIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    Criteria criteria = Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
                            .is(accountId)
                            .and(PipelineMetadataV2Keys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineMetadataV2Keys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineMetadataV2Keys.identifier)
                            .in(identifiers);
    return mongoTemplate.find(query(criteria), PipelineMetadataV2.class);
  }

  @Override
  public Optional<PipelineMetadataV2> cloneFromPipelineMetadata(
      String accountId, String orgId, String projectIdentifier, String identifier) {
    Update update = new Update();
    Criteria criteria = Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
                            .is(accountId)
                            .and(PipelineMetadataV2Keys.orgIdentifier)
                            .is(orgId)
                            .and(PipelineMetadataV2Keys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineMetadataV2Keys.identifier)
                            .is(identifier);
    update.inc(PipelineMetadataV2Keys.runSequence);
    PipelineMetadata pipelineMetadata = mongoTemplate.findOne(
        new Query(criteria).with(Sort.by(Sort.Direction.DESC, PipelineMetadataV2Keys.runSequence)),
        PipelineMetadata.class);
    if (pipelineMetadata == null) {
      return Optional.of(mongoTemplate.save(PipelineMetadataV2.builder()
                                                .accountIdentifier(accountId)
                                                .orgIdentifier(orgId)
                                                .projectIdentifier(projectIdentifier)
                                                .identifier(identifier)
                                                .runSequence(1)
                                                .build()));
    }
    return Optional.of(mongoTemplate.save(PipelineMetadataV2.builder()
                                              .accountIdentifier(accountId)
                                              .orgIdentifier(orgId)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier(identifier)
                                              .runSequence(pipelineMetadata.getRunSequence() + 1)
                                              .build()));
  }
}
