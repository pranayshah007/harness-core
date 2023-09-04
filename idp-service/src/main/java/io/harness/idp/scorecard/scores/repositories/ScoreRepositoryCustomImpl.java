/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.repositories;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entities.ScoreEntity;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class ScoreRepositoryCustomImpl implements ScoreRepositoryCustom {
  private MongoTemplate mongoTemplate;
  @Override
  public AggregationResults<ScoreEntityByScorecardIdentifier> getAllLatestScoresByScorecardsForAnEntity(
      String accountIdentifier, String entityIdentifier) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScoreEntity.ScoreKeys.entityIdentifier)
                            .is(entityIdentifier);

    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression(Constants.ID_KEY)
                                                  .as(ScoreEntity.ScoreKeys.scorecardIdentifier)
                                                  .andExpression(Constants.SCORE_ENTITY_KEY)
                                                  .as(Constants.SCORE_ENTITY_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.scorecardIdentifier)
            .push(ScoreEntity.ScoreKeys.scorecardIdentifier)
            .as(ScoreEntity.ScoreKeys.scorecardIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        projectionOperation);

    AggregationResults<ScoreEntityByScorecardIdentifier> result =
        mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, ScoreEntityByScorecardIdentifier.class);
    return result;
  }

  @Override
  public ScoreEntity getLatestComputedScoreForEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scoreCardIdentifier) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScoreEntity.ScoreKeys.entityIdentifier)
                            .is(entityIdentifier)
                            .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                            .is(scoreCardIdentifier);
    Query query =
        new Query(criteria).with(Sort.by(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp)).limit(1);
    return mongoTemplate.findOne(query, ScoreEntity.class);
  }
}
