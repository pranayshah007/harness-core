/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.idp.common.Constants.CATALOG_IDENTIFIER;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_TECH_DOCS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_BRANCH_PROTECTED;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.idp.backstagebeans.BackstageCatalogComponentEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatusEntityByIdentifier;
import io.harness.idp.scorecard.checks.repositories.CheckStatusRepository;
import io.harness.idp.scorecard.checks.service.CheckServiceImpl;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.repositories.EntityIdentifierAndCheckStatus;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckStats;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.InputDetails;
import io.harness.spec.server.idp.v1.model.Rule;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.utils.PageUtils;

import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class CheckServiceImplTest extends CategoryTest {
  private static final String DEVELOP_BRANCH = "develop";
  private static final String RULE_IDENTIFIER1 = "rule1";
  private static final String RULE_IDENTIFIER2 = "rule2";
  private CheckServiceImpl checkServiceImpl;
  @Mock CheckRepository checkRepository;
  @Mock CheckStatusRepository checkStatusRepository;
  @Mock ScorecardService scorecardService;
  @Mock NamespaceService namespaceService;
  @Mock ScoreComputerService scoreComputerService;
  @Mock ScoreService scoreService;
  @Mock NGSettingsClient settingsClient;
  @Mock EntitySetupUsageClient entitySetupUsageClient;
  @Mock DataPointService dataPointService;

  @Mock TransactionTemplate transactionTemplate;

  @Mock OutboxService outboxService;
  @Captor private ArgumentCaptor<CheckEntity> checkEntityCaptor;
  @Captor private ArgumentCaptor<List<CheckStatusEntity>> checkStatusEntitiesCaptor;
  private static final String ACCOUNT_ID = "123";
  private static final String GITHUB_CHECK_NAME = "Github Checks";
  private static final String GITHUB_CHECK_ID = "github_checks";
  private static final String CATALOG_CHECK_NAME = "Catalog Checks";
  private static final String CATALOG_CHECK_ID = "catalog_checks";
  private static final String DATA_SOURCE_ID = "github";
  private static final String DATA_POINT_ID = "isFileExist";
  private static final String README_FILE = "README.md";
  private static final String SERVICE_MATURITY_SCORECARD = "service-maturity";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    checkServiceImpl = new CheckServiceImpl(checkRepository, checkStatusRepository, scorecardService,
        scoreComputerService, namespaceService, scoreService, settingsClient, entitySetupUsageClient, dataPointService,
        transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCheck() {
    when(checkRepository.update(any())).thenReturn(CheckEntity.builder().build());
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(getDataPointMap());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(dataPointService.getDataPoint(ACCOUNT_ID, DATA_SOURCE_ID, DATA_POINT_ID))
        .thenReturn(
            DataPointEntity.builder().identifier(DATA_POINT_ID).inputDetails(List.of(getInputDetails())).build());
    when(checkRepository.save(any())).thenReturn(getCheckEntities().get(0));
    checkServiceImpl.createCheck(getCheckDetails(README_FILE), ACCOUNT_ID);
    verify(checkRepository).save(checkEntityCaptor.capture());
    assertEquals(GITHUB_CHECK_ID, checkEntityCaptor.getValue().getIdentifier());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCheckThrowsException() {
    when(checkRepository.update(any())).thenReturn(CheckEntity.builder().build());
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(new HashMap<>());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(dataPointService.getDataPoint(ACCOUNT_ID, DATA_SOURCE_ID, DATA_POINT_ID))
        .thenReturn(
            DataPointEntity.builder().identifier(DATA_POINT_ID).inputDetails(List.of(getInputDetails())).build());
    when(checkRepository.save(any())).thenReturn(getCheckEntities().get(0));
    checkServiceImpl.createCheck(getCheckDetails(README_FILE), ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCheck() {
    when(checkRepository.update(any())).thenReturn(CheckEntity.builder().build());
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(getDataPointMap());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(checkRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(getCheckEntities().get(0));
    when(dataPointService.getDataPoint(ACCOUNT_ID, DATA_SOURCE_ID, DATA_POINT_ID))
        .thenReturn(
            DataPointEntity.builder().identifier(DATA_POINT_ID).inputDetails(List.of(getInputDetails())).build());
    checkServiceImpl.updateCheck(getCheckDetails(README_FILE), ACCOUNT_ID);
    verify(checkRepository).update(checkEntityCaptor.capture());
    assertEquals(GITHUB_CHECK_ID, checkEntityCaptor.getValue().getIdentifier());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCheckThrowsException() {
    when(checkRepository.update(any())).thenReturn(CheckEntity.builder().build());
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(getDataPointMap());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(dataPointService.getDataPoint(ACCOUNT_ID, DATA_SOURCE_ID, DATA_POINT_ID))
        .thenReturn(
            DataPointEntity.builder().identifier(DATA_POINT_ID).inputDetails(List.of(getInputDetails())).build());
    when(checkRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(getCheckEntities().get(0));
    checkServiceImpl.updateCheck(getCheckDetails(null), ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateDefaultCheckThrowsException() {
    when(checkRepository.update(any())).thenReturn(null);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(getDataPointMap());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(dataPointService.getDataPoint(ACCOUNT_ID, DATA_SOURCE_ID, DATA_POINT_ID))
        .thenReturn(
            DataPointEntity.builder().identifier(DATA_POINT_ID).inputDetails(List.of(getInputDetails())).build());
    when(checkRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(getCheckEntities().get(0));
    checkServiceImpl.updateCheck(getCheckDetails(README_FILE), ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllChecks() {
    when(checkRepository.findAll(any(), any())).thenReturn(getPageCheckEntity(null));
    Page<CheckEntity> checkEntityPage =
        checkServiceImpl.getChecksByAccountId(null, ACCOUNT_ID, PageUtils.getPageRequest(0, 10, null), "java");
    assertEquals(2, checkEntityPage.getTotalElements());
    assertEquals(2, checkEntityPage.getContent().size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomChecks() {
    when(checkRepository.findAll(any(), any())).thenReturn(getPageCheckEntity(true));
    Page<CheckEntity> checkEntityPage =
        checkServiceImpl.getChecksByAccountId(true, ACCOUNT_ID, PageUtils.getPageRequest(0, 10, null), null);
    assertEquals(1, checkEntityPage.getTotalElements());
    assertEquals(1, checkEntityPage.getContent().size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetDefaultChecks() {
    when(checkRepository.findAll(any(), any())).thenReturn(getPageCheckEntity(false));
    Page<CheckEntity> checkEntityPage =
        checkServiceImpl.getChecksByAccountId(false, ACCOUNT_ID, PageUtils.getPageRequest(0, 10, null), null);
    assertEquals(1, checkEntityPage.getTotalElements());
    assertEquals(1, checkEntityPage.getContent().size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetActiveChecks() {
    when(checkRepository.findByAccountIdentifierInAndIsDeletedAndIdentifierIn(
             Set.of(ACCOUNT_ID, GLOBAL_ACCOUNT_ID), false, List.of(GITHUB_CHECK_ID, CATALOG_CHECK_ID)))
        .thenReturn(getCheckEntities());
    List<CheckEntity> checkEntities =
        checkServiceImpl.getActiveChecks(ACCOUNT_ID, List.of(GITHUB_CHECK_ID, CATALOG_CHECK_ID));
    assertEquals(2, checkEntities.size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomCheckDetails() {
    when(checkRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, GITHUB_CHECK_ID))
        .thenReturn(getCheckEntities().get(0));
    CheckDetails checkDetails = checkServiceImpl.getCheckDetails(ACCOUNT_ID, GITHUB_CHECK_ID, Boolean.TRUE);
    assertEquals(GITHUB_CHECK_ID, checkDetails.getIdentifier());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetDefaultCheckDetails() {
    when(checkRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, GITHUB_CHECK_ID))
        .thenReturn(getCheckEntities().get(1));
    CheckDetails checkDetails = checkServiceImpl.getCheckDetails(GLOBAL_ACCOUNT_ID, GITHUB_CHECK_ID, Boolean.FALSE);
    assertEquals(CATALOG_CHECK_ID, checkDetails.getIdentifier());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCheckDetailsThrowsException() {
    when(checkRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, CATALOG_CHECK_ID)).thenReturn(null);
    checkServiceImpl.getCheckDetails(ACCOUNT_ID, CATALOG_CHECK_ID, Boolean.FALSE);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetChecksByAccountIdAndIdentifiers() {
    when(checkRepository.findByAccountIdentifierInAndIdentifierIn(
             Set.of(ACCOUNT_ID, GLOBAL_ACCOUNT_ID), Set.of(GITHUB_CHECK_ID, CATALOG_CHECK_ID)))
        .thenReturn(getCheckEntities());
    List<CheckEntity> checkEntities =
        checkServiceImpl.getChecksByAccountIdAndIdentifiers(ACCOUNT_ID, Set.of(GITHUB_CHECK_ID, CATALOG_CHECK_ID));
    assertEquals(2, checkEntities.size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCheckStats() {
    when(scorecardService.getScorecardIdentifiers(ACCOUNT_ID, GITHUB_CHECK_ID, Boolean.TRUE))
        .thenReturn(List.of(SERVICE_MATURITY_SCORECARD));
    when(scorecardService.getScorecardFilters(ACCOUNT_ID, List.of(SERVICE_MATURITY_SCORECARD)))
        .thenReturn(getFilters());
    when(scoreComputerService.getAllEntities(any(), any(), any())).thenReturn(getBackstageCatalogEntities());
    when(scoreService.getCheckStatusForEntityIdentifiersAndScorecardIdentifiers(
             any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEntityIdentifierAndCheckStatus());
    List<CheckStats> checkStats = checkServiceImpl.getCheckStats(ACCOUNT_ID, GITHUB_CHECK_ID, Boolean.TRUE);
    assertEquals(1, checkStats.size());
    assertEquals(IDP_SERVICE_ENTITY_NAME, checkStats.get(0).getName());
    assertEquals(CheckStatus.StatusEnum.PASS.toString(), checkStats.get(0).getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCheckGraph() {
    when(checkStatusRepository.findByAccountIdentifierAndIdentifierAndCustom(ACCOUNT_ID, GITHUB_CHECK_ID, true))
        .thenReturn(getCheckStatusEntities());
    List<CheckGraph> checkGraphs = checkServiceImpl.getCheckGraph(ACCOUNT_ID, GITHUB_CHECK_ID, true);
    assertEquals(1, checkGraphs.size());
    assertEquals(5, (int) checkGraphs.get(0).getCount());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testComputeCheckStatus() {
    when(namespaceService.getAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    when(checkRepository.findByAccountIdentifierInAndIsDeleted(anySet(), anyBoolean())).thenReturn(getCheckEntities());
    when(scorecardService.getScorecardIdentifiers(any(), any(), any()))
        .thenReturn(List.of(SERVICE_MATURITY_SCORECARD))
        .thenReturn(new ArrayList<>());
    when(scoreService.getCheckStatusForEntityIdentifiersAndScorecardIdentifiers(
             any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(getEntityIdentifierAndCheckStatus());
    checkServiceImpl.computeCheckStatus();
    verify(checkStatusRepository).saveAll(checkStatusEntitiesCaptor.capture());
    assertEquals(GITHUB_CHECK_ID, checkStatusEntitiesCaptor.getValue().get(0).getIdentifier());
    assertEquals(1, checkStatusEntitiesCaptor.getValue().get(0).getPassCount());
    assertEquals(1, checkStatusEntitiesCaptor.getValue().get(0).getTotal());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCheckStatusByAccountIdAndIdentifiers() {
    when(checkStatusRepository.findByAccountIdentifierAndIdentifierIn(any(), any()))
        .thenReturn(List.of(CheckStatusEntityByIdentifier.builder()
                                .identifier(GITHUB_CHECK_ID)
                                .custom(true)
                                .checkStatusEntity(getCheckStatusEntities().get(0))
                                .build()));
    Map<String, CheckStatusEntity> checkStatusEntityMap =
        checkServiceImpl.getCheckStatusByAccountIdAndIdentifiers(ACCOUNT_ID, List.of(GITHUB_CHECK_ID));
    CheckStatusEntity checkStatusEntity = checkStatusEntityMap.get(ACCOUNT_ID + DOT_SEPARATOR + GITHUB_CHECK_ID);
    assertNotNull(checkStatusEntity);
    assertEquals(5, checkStatusEntity.getPassCount());
    assertEquals(10, checkStatusEntity.getTotal());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCheck() {
    Call<ResponseDTO<SettingValueResponseDTO>> response = getSettingValueResponseDTOCall(true);
    when(settingsClient.getSetting(any(), any(), any(), any())).thenReturn(response);
    when(checkRepository.updateDeleted(ACCOUNT_ID, GITHUB_CHECK_ID)).thenReturn(UpdateResult.acknowledged(1, 1L, null));
    assertThatCode(() -> checkServiceImpl.deleteCustomCheck(ACCOUNT_ID, GITHUB_CHECK_ID, true))
        .doesNotThrowAnyException();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCheckWhenForceDeleteIsDisabled() {
    Call<ResponseDTO<SettingValueResponseDTO>> response = getSettingValueResponseDTOCall(false);
    when(settingsClient.getSetting(any(), any(), any(), any())).thenReturn(response);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(checkRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(getCheckEntities().get(0));
    checkServiceImpl.deleteCustomCheck(ACCOUNT_ID, GITHUB_CHECK_ID, true);
  }

  @Test(expected = ReferencedEntityException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCheckThrowsReferencedEntityException() {
    Call<ResponseDTO<Boolean>> response = getResponseDTOCall(true);
    when(entitySetupUsageClient.isEntityReferenced(any(), any(), any())).thenReturn(response);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(checkRepository.findByAccountIdentifierAndIdentifier(any(), any())).thenReturn(getCheckEntities().get(0));
    checkServiceImpl.deleteCustomCheck(ACCOUNT_ID, GITHUB_CHECK_ID, false);
  }

  private CheckDetails getCheckDetails(String conditionalInput) {
    List<Rule> rules = new ArrayList<>();
    Rule rule = new Rule();
    rule.setIdentifier(RULE_IDENTIFIER1);
    rule.setDataSourceIdentifier(DATA_SOURCE_ID);
    rule.setDataPointIdentifier(DATA_POINT_ID);
    rule.setOperator("==");
    rule.setConditionalInputValue(conditionalInput);
    rule.setValue("true");
    rules.add(rule);
    CheckDetails checkDetails = new CheckDetails();
    checkDetails.setName(GITHUB_CHECK_NAME);
    checkDetails.setIdentifier(GITHUB_CHECK_ID);
    checkDetails.setRuleStrategy(CheckDetails.RuleStrategyEnum.ALL_OF);
    checkDetails.setRules(rules);
    checkDetails.setCustom(true);
    return checkDetails;
  }

  private Map<String, DataPoint> getDataPointMap() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT_ID);
    dataPoint.setIsConditional(true);
    return Map.of(DATA_SOURCE_ID + DOT_SEPARATOR + DATA_POINT_ID, dataPoint);
  }

  private Page<CheckEntity> getPageCheckEntity(Boolean custom) {
    Rule rule1 = new Rule();
    rule1.setIdentifier(RULE_IDENTIFIER1);
    rule1.setDataSourceIdentifier(GITHUB_IDENTIFIER);
    rule1.setDataPointIdentifier(IS_BRANCH_PROTECTED);
    List<CheckEntity> entities = new ArrayList<>();
    CheckEntity customCheck = CheckEntity.builder()
                                  .identifier(GITHUB_CHECK_ID)
                                  .name(GITHUB_CHECK_NAME)
                                  .accountIdentifier(ACCOUNT_ID)
                                  .rules(List.of(rule1))
                                  .isCustom(true)
                                  .build();
    Rule rule2 = new Rule();
    rule1.setIdentifier(RULE_IDENTIFIER2);
    rule2.setDataSourceIdentifier(CATALOG_IDENTIFIER);
    rule2.setDataPointIdentifier(CATALOG_TECH_DOCS);
    CheckEntity defaultCheck = CheckEntity.builder()
                                   .identifier(CATALOG_CHECK_ID)
                                   .name(CATALOG_CHECK_NAME)
                                   .accountIdentifier(ACCOUNT_ID)
                                   .isCustom(false)
                                   .rules(List.of(rule2))
                                   .build();
    if (custom == null) {
      entities.add(customCheck);
      entities.add(defaultCheck);
    } else {
      CheckEntity entity = custom ? customCheck : defaultCheck;
      entities.add(entity);
    }
    return new PageImpl<>(entities);
  }

  private List<CheckEntity> getCheckEntities() {
    Rule rule1 = new Rule();
    rule1.setIdentifier(RULE_IDENTIFIER1);
    rule1.setDataSourceIdentifier(GITHUB_IDENTIFIER);
    rule1.setDataPointIdentifier(IS_BRANCH_PROTECTED);
    CheckEntity entity1 = CheckEntity.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .identifier(GITHUB_CHECK_ID)
                              .name(GITHUB_CHECK_NAME)
                              .rules(List.of(rule1))
                              .isCustom(true)
                              .build();
    Rule rule2 = new Rule();
    rule1.setIdentifier(RULE_IDENTIFIER2);
    rule2.setDataSourceIdentifier(CATALOG_IDENTIFIER);
    rule2.setDataPointIdentifier(CATALOG_TECH_DOCS);
    CheckEntity entity2 = CheckEntity.builder()
                              .accountIdentifier(GLOBAL_ACCOUNT_ID)
                              .identifier(CATALOG_CHECK_ID)
                              .name(CATALOG_CHECK_NAME)
                              .rules(List.of(rule2))
                              .isCustom(false)
                              .build();
    return List.of(entity1, entity2);
  }

  private Call<ResponseDTO<Boolean>> getResponseDTOCall(boolean setValue) {
    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(setValue)));
    } catch (Exception ignored) {
    }
    return request;
  }

  private Call<ResponseDTO<SettingValueResponseDTO>> getSettingValueResponseDTOCall(boolean setValue) {
    Call<ResponseDTO<SettingValueResponseDTO>> request = mock(Call.class);
    try {
      when(request.execute())
          .thenReturn(Response.success(ResponseDTO.newResponse(SettingValueResponseDTO.builder()
                                                                   .value(String.valueOf(setValue))
                                                                   .valueType(SettingValueType.BOOLEAN)
                                                                   .build())));
    } catch (Exception ignored) {
    }
    return request;
  }

  private InputDetails getInputDetails() {
    InputDetails inputDetails = new InputDetails();
    inputDetails.key(BRANCH_NAME);
    inputDetails.key(DEVELOP_BRANCH);
    return inputDetails;
  }

  private List<ScorecardFilter> getFilters() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setType("service");
    return List.of(filter);
  }

  private Set<BackstageCatalogEntity> getBackstageCatalogEntities() {
    BackstageCatalogComponentEntity entity = new BackstageCatalogComponentEntity();
    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setUid(IDP_SERVICE_ENTITY_ID);
    metadata.setName(IDP_SERVICE_ENTITY_NAME);
    entity.setMetadata(metadata);

    BackstageCatalogComponentEntity.Spec spec = new BackstageCatalogComponentEntity.Spec();
    spec.setType("service");
    spec.setLifecycle("experimental");
    spec.setOwner("team-a");
    spec.setSystem("Unknown");
    entity.setSpec(spec);
    return Set.of(entity);
  }

  private List<EntityIdentifierAndCheckStatus> getEntityIdentifierAndCheckStatus() {
    return List.of(EntityIdentifierAndCheckStatus.builder()
                       .entityIdentifier(IDP_SERVICE_ENTITY_ID)
                       .status(CheckStatus.StatusEnum.PASS)
                       .build());
  }

  private List<CheckStatusEntity> getCheckStatusEntities() {
    CheckStatusEntity checkStatusEntity = CheckStatusEntity.builder()
                                              .accountIdentifier(ACCOUNT_ID)
                                              .identifier(GITHUB_CHECK_ID)
                                              .isCustom(true)
                                              .name(GITHUB_CHECK_NAME)
                                              .total(10)
                                              .passCount(5)
                                              .build();
    return List.of(checkStatusEntity);
  }
}
