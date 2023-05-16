/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.EntityConfig;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.helpers.FreezeFilterHelper;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.support.PageableExecutionUtils;

public class FreezeEvaluateServiceImplTest {
  @Spy @InjectMocks FreezeEvaluateServiceImpl freezeEvaluateService;

  @Mock FreezeCRUDServiceImpl freezeCRUDService;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "oId";
  private final String PROJ_IDENTIFIER = "pId";
  private final String FREEZE_IDENTIFIER = "id";
  private final String PIPELINE_IDENTIFIER = "pipelineid";
  public DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                                     .parseCaseInsensitive()
                                     .appendPattern("yyyy-MM-dd hh:mm a")
                                     .toFormatter(Locale.ENGLISH);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    on(freezeEvaluateService).set("freezeCRUDService", freezeCRUDService);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMatchesEntitiesWhenEntityMapContainsRefOfEntityConfigAndFilterTypeEquals() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.SERVICE, Arrays.asList("serviceId2", "serviceId3"));
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setEntityReference(Arrays.asList("serviceId1", "serviceId2"));
    entityConfig.setFreezeEntityType(FreezeEntityType.SERVICE);
    entityConfig.setFilterType(FilterType.EQUALS);
    assertThat(freezeEvaluateService.matchesEntities(entityMap, entityConfig)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMatchesEntitiesWhenEntityMapContainsRefOfEntityConfigAndFilterTypeNotEquals() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.SERVICE, Arrays.asList("serviceId2", "serviceId3"));
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setEntityReference(Arrays.asList("serviceId1", "serviceId2"));
    entityConfig.setFreezeEntityType(FreezeEntityType.SERVICE);
    entityConfig.setFilterType(FilterType.NOT_EQUALS);
    assertThat(freezeEvaluateService.matchesEntities(entityMap, entityConfig)).isEqualTo(false);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMatchesEntitiesWhenEntityMapDoesntContainsFreezeEntityType() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setEntityReference(Arrays.asList("serviceId1", "serviceId2"));
    entityConfig.setFreezeEntityType(FreezeEntityType.SERVICE);
    entityConfig.setFilterType(FilterType.NOT_EQUALS);
    assertThat(freezeEvaluateService.matchesEntities(entityMap, entityConfig)).isEqualTo(false);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMatchesEntitiesWhenEntityMapMatchesAllEntityConfigsInFreezeRule() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    entityMap.put(FreezeEntityType.SERVICE, Arrays.asList("serviceId1", "serviceId3"));
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setEntityReference(Arrays.asList("serviceId1", "serviceId2"));
    entityConfig.setFreezeEntityType(FreezeEntityType.SERVICE);
    entityConfig.setFilterType(FilterType.EQUALS);
    EntityConfig entityConfig1 = new EntityConfig();
    entityConfig1.setEntityReference(Arrays.asList("envId3", "envId4"));
    entityConfig1.setFreezeEntityType(FreezeEntityType.ENVIRONMENT);
    entityConfig1.setFilterType(FilterType.NOT_EQUALS);
    EntityConfig entityConfig2 = new EntityConfig();
    entityConfig2.setFreezeEntityType(FreezeEntityType.ENV_TYPE);
    entityConfig2.setFilterType(FilterType.ALL);
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig, entityConfig1, entityConfig2));
    freezeEntityRule.setName("Rule");
    assertThat(freezeEvaluateService.matchesEntities(entityMap, Arrays.asList(freezeEntityRule))).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testMatchesEntitiesWhenEntityMapDoesNotMatchesAllEntityConfigsInFreezeRule() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setEntityReference(Arrays.asList("serviceId1", "serviceId2"));
    entityConfig.setFreezeEntityType(FreezeEntityType.SERVICE);
    entityConfig.setFilterType(FilterType.EQUALS);
    EntityConfig entityConfig1 = new EntityConfig();
    entityConfig1.setEntityReference(Arrays.asList("envId3", "envId4"));
    entityConfig1.setFreezeEntityType(FreezeEntityType.ENVIRONMENT);
    entityConfig1.setFilterType(FilterType.NOT_EQUALS);
    EntityConfig entityConfig2 = new EntityConfig();
    entityConfig2.setFreezeEntityType(FreezeEntityType.ENV_TYPE);
    entityConfig2.setFilterType(FilterType.ALL);
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig, entityConfig1, entityConfig2));
    freezeEntityRule.setName("Rule");
    assertThat(freezeEvaluateService.matchesEntities(entityMap, Arrays.asList(freezeEntityRule))).isEqualTo(false);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testSkipEntityConfigsWithFilterTypeAll() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setFreezeEntityType(FreezeEntityType.ENV_TYPE);
    entityConfig.setFilterType(FilterType.ALL);
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig));
    freezeEntityRule.setName("Rule");
    assertThat(freezeEvaluateService.matchesEntities(entityMap, Arrays.asList(freezeEntityRule))).isEqualTo(true);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveFreezeEntitiesWhenOneIsActive() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    FreezeSummaryResponseDTO activeFreezeSummaryResponseDTO = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FREEZE_IDENTIFIER, Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> freezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(activeFreezeSummaryResponseDTO),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt)), () -> 1L);
    when(freezeCRUDService.list(any(), any())).thenReturn(freezeConfigs);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, entityMap);
    assertThat(activeFreezeConfigs.size()).isEqualTo(1);
    assertThat(activeFreezeConfigs.get(0)).isEqualTo(activeFreezeSummaryResponseDTO);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveFreezeEntitiesWhenNoneIsActive() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    FreezeSummaryResponseDTO activeFreezeSummaryResponseDTO = constructInActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FREEZE_IDENTIFIER, Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> freezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(activeFreezeSummaryResponseDTO),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt)), () -> 1L);
    when(freezeCRUDService.list(any(), any())).thenReturn(freezeConfigs);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, entityMap);
    assertThat(activeFreezeConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveManualFreezeEntitiesAtProjectScope() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveManualFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, entityMap);
    assertThat(activeFreezeConfigs.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveManualFreezeEntitiesAtOrgScope() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveManualFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, null, entityMap);
    assertThat(activeFreezeConfigs.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveManualFreezeEntitiesAtAccountScope() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveManualFreezeEntities(ACCOUNT_ID, null, null, entityMap);
    assertThat(activeFreezeConfigs.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveGlobalFreezeEntitiesAtProjectScope() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null)).thenReturn(accountLevelActiveFreezeWindow);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.anyGlobalFreezeActive(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertThat(activeFreezeConfigs.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveGlobalFreezeEntitiesAtOrgScope() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null)).thenReturn(accountLevelActiveFreezeWindow);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.anyGlobalFreezeActive(ACCOUNT_ID, ORG_IDENTIFIER, null);
    assertThat(activeFreezeConfigs.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getActiveGlobalFreezeEntitiesAtAccountScope() {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ENVIRONMENT, Arrays.asList("envId1", "envId2"));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null)).thenReturn(accountLevelActiveFreezeWindow);
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.anyGlobalFreezeActive(ACCOUNT_ID, null, null);
    assertThat(activeFreezeConfigs.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getAnyActiveFreezeEntitiesAtProjectScope() {
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);

    FreezeSummaryResponseDTO projectLevelActiveGlobalFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "gId1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "gId2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "gId3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null))
        .thenReturn(accountLevelActiveGlobalFreezeWindow);

    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "pipelineId");
    assertThat(activeFreezeConfigs.size()).isEqualTo(6);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getAnyActiveFreezeEntitiesAtOrgScope() {
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);

    FreezeSummaryResponseDTO projectLevelActiveGlobalFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "gId1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "gId2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "gId3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null))
        .thenReturn(accountLevelActiveGlobalFreezeWindow);

    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, null, "pipelineId");
    assertThat(activeFreezeConfigs.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getAnyActiveFreezeEntitiesAtAccountScope() {
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);

    FreezeSummaryResponseDTO projectLevelActiveGlobalFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "gId1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "gId2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "gId3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null))
        .thenReturn(accountLevelActiveGlobalFreezeWindow);

    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveFreezeEntities(ACCOUNT_ID, null, null, "pipelineId");
    assertThat(activeFreezeConfigs.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void getAnyActiveFreezeEntitiesAtProjectScopeForPipelineEntity() {
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindowForPipelineEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL, PIPELINE_IDENTIFIER);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);

    FreezeSummaryResponseDTO projectLevelActiveGlobalFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "gId1", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO orgLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, ORG_IDENTIFIER, null, "gId2", Scope.PROJECT, FreezeType.GLOBAL);
    FreezeSummaryResponseDTO accountLevelActiveGlobalFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "gId3", Scope.PROJECT, FreezeType.GLOBAL);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(projectLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .thenReturn(orgLevelActiveGlobalFreezeWindow);
    when(freezeCRUDService.getGlobalFreezeSummary(ACCOUNT_ID, null, null))
        .thenReturn(accountLevelActiveGlobalFreezeWindow);

    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    assertThat(activeFreezeConfigs.size()).isEqualTo(6);
  }

  private FreezeSummaryResponseDTO constructActiveFreezeWindowForPipelineEntity(String accountId, String orgId,
      String projectId, String freezeId, Scope freezeScope, FreezeType freezeType, String pipelineId) {
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setFreezeEntityType(FreezeEntityType.PIPELINE);
    entityConfig.setFilterType(FilterType.EQUALS);
    entityConfig.setEntityReference(List.of(pipelineId));
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig));
    freezeEntityRule.setName("Rule");
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setDuration("30m");
    freezeWindow.setStartTime(getCurrentTimeInString());
    freezeWindow.setTimeZone("UTC");
    CurrentOrUpcomingWindow currentOrUpcomingWindow =
        FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(Arrays.asList(freezeWindow));
    return FreezeSummaryResponseDTO.builder()
        .accountId(accountId)
        .projectIdentifier(projectId)
        .accountId(orgId)
        .identifier(freezeId)
        .freezeScope(freezeScope)
        .windows(Arrays.asList(freezeWindow))
        .status(FreezeStatus.ENABLED)
        .rules(Arrays.asList(freezeEntityRule))
        .yaml("yaml")
        .name("freeze")
        .type(freezeType)
        .currentOrUpcomingWindow(currentOrUpcomingWindow)
        .build();
  }

  private FreezeSummaryResponseDTO constructActiveFreezeWindow(
      String accountId, String orgId, String projectId, String freezeId, Scope freezeScope, FreezeType freezeType) {
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setFreezeEntityType(FreezeEntityType.ENV_TYPE);
    entityConfig.setFilterType(FilterType.ALL);
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig));
    freezeEntityRule.setName("Rule");
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setDuration("30m");
    freezeWindow.setStartTime(getCurrentTimeInString());
    freezeWindow.setTimeZone("UTC");
    CurrentOrUpcomingWindow currentOrUpcomingWindow =
        FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(Arrays.asList(freezeWindow));
    return FreezeSummaryResponseDTO.builder()
        .accountId(accountId)
        .projectIdentifier(projectId)
        .accountId(orgId)
        .identifier(freezeId)
        .freezeScope(freezeScope)
        .windows(Arrays.asList(freezeWindow))
        .status(FreezeStatus.ENABLED)
        .rules(Arrays.asList(freezeEntityRule))
        .yaml("yaml")
        .name("freeze")
        .type(freezeType)
        .currentOrUpcomingWindow(currentOrUpcomingWindow)
        .build();
  }

  private FreezeSummaryResponseDTO constructInActiveFreezeWindow(
      String accountId, String orgId, String projectId, String freezeId, Scope freezeScope, FreezeType type) {
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setFreezeEntityType(FreezeEntityType.ENV_TYPE);
    entityConfig.setFilterType(FilterType.ALL);
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig));
    freezeEntityRule.setName("Rule");
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime(getCurrentTimeInString());
    freezeWindow.setStartTime(getCurrentTimeInString());
    freezeWindow.setTimeZone("UTC");
    CurrentOrUpcomingWindow currentOrUpcomingWindow =
        FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(Arrays.asList(freezeWindow));
    return FreezeSummaryResponseDTO.builder()
        .accountId(accountId)
        .projectIdentifier(projectId)
        .accountId(orgId)
        .identifier(freezeId)
        .freezeScope(freezeScope)
        .windows(Arrays.asList(freezeWindow))
        .rules(Arrays.asList(freezeEntityRule))
        .yaml("yaml")
        .status(FreezeStatus.ENABLED)
        .name("freeze")
        .type(type)
        .currentOrUpcomingWindow(currentOrUpcomingWindow)
        .build();
  }

  private String getCurrentTimeInString() {
    LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
    return dtf.format(now);
  }
}
