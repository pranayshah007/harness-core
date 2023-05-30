/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.instancesyncperpetualtaskinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import lombok.AllArgsConstructor;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.DX)
public class InstanceSyncPerpetualTaskInfoRepositoryCustomImpl
    implements InstanceSyncPerpetualTaskInfoRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public InstanceSyncPerpetualTaskInfo update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), InstanceSyncPerpetualTaskInfo.class);
  }

  @Override
  public List<InstanceSyncPerpetualTaskInfo> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, InstanceSyncPerpetualTaskInfo.class);
  }

  @Override
  public Page<InstanceSyncPerpetualTaskInfo> findAllInPages(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<InstanceSyncPerpetualTaskInfo> instanceSyncPerpetualTaskInfos =
        mongoTemplate.find(query, InstanceSyncPerpetualTaskInfo.class);
    return PageableExecutionUtils.getPage(instanceSyncPerpetualTaskInfos, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), InstanceSyncPerpetualTaskInfo.class));
  }

  @Override
  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy("[Retrying]: Failed deleting instance sync perpetual task info; attempt: {}",
            "[Failed]: Failed deleting Service; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, InstanceSyncPerpetualTaskInfo.class));
  }
  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
