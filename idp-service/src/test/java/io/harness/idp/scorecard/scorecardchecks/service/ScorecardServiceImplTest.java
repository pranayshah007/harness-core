/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecardchecks.service;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.scorecard.scorecardchecks.beans.BackstageCatalogEntityFacets;
import io.harness.idp.scorecard.scorecardchecks.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecardchecks.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecardchecks.repositories.ScorecardRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Facets;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardChecks;
import io.harness.spec.server.idp.v1.model.ScorecardChecksDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardServiceImplTest extends CategoryTest {
  private ScorecardServiceImpl scorecardServiceImpl;
  @Mock ScorecardRepository scorecardRepository;
  @Mock CheckService checkService;
  @Mock SetupUsageProducer setupUsageProducer;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock Call<Object> call;
  @Mock ObjectMapper objectMapper;

  @Mock TransactionTemplate transactionTemplate;

  @Mock OutboxService outboxService;
  private static final String ACCOUNT_ID = "123";
  private static final String SCORECARD_ID = "service_maturity";
  private static final String SCORECARD_NAME = "Service Maturity";
  private static final String GITHUB_CHECK_NAME = "Github Checks";
  private static final String GITHUB_CHECK_ID = "github_checks";
  private static final String CATALOG_CHECK_NAME = "Catalog Checks";
  private static final String CATALOG_CHECK_ID = "catalog_checks";
  private static final String SAMPLE_CHECK_ID = "sample_check";

  private static final String TEST_CHECK_IDENTIFIER = "test-check-identifier";
  private static final boolean TEST_CHECK_IS_CUSTOM = false;
  private static final double TEST_CHECK_WRIGHT = 1.0;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    scorecardServiceImpl = new ScorecardServiceImpl(scorecardRepository, checkService, setupUsageProducer,
        backstageResourceClient, transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllScorecardsAndChecksDetails() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    when(scorecardRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(List.of(scorecardEntity));
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    List<Scorecard> scorecards = scorecardServiceImpl.getAllScorecardsAndChecksDetails(ACCOUNT_ID);
    assertEquals(1, scorecards.size());
    assertEquals(1, scorecards.get(0).getChecksMissing().size());
    assertEquals(SAMPLE_CHECK_ID, scorecards.get(0).getChecksMissing().get(0));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllScorecardAndChecks() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    when(scorecardRepository.findByAccountIdentifierAndPublished(ACCOUNT_ID, true))
        .thenReturn(List.of(scorecardEntity));
    when(scorecardRepository.findByAccountIdentifierAndIdentifierIn(ACCOUNT_ID, List.of(SCORECARD_ID)))
        .thenReturn(List.of(scorecardEntity));
    when(checkService.getActiveChecks(any(), any())).thenReturn(getCheckEntities());
    List<ScorecardAndChecks> scorecardDetailsList =
        scorecardServiceImpl.getAllScorecardAndChecks(ACCOUNT_ID, List.of(SCORECARD_ID));
    assertEquals(1, scorecardDetailsList.size());
    assertEquals(2, scorecardDetailsList.get(0).getChecks().size());
    scorecardDetailsList = scorecardServiceImpl.getAllScorecardAndChecks(ACCOUNT_ID, new ArrayList<>());
    assertEquals(1, scorecardDetailsList.size());
    assertEquals(2, scorecardDetailsList.get(0).getChecks().size());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecardWithNoChecks() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(true, true);
    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecardWithInvalidCheck() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecardWithDeletedCheck() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(CheckEntity.builder()
                          .accountIdentifier(ACCOUNT_ID)
                          .identifier(SAMPLE_CHECK_ID)
                          .isCustom(true)
                          .isDeleted(true)
                          .build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveScorecard() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, true);
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    when(scorecardRepository.saveOrUpdate(any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    doNothing().when(setupUsageProducer).publishScorecardSetupUsage(request, ACCOUNT_ID);
    assertThatCode(() -> scorecardServiceImpl.saveScorecard(request, ACCOUNT_ID)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateScorecard() {
    ScorecardDetailsRequest request = getScorecardDetailsRequest(false, false);
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    when(scorecardRepository.update(any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    doNothing().when(setupUsageProducer).deleteScorecardSetupUsage(ACCOUNT_ID, request.getScorecard().getIdentifier());
    doNothing().when(setupUsageProducer).publishScorecardSetupUsage(request, ACCOUNT_ID);
    assertThatCode(() -> scorecardServiceImpl.updateScorecard(request, ACCOUNT_ID)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardDetails() {
    ScorecardEntity scorecardEntity = getScorecardEntity();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, SCORECARD_ID))
        .thenReturn(scorecardEntity);
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(getCheckEntities());
    ScorecardDetailsResponse response = scorecardServiceImpl.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID);
    assertEquals(SCORECARD_ID, response.getScorecard().getIdentifier());
    assertEquals(1, response.getScorecard().getChecksMissing().size());
    assertEquals(SAMPLE_CHECK_ID, response.getScorecard().getChecksMissing().get(0));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardDetailsThrowsException() {
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(null);
    scorecardServiceImpl.getScorecardDetails(ACCOUNT_ID, SCORECARD_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteScorecard() {
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(getTestCheck())).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    DeleteResult deleteResult = DeleteResult.acknowledged(1);
    when(scorecardRepository.delete(ACCOUNT_ID, SCORECARD_ID)).thenReturn(deleteResult);
    doNothing().when(setupUsageProducer).deleteScorecardSetupUsage(ACCOUNT_ID, SCORECARD_ID);
    assertThatCode(() -> scorecardServiceImpl.deleteScorecard(ACCOUNT_ID, SCORECARD_ID)).doesNotThrowAnyException();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteScorecardThrowsException() {
    List<CheckEntity> checkEntities = new ArrayList<>(getCheckEntities());
    checkEntities.add(
        CheckEntity.builder().accountIdentifier(ACCOUNT_ID).identifier(SAMPLE_CHECK_ID).isCustom(true).build());
    when(checkService.getChecksByAccountIdAndIdentifiers(any(), any())).thenReturn(checkEntities);
    ScorecardEntity.Check check =
        ScorecardEntity.Check.builder().weightage(1.0).isCustom(false).identifier("test").build();
    when(scorecardRepository.findByAccountIdentifierAndIdentifier(any(), any()))
        .thenReturn(ScorecardEntity.builder().checks(Collections.singletonList(check)).build());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    DeleteResult deleteResult = DeleteResult.acknowledged(0);
    when(scorecardRepository.delete(ACCOUNT_ID, SCORECARD_ID)).thenReturn(deleteResult);
    scorecardServiceImpl.deleteScorecard(ACCOUNT_ID, SCORECARD_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllEntityFacets() throws IOException {
    String data = "{\n"
        + "    \"facets\": {\n"
        + "        \"spec.type\": [\n"
        + "            {\n"
        + "                \"value\": \"library\",\n"
        + "                \"count\": 2\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"service\",\n"
        + "                \"count\": 33\n"
        + "            }\n"
        + "        ],\n"
        + "        \"relations.ownedBy\": [\n"
        + "            {\n"
        + "                \"value\": \"group:default/ccmplayacc\",\n"
        + "                \"count\": 1\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"group:default/cncf\",\n"
        + "                \"count\": 1\n"
        + "            }\n"
        + "        ],\n"
        + "        \"metadata.tags\": [\n"
        + "            {\n"
        + "                \"value\": \"data\",\n"
        + "                \"count\": 4\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"django\",\n"
        + "                \"count\": 1\n"
        + "            }\n"
        + "        ],\n"
        + "        \"spec.lifecycle\": [\n"
        + "            {\n"
        + "                \"value\": \"experimental\",\n"
        + "                \"count\": 20\n"
        + "            },\n"
        + "            {\n"
        + "                \"value\": \"prod\",\n"
        + "                \"count\": 2\n"
        + "            }\n"
        + "        ]\n"
        + "    }\n"
        + "}";
    Response<Object> response =
        Response.success(200, ResponseBody.create("Content", MediaType.parse("application/json")));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntityFacets(any())).thenReturn(call);
    on(scorecardServiceImpl).set("mapper", objectMapper);
    doReturn(GsonUtils.convertJsonStringToObject(data, BackstageCatalogEntityFacets.class))
        .when(objectMapper)
        .convertValue(any(), eq(BackstageCatalogEntityFacets.class));
    Facets facets = scorecardServiceImpl.getAllEntityFacets(ACCOUNT_ID, "component");
    assertEquals(List.of("library", "service"), facets.getType());
    assertEquals(List.of("data", "django"), facets.getTags());
  }

  private ScorecardEntity getScorecardEntity() {
    ScorecardEntity.Check check1 = ScorecardEntity.Check.builder().identifier(GITHUB_CHECK_ID).isCustom(true).build();
    ScorecardEntity.Check check2 = ScorecardEntity.Check.builder().identifier(CATALOG_CHECK_ID).isCustom(false).build();
    ScorecardEntity.Check check3 = ScorecardEntity.Check.builder().identifier(SAMPLE_CHECK_ID).isCustom(true).build();
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(SCORECARD_ID)
        .name(SCORECARD_NAME)
        .checks(List.of(check1, check2, check3))
        .published(true)
        .build();
  }

  private List<CheckEntity> getCheckEntities() {
    CheckEntity entity1 = CheckEntity.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .identifier(GITHUB_CHECK_ID)
                              .name(GITHUB_CHECK_NAME)
                              .isCustom(true)
                              .build();
    CheckEntity entity2 = CheckEntity.builder()
                              .accountIdentifier(GLOBAL_ACCOUNT_ID)
                              .identifier(CATALOG_CHECK_ID)
                              .name(CATALOG_CHECK_NAME)
                              .isCustom(false)
                              .build();
    return List.of(entity1, entity2);
  }

  private ScorecardDetailsRequest getScorecardDetailsRequest(boolean isEmptyChecks, boolean isEqualWeights) {
    ScorecardDetailsRequest request = new ScorecardDetailsRequest();
    ScorecardDetails scorecardDetails = new ScorecardDetails();
    scorecardDetails.setName(SCORECARD_NAME);
    scorecardDetails.setIdentifier(SCORECARD_ID);
    scorecardDetails.setPublished(true);
    scorecardDetails.setWeightageStrategy(isEqualWeights ? ScorecardDetails.WeightageStrategyEnum.EQUAL_WEIGHTS
                                                         : ScorecardDetails.WeightageStrategyEnum.CUSTOM);
    request.setScorecard(scorecardDetails);
    if (isEmptyChecks) {
      request.setChecks(new ArrayList<>());
    } else {
      ScorecardChecks scorecardChecks1 = new ScorecardChecksDetails();
      scorecardChecks1.setIdentifier(GITHUB_CHECK_ID);
      scorecardChecks1.setCustom(true);
      scorecardChecks1.setWeightage(2.0);
      ScorecardChecks scorecardChecks2 = new ScorecardChecksDetails();
      scorecardChecks2.setIdentifier(CATALOG_CHECK_ID);
      scorecardChecks2.setCustom(false);
      scorecardChecks2.setWeightage(4.0);
      ScorecardChecks scorecardChecks3 = new ScorecardChecksDetails();
      scorecardChecks3.setIdentifier(SAMPLE_CHECK_ID);
      scorecardChecks3.setCustom(true);
      scorecardChecks3.setWeightage(5.0);
      request.setChecks(List.of(scorecardChecks1, scorecardChecks2, scorecardChecks3));
    }
    return request;
  }

  private ScorecardEntity.Check getTestCheck() {
    return ScorecardEntity.Check.builder()
        .weightage(TEST_CHECK_WRIGHT)
        .isCustom(TEST_CHECK_IS_CUSTOM)
        .identifier(TEST_CHECK_IDENTIFIER)
        .build();
  }
}
