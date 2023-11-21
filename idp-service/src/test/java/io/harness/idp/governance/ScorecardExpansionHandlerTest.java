/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.governance;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.governance.beans.Constants.IDP_SCORECARD_EXPANSION_KEY;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdstage.remote.CDStageConfigClient;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstagebeans.BackstageCatalogComponentEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntityTypes;
import io.harness.idp.governance.services.ScorecardExpansionHandler;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class ScorecardExpansionHandlerTest extends CategoryTest {
  @InjectMocks ScorecardExpansionHandler expansionHandler;
  @Mock CDStageConfigClient cdStageConfigClient;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock ScoreService scoreService;
  private static final String ACCOUNT_ID = "123";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  private static final String TEST_SCORECARD_NAME = "test-scorecard-name";
  private static final String TEST_SCORECARD_IDENTIFIER = "test-score-card-id";
  private static final int TEST_SCORE_FOR_SCORECARD = 90;
  private static final String TEST_CHECK_NAME = "test-check-name";
  private static final String TEST_CHECK_REASON = "test-check-reason";
  private static final Integer TEST_WEIGHT_FOR_CHECKS = 5;
  private ExpansionRequestMetadata expansionRequestMetadata;
  private final Gson gson = new Gson();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                   .setAccountId(ACCOUNT_ID)
                                   .setOrgId(ORG_ID)
                                   .setProjectId(PROJECT_ID)
                                   .build();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testExpand() throws IOException {
    CDStageMetaDataDTO cdStageMetaDataDTO =
        CDStageMetaDataDTO.builder().serviceRef(IDP_SERVICE_ENTITY_NAME).environmentRef("env").build();
    JsonNode fieldValue = getJson("governance/ScorecardExpansionHandlerInput.json");

    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(cdStageMetaDataDTO);
    mockRestStatic.when(() -> NGRestUtils.getGeneralResponse(any())).thenReturn(getResponse());
    when(scoreService.getScoresSummaryForAnEntity(ACCOUNT_ID, IDP_SERVICE_ENTITY_ID))
        .thenReturn(Collections.singletonList(getTestScorecardSummaryInfo()));
    ExpansionResponse response = expansionHandler.expand(fieldValue, expansionRequestMetadata, null);
    String expectedJson = "{\"idp-service\":["
        + "{\"identifier\":\"test-score-card-id\",\"name\":\"test-scorecard-name\",\"score\":90,"
        + "\"check\":[{\"name\":\"test-check-name\",\"status\":\"PASS\"}]}]}";
    assertNotNull(response);
    assertTrue(response.isSuccess());
    assertEquals(expectedJson, response.getValue().toJson());
    assertEquals(IDP_SCORECARD_EXPANSION_KEY, response.getKey());
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testExpandCDStageResponseThrowsException() throws IOException {
    JsonNode fieldValue = getJson("governance/ScorecardExpansionHandlerInput.json");
    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenThrow(InvalidRequestException.class);
    ExpansionResponse response = expansionHandler.expand(fieldValue, expansionRequestMetadata, null);
    assertNotNull(response);
    assertFalse(response.isSuccess());
    assertNotNull(response.getErrorMessage());
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testExpandGetByEntityRefsThrowsException() throws IOException {
    CDStageMetaDataDTO cdStageMetaDataDTO =
        CDStageMetaDataDTO.builder().serviceRef(IDP_SERVICE_ENTITY_NAME).environmentRef("env").build();
    JsonNode fieldValue = getJson("governance/ScorecardExpansionHandlerInput.json");

    MockedStatic<NGRestUtils> mockRestStatic = mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(cdStageMetaDataDTO);
    mockRestStatic.when(() -> NGRestUtils.getGeneralResponse(any())).thenThrow(InvalidRequestException.class);
    ExpansionResponse response = expansionHandler.expand(fieldValue, expansionRequestMetadata, null);
    assertNotNull(response);
    assertFalse(response.isSuccess());
    assertNotNull(response.getErrorMessage());
    mockRestStatic.close();
  }

  private Map<String, Object> getResponse() {
    List<Map<String, Object>> services = new ArrayList<>();
    for (BackstageCatalogComponentEntity service : getBackstageCatalogEntities()) {
      String responseString = gson.toJson(service);
      Map<String, Object> responseMap =
          gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
      services.add(responseMap);
    }
    return Map.of("items", services);
  }

  private List<BackstageCatalogComponentEntity> getBackstageCatalogEntities() {
    BackstageCatalogComponentEntity entity = BackstageCatalogComponentEntity.builder()
                                                 .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
                                                 .metadata(BackstageCatalogEntity.Metadata.builder()
                                                               .uid(IDP_SERVICE_ENTITY_ID)
                                                               .name(IDP_SERVICE_ENTITY_NAME)
                                                               .build())
                                                 .spec(BackstageCatalogComponentEntity.Spec.builder()
                                                           .domain(ORG_ID)
                                                           .system(PROJECT_ID)
                                                           .type("service")
                                                           .owner("team-a")
                                                           .build())
                                                 .build();
    return Arrays.asList(entity, null);
  }

  private ScorecardSummaryInfo getTestScorecardSummaryInfo() {
    ScorecardSummaryInfo scorecardSummaryInfo = new ScorecardSummaryInfo();
    scorecardSummaryInfo.setScorecardName(TEST_SCORECARD_NAME);
    scorecardSummaryInfo.setScorecardIdentifier(TEST_SCORECARD_IDENTIFIER);
    scorecardSummaryInfo.setScore(TEST_SCORE_FOR_SCORECARD);
    CheckStatus checkStatus = new CheckStatus();
    checkStatus.setName(TEST_CHECK_NAME);
    checkStatus.setReason(TEST_CHECK_REASON);
    checkStatus.setWeight(TEST_WEIGHT_FOR_CHECKS);
    checkStatus.setStatus(CheckStatus.StatusEnum.PASS);
    scorecardSummaryInfo.setChecksStatuses(Collections.singletonList(checkStatus));
    return scorecardSummaryInfo;
  }

  private JsonNode getJson(String filename) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String content =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(content, JsonNode.class);
  }
}
