/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecardchecks.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.scorecardchecks.service.ScorecardService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Check;
import io.harness.spec.server.idp.v1.model.Facets;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardChecksDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardsApiImplTest extends CategoryTest {
  private ScorecardsApiImpl scorecardsApiImpl;
  @Mock ScorecardService scorecardService;
  private static final String ACCOUNT_ID = "123";
  private static final String SCORECARD_ID = "service_maturity";
  private static final String SCORECARD_NAME = "Service Maturity";
  private static final String CHECK_ID = "readme_file_exists";
  private static final String CHECK_NAME = "Readme file exists";
  private static final String KIND = "component";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    scorecardsApiImpl = new ScorecardsApiImpl(scorecardService);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecards() {
    Scorecard scorecard = new Scorecard();
    scorecard.setName(SCORECARD_NAME);
    scorecard.setIdentifier(SCORECARD_ID);
    Check check = new Check();
    check.setName(CHECK_NAME);
    check.setIdentifier(CHECK_ID);
    check.setCustom(true);
    scorecard.setChecks(List.of(check));
    when(scorecardService.getAllScorecardsAndChecksDetails(ACCOUNT_ID)).thenReturn(List.of(scorecard));
    Response response = scorecardsApiImpl.getScorecards(ACCOUNT_ID);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecard() {
    ScorecardDetailsResponse scorecardDetailsResponse = new ScorecardDetailsResponse();
    ScorecardDetails scorecardDetails = new ScorecardDetails();
    scorecardDetails.setName(SCORECARD_NAME);
    scorecardDetails.setIdentifier(SCORECARD_ID);
    scorecardDetailsResponse.setScorecard(scorecardDetails);
    ScorecardChecksDetails scorecardChecksDetails = new ScorecardChecksDetails();
    scorecardChecksDetails.setName(CHECK_NAME);
    scorecardDetails.setIdentifier(CHECK_ID);
    scorecardDetailsResponse.setChecks(List.of(scorecardChecksDetails));
    when(scorecardService.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID)).thenReturn(scorecardDetailsResponse);
    Response response = scorecardsApiImpl.getScorecard(SCORECARD_ID, ACCOUNT_ID);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardThrowsException() {
    when(scorecardService.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID)).thenThrow(InvalidRequestException.class);
    Response response = scorecardsApiImpl.getScorecard(SCORECARD_ID, ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateScorecard() {
    doNothing().when(scorecardService).saveScorecard(new ScorecardDetailsRequest(), ACCOUNT_ID);
    Response response = scorecardsApiImpl.createScorecard(new ScorecardDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateScorecardThrowsDuplicateKeyException() {
    ScorecardDetailsRequest request = new ScorecardDetailsRequest();
    ScorecardDetails scorecardDetails = new ScorecardDetails();
    scorecardDetails.setIdentifier(SCORECARD_ID);
    scorecardDetails.setName(SCORECARD_NAME);
    request.setScorecard(scorecardDetails);
    doThrow(DuplicateKeyException.class).when(scorecardService).saveScorecard(request, ACCOUNT_ID);
    Response response = scorecardsApiImpl.createScorecard(request, ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateScorecardThrowsException() {
    doThrow(InvalidRequestException.class)
        .when(scorecardService)
        .saveScorecard(new ScorecardDetailsRequest(), ACCOUNT_ID);
    Response response = scorecardsApiImpl.createScorecard(new ScorecardDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteScorecard() {
    doNothing().when(scorecardService).deleteScorecard(ACCOUNT_ID, SCORECARD_ID);
    Response response = scorecardsApiImpl.deleteScorecard(SCORECARD_ID, ACCOUNT_ID);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteScorecardThrowsException() {
    doThrow(InvalidRequestException.class).when(scorecardService).deleteScorecard(ACCOUNT_ID, SCORECARD_ID);
    Response response = scorecardsApiImpl.deleteScorecard(SCORECARD_ID, ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntityFacets() {
    Facets facets = new Facets();
    facets.setType(List.of("service"));
    facets.setTags(List.of("java"));
    facets.setLifecycle(List.of("production"));
    when(scorecardService.getAllEntityFacets(ACCOUNT_ID, KIND)).thenReturn(facets);
    Response response = scorecardsApiImpl.getEntityFacets(KIND, ACCOUNT_ID);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntityFacetsThrowsException() {
    when(scorecardService.getAllEntityFacets(ACCOUNT_ID, KIND)).thenThrow(InvalidRequestException.class);
    Response response = scorecardsApiImpl.getEntityFacets(KIND, ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateScorecard() {
    doNothing().when(scorecardService).updateScorecard(new ScorecardDetailsRequest(), ACCOUNT_ID);
    Response response = scorecardsApiImpl.updateScorecard(SCORECARD_ID, new ScorecardDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateScorecardThrowsException() {
    doThrow(InvalidRequestException.class)
        .when(scorecardService)
        .updateScorecard(new ScorecardDetailsRequest(), ACCOUNT_ID);
    Response response = scorecardsApiImpl.updateScorecard(SCORECARD_ID, new ScorecardDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }
}
