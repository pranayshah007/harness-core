/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@OwnedBy(HarnessTeam.IDP)
public interface ScoreRepositoryCustom {
  AggregationResults<ScoreEntityByScorecardIdentifier> getAllLatestScoresByScorecardsForAnEntity(
      String accountIdentifier, String entityIdentifier);
  AggregationResults<ScorecardIdentifierAndScore> computeScoresPercentageByScorecard(
      String accountIdentifier, List<String> scorecardIdentifiers);

  ScoreEntity getLatestComputedScoreForEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scoreCardIdentifier);

  AggregationResults<EntityIdentifierAndScore> getScoresForEntityIdentifiersAndScorecardIdentifiers(
      String accountIdentifier, List<String> entityIdentifiers, List<String> scorecardIdentifiers);

  AggregationResults<EntityIdentifierAndCheckStatus> getCheckStatusForLatestComputedScores(String accountIdentifier,
      List<String> entityIdentifiers, List<String> scorecardIdentifiers, Pair<Long, Long> previousDay24HourTimeFrame,
      String checkIdentifier, boolean custom);

  UpdateResult updateCheckIdentifier(ScoreEntity score, List<CheckStatus> checkStatuses);
}
