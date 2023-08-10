/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ScoresApi;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfoResponse;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateResponse;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardScoreResponse;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryResponse;

import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
public class ScoreApiImpl implements ScoresApi {
  private ScoreService scoreService;
  @Override
  public Response getAllScorecardSummary(String entityIdentifier, String harnessAccount) {
    try {
      List<ScorecardSummaryInfo> scorecardSummaryInfoList =
          scoreService.getScoresSummaryForAnEntity(harnessAccount, entityIdentifier);
      ScorecardSummaryResponse scorecardSummaryResponse = new ScorecardSummaryResponse();
      scorecardSummaryResponse.setScorecardsSummary(scorecardSummaryInfoList);
      return Response.status(Response.Status.OK).entity(scorecardSummaryResponse).build();
    } catch (Exception e) {
      log.error("Error in getting score summary for scorecards details for account - {} and entity - {}",
          harnessAccount, entityIdentifier);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getRecalibratedScoreForScorecard(
      String entityIdentifier, String scorecardIdentifier, String harnessAccount) {
    try {
      ScorecardSummaryInfo scorecardSummaryInfo = scoreService.getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
          harnessAccount, entityIdentifier, scorecardIdentifier);
      ScorecardRecalibrateResponse scorecardRecalibrateResponse = new ScorecardRecalibrateResponse();
      scorecardRecalibrateResponse.setRecalibratedScores(scorecardSummaryInfo);
      return Response.status(Response.Status.OK).entity(scorecardRecalibrateResponse).build();
    } catch (Exception e) {
      log.error(
          "Error in getting recalibrated score for scorecards details for account - {},  entity - {} and scorecard - {}",
          harnessAccount, entityIdentifier, scorecardIdentifier);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getScorecardsGraphsScoreSummary(
      String entityIdentifier, String harnessAccount, String scorecardIdentifier) {
    try {
      List<ScorecardGraphSummaryInfo> scorecardGraphSummaryInfos =
          scoreService.getScoresGraphSummaryForAnEntityAndScorecard(
              harnessAccount, entityIdentifier, scorecardIdentifier);
      ScorecardGraphSummaryInfoResponse scorecardGraphSummaryInfoResponse = new ScorecardGraphSummaryInfoResponse();
      scorecardGraphSummaryInfoResponse.setScorecardGraphSummary(scorecardGraphSummaryInfos);
      return Response.status(Response.Status.OK).entity(scorecardGraphSummaryInfoResponse).build();
    } catch (Exception e) {
      log.error(
          "Error in getting score graph summary for scorecards details for account - {},  entity - {} and scorecard - {}",
          harnessAccount, entityIdentifier, scorecardIdentifier);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getScorecardsScoresOverview(String entityIdentifier, String harnessAccount) {
    try {
      List<ScorecardScore> scorecardScores =
          scoreService.getScorecardScoreOverviewForAnEntity(harnessAccount, entityIdentifier);
      ScorecardScoreResponse scorecardScoreResponse = new ScorecardScoreResponse();
      int overallScore = 0;
      for (ScorecardScore scorecardScore : scorecardScores) {
        overallScore = overallScore + scorecardScore.getScore();
      }
      scorecardScoreResponse.setScorecardScores(scorecardScores);
      if (scorecardScores.size() > 0) {
        scorecardScoreResponse.setOverallScore(overallScore / scorecardScores.size());
      }
      return Response.status(Response.Status.OK).entity(scorecardScoreResponse).build();
    } catch (Exception e) {
      log.error("Error in getting scores overview for scorecards details for account - {},  entity - {} }",
          harnessAccount, entityIdentifier);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}