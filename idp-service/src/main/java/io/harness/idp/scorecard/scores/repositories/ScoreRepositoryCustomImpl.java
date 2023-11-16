/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.DateUtils.yesterdayInMilliseconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.BooleanOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
  public AggregationResults<ScorecardIdentifierAndScore> computeScoresPercentageByScorecard(
      String accountIdentifier, List<String> scorecardIdentifiers) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                            .in(scorecardIdentifiers)
                            .and(ScoreEntity.ScoreKeys.lastComputedTimestamp)
                            .gt(yesterdayInMilliseconds());
    ProjectionOperation projectionOperation =
        Aggregation.project()
            .andExpression(Constants.ID_KEY)
            .as(Constants.SCORECARD_IDENTIFIER_KEY)
            .andExpression(Constants.COUNT_KEY)
            .as(Constants.COUNT_KEY)
            .and(
                ConditionalOperators.when(Criteria.where(Constants.COUNT_KEY).ne(0))
                    .then(
                        ArithmeticOperators.valueOf(Constants.SCORES_GREATER_THAN_75_KEY).divideBy(Constants.COUNT_KEY))
                    .otherwise(0))
            .as(Constants.PERCENTAGE_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.scorecardIdentifier, ScoreEntity.ScoreKeys.entityIdentifier)
            .first(Constants.SCORE_KEY)
            .as(Constants.SCORE_KEY),
        Aggregation.group(Constants.ID_KEY + DOT_SEPARATOR + Constants.SCORECARD_IDENTIFIER_KEY)
            .count()
            .as(Constants.COUNT_KEY)
            .sum(ConditionalOperators.when(Criteria.where(Constants.SCORE_KEY).gt(75)).then(1).otherwise(0))
            .as(Constants.SCORES_GREATER_THAN_75_KEY),
        projectionOperation);

    return mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, ScorecardIdentifierAndScore.class);
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

  @Override
  public AggregationResults<EntityIdentifierAndScore> getScoresForEntityIdentifiersAndScorecardIdentifiers(
      String accountIdentifier, List<String> entityIdentifiers, List<String> scorecardIdentifiers) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier);

    if (!isEmpty(entityIdentifiers)) {
      criteria.and(ScoreEntity.ScoreKeys.entityIdentifier).in(entityIdentifiers);
    }

    if (!isEmpty(scorecardIdentifiers)) {
      criteria.and(ScoreEntity.ScoreKeys.scorecardIdentifier).in(scorecardIdentifiers);
    }

    ProjectionOperation projectionOperation =
        Aggregation.project()
            .andExpression(Constants.ID_KEY)
            .as(ScoreEntity.ScoreKeys.entityIdentifier)
            .andExpression(Constants.SCORE_ENTITY_KEY + DOT_SEPARATOR + Constants.SCORE_KEY)
            .as(Constants.SCORE_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.entityIdentifier)
            .push(ScoreEntity.ScoreKeys.entityIdentifier)
            .as(ScoreEntity.ScoreKeys.entityIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        projectionOperation);
    return mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, EntityIdentifierAndScore.class);
  }

  @Override
  public AggregationResults<EntityIdentifierAndCheckStatus> getCheckStatusForLatestComputedScores(
      String accountIdentifier, List<String> entityIdentifiers, List<String> scorecardIdentifiers,
      Pair<Long, Long> previousDay24HourTimeFrame, String checkIdentifier, boolean custom) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier).is(accountIdentifier);

    if (!isEmpty(entityIdentifiers)) {
      criteria.and(ScoreEntity.ScoreKeys.entityIdentifier).in(entityIdentifiers);
    }

    if (!isEmpty(scorecardIdentifiers)) {
      criteria.and(ScoreEntity.ScoreKeys.scorecardIdentifier).in(scorecardIdentifiers);
    }

    if (previousDay24HourTimeFrame != null) {
      criteria.and(ScoreEntity.ScoreKeys.lastComputedTimestamp)
          .gt(previousDay24HourTimeFrame.getLeft())
          .lt(previousDay24HourTimeFrame.getRight());
    }

    ProjectionOperation filterProjection =
        Aggregation.project()
            .andExpression(Constants.ID_KEY)
            .as(ScoreEntity.ScoreKeys.entityIdentifier)
            .and(ArrayOperators.Filter.filter("scoreEntity.checkStatus")
                     .as(Constants.CHECK_KEY)
                     .by(BooleanOperators.And.and(
                         ComparisonOperators.valueOf("check.identifier").equalToValue(checkIdentifier),
                         ComparisonOperators.valueOf("check.custom").equalToValue(custom))))
            .as(Constants.CHECK_STATUS_KEY);
    ProjectionOperation mapProjection = Aggregation.project()
                                            .andExpression(ScoreEntity.ScoreKeys.entityIdentifier)
                                            .as(ScoreEntity.ScoreKeys.entityIdentifier)
                                            .and(ArrayOperators.arrayOf("checkStatus.status").elementAt(0))
                                            .as(Constants.STATUS_KEY);

    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.sort(Sort.Direction.DESC, ScoreEntity.ScoreKeys.lastComputedTimestamp),
        Aggregation.group(ScoreEntity.ScoreKeys.entityIdentifier)
            .push(ScoreEntity.ScoreKeys.entityIdentifier)
            .as(ScoreEntity.ScoreKeys.entityIdentifier)
            .first(Aggregation.ROOT)
            .as(Constants.SCORE_ENTITY_KEY),
        filterProjection, mapProjection);
    return mongoTemplate.aggregate(aggregation, Constants.SCORE_COLLECTION_NAME, EntityIdentifierAndCheckStatus.class);
  }

  @Override
  public UpdateResult updateCheckIdentifier(ScoreEntity score, List<CheckStatus> checkStatuses) {
    Criteria criteria = Criteria.where(ScoreEntity.ScoreKeys.accountIdentifier)
                            .is(score.getAccountIdentifier())
                            .and(ScoreEntity.ScoreKeys.scorecardIdentifier)
                            .is(score.getScorecardIdentifier())
                            .and(ScoreEntity.ScoreKeys.entityIdentifier)
                            .is(score.getEntityIdentifier())
                            .and(ScoreEntity.ScoreKeys.lastComputedTimestamp)
                            .is(score.getLastComputedTimestamp());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(ScoreEntity.ScoreKeys.checkStatus, checkStatuses);
    return mongoTemplate.updateFirst(query, update, ScoreEntity.class);
  }
}
