/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityScores;
import io.harness.spec.server.idp.v1.model.EntityScoresResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfoResponse;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateIdentifiers;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateResponse;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardScoreResponse;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryResponse;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ScoresApiImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock ScoreService scoreService;

  @InjectMocks ScoreApiImpl scoreApiImpl;

  private static final String TEST_SCORECARD_NAME = "test-scorecard-name";
  private static final String TEST_SCORECARD_IDENTIFIER = "test-score-card-id";
  private static final int TEST_SCORE_FOR_SCORECARD = 90;
  private static final String TEST_DESCRIPTION_FOR_SCORECARD_IDENTIFIER = "test-description-for-scorecard";
  private static final Long TEST_TIMESTAMP_FOR_SCORECARD = 1694169398738L;

  private static final String TEST_CHECK_NAME = "test-check-name";
  private static final String TEST_CHECK_REASON = "test-check-reason";
  private static final Integer TEST_WEIGHT_FOR_CHECKS = 5;

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";
  private static final String TEST_ENTITY_IDENTIFIER = "test-entity-identifier";
  private static final String ERROR_MESSAGE_FOR_API_CALL = "Error : In Making API Call";

  private static final String TEST_ENTITY_NAME = "test-entity-name";
  private static final String TEST_ENTITY_KIND = "test-entity-kind";
  private static final String TEST_ENTITY_NAMESPACE = "test-entity-namespace";
  private static final String TEST_ENTITY_TITLE = "test-entity-title";
  private static final String TEST_ENTITY_TYPE = "test-entity-type";
  private static final String TEST_ENTITY_LIFECYCLE = "test-entity-lifecycle";
  private static final String TEST_ENTITY_TAGS = "test-entity-tags";
  private static final String TEST_ENTITY_OWNERS = "test-entity-owners";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllScorecardSummary() {
    ScorecardSummaryInfo scorecardSummaryInfo = getTestScorecardSummaryInfo();
    when(scoreService.getScoresSummaryForAnEntity(TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER))
        .thenReturn(Collections.singletonList(scorecardSummaryInfo));
    Response response = scoreApiImpl.getAllScorecardSummary(TEST_ENTITY_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(scorecardSummaryInfo, ((ScorecardSummaryResponse) response.getEntity()).getScorecardsSummary().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllScorecardSummaryError() {
    when(scoreService.getScoresSummaryForAnEntity(TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response = scoreApiImpl.getAllScorecardSummary(TEST_ENTITY_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetRecalibratedScoreForScorecard() {
    ScorecardSummaryInfo scorecardSummaryInfo = getTestScorecardSummaryInfo();
    when(scoreService.getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
             TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER, TEST_SCORECARD_IDENTIFIER))
        .thenReturn(getTestScorecardSummaryInfo());
    ScorecardRecalibrateRequest scorecardRecalibrateRequest = getScorecardRecalibrateRequest();
    Response response = scoreApiImpl.scorecardRecalibrate(scorecardRecalibrateRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(scorecardSummaryInfo, ((ScorecardRecalibrateResponse) response.getEntity()).getRecalibratedScores());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetRecalibratedScoreForScorecardError() {
    when(scoreService.getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
             TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER, TEST_SCORECARD_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    ScorecardRecalibrateRequest scorecardRecalibrateRequest = getScorecardRecalibrateRequest();
    Response response = scoreApiImpl.scorecardRecalibrate(scorecardRecalibrateRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetScorecardsGraphsScoreSummary() {
    ScorecardGraphSummaryInfo scorecardGraphSummaryInfo = new ScorecardGraphSummaryInfo();
    scorecardGraphSummaryInfo.setTimestamp(TEST_TIMESTAMP_FOR_SCORECARD);
    scorecardGraphSummaryInfo.setScorecardIdentifier(TEST_SCORECARD_IDENTIFIER);
    scorecardGraphSummaryInfo.setScore(TEST_SCORE_FOR_SCORECARD);

    when(scoreService.getScoresGraphSummaryForAnEntityAndScorecard(
             TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER, TEST_SCORECARD_IDENTIFIER))
        .thenReturn(Collections.singletonList(scorecardGraphSummaryInfo));
    Response response = scoreApiImpl.getScorecardsGraphsScoreSummary(
        TEST_ENTITY_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, TEST_SCORECARD_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(scorecardGraphSummaryInfo,
        ((ScorecardGraphSummaryInfoResponse) response.getEntity()).getScorecardGraphSummary().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetScorecardsGraphsScoreSummaryError() {
    when(scoreService.getScoresGraphSummaryForAnEntityAndScorecard(
             TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER, TEST_SCORECARD_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response = scoreApiImpl.getScorecardsGraphsScoreSummary(
        TEST_ENTITY_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, TEST_SCORECARD_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetScorecardsScoresOverview() {
    ScorecardScore scorecardScore = getTestScorecardScore();
    when(scoreService.getScorecardScoreOverviewForAnEntity(TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER))
        .thenReturn(Collections.singletonList(scorecardScore));
    Response response = scoreApiImpl.getScorecardsScoresOverview(TEST_ENTITY_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(scorecardScore, ((ScorecardScoreResponse) response.getEntity()).getScorecardScores().get(0));
    assertEquals(TEST_SCORE_FOR_SCORECARD, (int) ((ScorecardScoreResponse) response.getEntity()).getOverallScore());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetScorecardsScoresOverviewError() {
    when(scoreService.getScorecardScoreOverviewForAnEntity(TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response = scoreApiImpl.getScorecardsScoresOverview(TEST_ENTITY_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAggregatedScores() {
    EntityScores entityScores = new EntityScores();
    entityScores.setScores(Collections.singletonList(getTestScorecardScore()));
    entityScores.setName(TEST_ENTITY_NAME);
    entityScores.setKind(TEST_ENTITY_KIND);
    entityScores.setTitle(TEST_ENTITY_TITLE);
    entityScores.setNamespace(TEST_ENTITY_NAMESPACE);

    ScorecardFilter scorecardFilter = getTestScorecardFilter();

    when(scoreService.getEntityScores(TEST_ACCOUNT_IDENTIFIER, scorecardFilter))
        .thenReturn(Collections.singletonList(entityScores));
    Response response = scoreApiImpl.getAggregatedScores(scorecardFilter, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(entityScores, ((List<EntityScoresResponse>) response.getEntity()).get(0).getEntity());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAggregatedScoresError() {
    ScorecardFilter scorecardFilter = getTestScorecardFilter();
    when(scoreService.getEntityScores(TEST_ACCOUNT_IDENTIFIER, scorecardFilter))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response = scoreApiImpl.getAggregatedScores(scorecardFilter, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  private ScorecardSummaryInfo getTestScorecardSummaryInfo() {
    ScorecardSummaryInfo scorecardSummaryInfo = new ScorecardSummaryInfo();
    scorecardSummaryInfo.setScorecardName(TEST_SCORECARD_NAME);
    scorecardSummaryInfo.setScorecardIdentifier(TEST_SCORECARD_IDENTIFIER);
    scorecardSummaryInfo.setScore(TEST_SCORE_FOR_SCORECARD);
    scorecardSummaryInfo.setDescription(TEST_DESCRIPTION_FOR_SCORECARD_IDENTIFIER);
    scorecardSummaryInfo.setTimestamp(TEST_TIMESTAMP_FOR_SCORECARD);
    CheckStatus checkStatus = new CheckStatus();
    checkStatus.setName(TEST_CHECK_NAME);
    checkStatus.setReason(TEST_CHECK_REASON);
    checkStatus.setWeight(TEST_WEIGHT_FOR_CHECKS);
    checkStatus.setStatus(CheckStatus.StatusEnum.PASS);
    scorecardSummaryInfo.setChecksStatuses(Collections.singletonList(checkStatus));
    return scorecardSummaryInfo;
  }

  private ScorecardScore getTestScorecardScore() {
    ScorecardScore scorecardScore = new ScorecardScore();
    scorecardScore.setScorecardName(TEST_SCORECARD_NAME);
    scorecardScore.setDescription(TEST_DESCRIPTION_FOR_SCORECARD_IDENTIFIER);
    scorecardScore.setScore(TEST_SCORE_FOR_SCORECARD);
    return scorecardScore;
  }

  private ScorecardFilter getTestScorecardFilter() {
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(TEST_ENTITY_KIND);
    scorecardFilter.setType(TEST_ENTITY_TYPE);
    scorecardFilter.setLifecycle(Collections.singletonList(TEST_ENTITY_LIFECYCLE));
    scorecardFilter.setTags(Collections.singletonList(TEST_ENTITY_TAGS));
    scorecardFilter.setOwners(Collections.singletonList(TEST_ENTITY_OWNERS));
    return scorecardFilter;
  }

  private ScorecardRecalibrateRequest getScorecardRecalibrateRequest() {
    ScorecardRecalibrateIdentifiers scorecardRecalibrate = new ScorecardRecalibrateIdentifiers();
    scorecardRecalibrate.setScorecardIdentifier(TEST_SCORECARD_IDENTIFIER);
    scorecardRecalibrate.setEntityIdentifier(TEST_ENTITY_IDENTIFIER);
    ScorecardRecalibrateRequest scorecardRecalibrateRequest = new ScorecardRecalibrateRequest();
    scorecardRecalibrateRequest.setIdentifiers(scorecardRecalibrate);
    return scorecardRecalibrateRequest;
  }
}
