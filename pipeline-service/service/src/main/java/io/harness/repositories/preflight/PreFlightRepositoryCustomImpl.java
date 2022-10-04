/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.preflight;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.entity.PreFlightEntity.PreFlightEntityKeys;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PreFlightRepositoryCustomImpl implements PreFlightRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PreFlightEntity update(Criteria criteria, PreFlightEntity entity) {
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(PreFlightEntityKeys.connectorCheckResponse, entity.getConnectorCheckResponse());
    update.set(PreFlightEntityKeys.pipelineInputResponse, entity.getPipelineInputResponse());
    RetryPolicy<Object> retryPolicy = getRetryPolicy();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PreFlightEntity.class));
  }

  @Override
  public PreFlightEntity update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PreFlightEntity.class));
  }

  private RetryPolicy<Object> getRetryPolicy() {
    return PersistenceUtils.getRetryPolicy(
        "[Retrying]: Failed updating Service; attempt: {}", "[Failed]: Failed updating Service; attempt: {}");
  }
}
