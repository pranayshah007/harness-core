/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.service.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.service.entity.ServiceSequence.ServiceSequenceKeys;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class ServiceSequenceRepositoryCustomImpl implements ServiceSequenceRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public ServiceSequence upsert(Criteria criteria, Update update, ServiceSequence serviceSequence) {
    Query query = new Query(criteria);
    Update newUpdate = getUpdateOperations(update, serviceSequence);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed upserting Service Sequence; attempt: {}",
        "[Failed]: Failed upserting Service Sequence; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, newUpdate, new FindAndModifyOptions().returnNew(true).upsert(true), ServiceSequence.class));
  }

  @Override
  public boolean delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed deleting Service Sequence; attempt: {}",
        "[Failed]: Failed deleting Service Sequence; attempt: {}");
    DeleteResult deleteResult =
        Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, ServiceSequence.class));
    return deleteResult.wasAcknowledged() && deleteResult.getDeletedCount() == 1;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  public Update getUpdateOperations(Update update, ServiceSequence serviceSequence) {
    update.set(ServiceSequenceKeys.accountId, serviceSequence.getAccountId());
    update.set(ServiceSequenceKeys.orgIdentifier, serviceSequence.getOrgIdentifier());
    update.set(ServiceSequenceKeys.projectIdentifier, serviceSequence.getProjectIdentifier());
    update.set(ServiceSequenceKeys.serviceIdentifier, serviceSequence.getServiceIdentifier());
    update.setOnInsert(ServiceSequenceKeys.createdAt, System.currentTimeMillis());
    update.set(ServiceSequenceKeys.lastModifiedAt, System.currentTimeMillis());
    update.set(ServiceSequenceKeys.serviceIdentifier, serviceSequence.getServiceIdentifier());
    return update;
  }
}
