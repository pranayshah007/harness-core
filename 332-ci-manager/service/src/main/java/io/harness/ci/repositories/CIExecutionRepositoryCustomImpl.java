/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ci.execution.CIExecutionMetadata;
import io.harness.ci.execution.CIExecutionMetadata.CIExecutionMetadataKeys;

import com.google.inject.Inject;
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
public class CIExecutionRepositoryCustomImpl implements CIExecutionRepositoryCustom {
  MongoTemplate mongoTemplate;

  @Override
  public void updateExecutionStatus(String accountID, String runtimeId, String status) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.status, status);
    mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(true), CIExecutionMetadata.class);
  }

  @Override
  public void updateQueueId(String accountID, String runtimeId, String queueId) {
    Criteria criteria = Criteria.where(CIExecutionMetadataKeys.accountId)
                            .is(accountID)
                            .and(CIExecutionMetadataKeys.stageExecutionId)
                            .is(runtimeId);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(CIExecutionMetadataKeys.queueId, queueId);
    mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true).upsert(true), CIExecutionMetadata.class);
  }
}
