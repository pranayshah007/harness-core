/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity.AsyncScoreComputationKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class AsyncScoreComputationRepositoryCustomImpl implements AsyncScoreComputationRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public AsyncScoreComputationEntity saveOrUpdate(AsyncScoreComputationEntity asyncScoreComputationEntity) {
    AsyncScoreComputationEntity entity = findByAccountIdAndIdentifier(asyncScoreComputationEntity);
    if (entity == null) {
      asyncScoreComputationEntity.setStartTime(System.currentTimeMillis());
      return mongoTemplate.save(asyncScoreComputationEntity);
    }
    return update(asyncScoreComputationEntity);
  }

  public AsyncScoreComputationEntity update(AsyncScoreComputationEntity entity) {
    Criteria criteria = getScoreComputationCriteria(entity);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(AsyncScoreComputationKeys.startTime, System.currentTimeMillis());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, AsyncScoreComputationEntity.class);
  }

  private AsyncScoreComputationEntity findByAccountIdAndIdentifier(AsyncScoreComputationEntity entity) {
    Criteria criteria = getScoreComputationCriteria(entity);
    return mongoTemplate.findOne(Query.query(criteria), AsyncScoreComputationEntity.class);
  }

  private Criteria getScoreComputationCriteria(AsyncScoreComputationEntity entity) {
    return Criteria.where(AsyncScoreComputationKeys.accountIdentifier)
        .is(entity.getAccountIdentifier())
        .and(AsyncScoreComputationKeys.scorecardIdentifier)
        .is(entity.getScorecardIdentifier())
        .and(AsyncScoreComputationKeys.entityIdentifier)
        .is(entity.getEntityIdentifier());
  }
}
