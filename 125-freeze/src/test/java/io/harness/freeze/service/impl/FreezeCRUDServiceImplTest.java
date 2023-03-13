/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.response.FrozenExecutionDetails;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.repositories.FreezeConfigRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FreezeCRUDServiceImplTest extends CategoryTest {
  @Mock private NotificationHelper notificationHelper;
  @Mock private FrozenExecutionService frozenExecutionService;
  @Mock private FreezeConfigRepository freezeConfigRepository;
  @InjectMocks private FreezeCRUDServiceImpl freezeCRUDService;

  static final String ACCOUNT_ID = "accountId";
  static final String ORG_ID = "orgId";
  static final String PROJECT_ID = "projectId";
  static final String PLAN_EXECUTION_ID = "planExecutionId";
  static final String STAGE_EXECUTION_ID = "stageExecutionId";
  static final String PIPELINE = "pipeline";
  static final String URL = "url";
  static final String MANUAL_FREEZE_1 = "manualFreeze1";
  static final String MANUAL_FREEZE_2 = "manualFreeze2";
  static final String GLOBAL_FREEZE_1 = "globalFreeze1";
  static final String GLOBAL_FREEZE_2 = "globalFreeze2";
  static final String YAML = "freeze:\n"
      + "  name: freeze\n"
      + "  identifier: freeze\n";
  static final Map<String, String> EMPTY_MAP = new HashMap<>();
  static final List<FreezeSummaryResponseDTO> manualFreezeList = Arrays.asList(FreezeSummaryResponseDTO.builder()
                                                                                   .freezeScope(Scope.PROJECT)
                                                                                   .identifier(MANUAL_FREEZE_1)
                                                                                   .type(FreezeType.MANUAL)
                                                                                   .lastUpdatedAt(1L)
                                                                                   .build(),
      FreezeSummaryResponseDTO.builder()
          .identifier(MANUAL_FREEZE_2)
          .type(FreezeType.MANUAL)
          .freezeScope(Scope.ORG)
          .lastUpdatedAt(2L)
          .build());
  static final List<FreezeSummaryResponseDTO> globalFreezeList = Arrays.asList(FreezeSummaryResponseDTO.builder()
                                                                                   .freezeScope(Scope.PROJECT)
                                                                                   .identifier(GLOBAL_FREEZE_1)
                                                                                   .type(FreezeType.GLOBAL)
                                                                                   .lastUpdatedAt(1L)
                                                                                   .build(),
      FreezeSummaryResponseDTO.builder()
          .identifier(GLOBAL_FREEZE_2)
          .freezeScope(Scope.ORG)
          .type(FreezeType.GLOBAL)
          .lastUpdatedAt(2L)
          .build());
  static final List<FreezeSummaryResponseDTO> freezeListLatest = Arrays.asList(FreezeSummaryResponseDTO.builder()
                                                                                   .accountId(ACCOUNT_ID)
                                                                                   .orgIdentifier(ORG_ID)
                                                                                   .projectIdentifier(PROJECT_ID)
                                                                                   .freezeScope(Scope.PROJECT)
                                                                                   .identifier(MANUAL_FREEZE_1)
                                                                                   .type(FreezeType.MANUAL)
                                                                                   .description("")
                                                                                   .tags(EMPTY_MAP)
                                                                                   .yaml(YAML)
                                                                                   .lastUpdatedAt(2L)
                                                                                   .build(),
      FreezeSummaryResponseDTO.builder()
          .accountId(ACCOUNT_ID)
          .orgIdentifier(ORG_ID)
          .identifier(MANUAL_FREEZE_2)
          .freezeScope(Scope.ORG)
          .type(FreezeType.MANUAL)
          .description("")
          .tags(EMPTY_MAP)
          .yaml(YAML)
          .lastUpdatedAt(3L)
          .build(),
      FreezeSummaryResponseDTO.builder()
          .accountId(ACCOUNT_ID)
          .orgIdentifier(ORG_ID)
          .projectIdentifier(PROJECT_ID)
          .freezeScope(Scope.PROJECT)
          .identifier(GLOBAL_FREEZE_1)
          .type(FreezeType.GLOBAL)
          .description("")
          .yaml(YAML)
          .tags(EMPTY_MAP)
          .lastUpdatedAt(2L)
          .build(),
      FreezeSummaryResponseDTO.builder()
          .accountId(ACCOUNT_ID)
          .orgIdentifier(ORG_ID)
          .identifier(GLOBAL_FREEZE_2)
          .freezeScope(Scope.ORG)
          .type(FreezeType.GLOBAL)
          .description("")
          .yaml(YAML)
          .tags(EMPTY_MAP)
          .lastUpdatedAt(3L)
          .build());
  static final FrozenExecution FROZEN_EXECUTION = FrozenExecution.builder()
                                                      .accountId(ACCOUNT_ID)
                                                      .orgId(ORG_ID)
                                                      .projectId(PROJECT_ID)
                                                      .planExecutionId(PLAN_EXECUTION_ID)
                                                      .stageExecutionId(STAGE_EXECUTION_ID)
                                                      .pipelineId(PIPELINE)
                                                      .manualFreezeList(manualFreezeList)
                                                      .globalFreezeList(globalFreezeList)
                                                      .build();
  static final List<FreezeConfigEntity> manualFreezeConfig1 = Arrays.asList(FreezeConfigEntity.builder()
                                                                                .accountId(ACCOUNT_ID)
                                                                                .orgIdentifier(ORG_ID)
                                                                                .projectIdentifier(PROJECT_ID)
                                                                                .freezeScope(Scope.PROJECT)
                                                                                .identifier(MANUAL_FREEZE_1)
                                                                                .type(FreezeType.MANUAL)
                                                                                .lastUpdatedAt(2L)
                                                                                .yaml(YAML)
                                                                                .build());
  static final List<FreezeConfigEntity> manualFreezeConfig2 = Arrays.asList(FreezeConfigEntity.builder()
                                                                                .accountId(ACCOUNT_ID)
                                                                                .orgIdentifier(ORG_ID)
                                                                                .freezeScope(Scope.ORG)
                                                                                .identifier(MANUAL_FREEZE_2)
                                                                                .type(FreezeType.MANUAL)
                                                                                .lastUpdatedAt(3L)
                                                                                .yaml(YAML)
                                                                                .build());
  static final FreezeConfigEntity globalFreezeConfig1 = FreezeConfigEntity.builder()
                                                            .accountId(ACCOUNT_ID)
                                                            .orgIdentifier(ORG_ID)
                                                            .projectIdentifier(PROJECT_ID)
                                                            .freezeScope(Scope.PROJECT)
                                                            .identifier(GLOBAL_FREEZE_1)
                                                            .type(FreezeType.GLOBAL)
                                                            .lastUpdatedAt(2L)
                                                            .yaml(YAML)
                                                            .build();
  static final FreezeConfigEntity globalFreezeConfig2 = FreezeConfigEntity.builder()
                                                            .accountId(ACCOUNT_ID)
                                                            .orgIdentifier(ORG_ID)
                                                            .freezeScope(Scope.ORG)
                                                            .identifier(GLOBAL_FREEZE_2)
                                                            .type(FreezeType.GLOBAL)
                                                            .lastUpdatedAt(3L)
                                                            .yaml(YAML)
                                                            .build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  private FrozenExecutionDetails getFrozenExecutionDetails() {
    List<FrozenExecutionDetails.FrozenExecutionDetail> frozenExecutionDetailList = new ArrayList<>();
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : freezeListLatest) {
      frozenExecutionDetailList.add(
          FrozenExecutionDetails.FrozenExecutionDetail.builder().url(URL).freeze(freezeSummaryResponseDTO).build());
    }
    return FrozenExecutionDetails.builder().freezeList(frozenExecutionDetailList).build();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getFrozenExecutionDetails() {
    when(frozenExecutionService.getFrozenExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID))
        .thenReturn(Optional.of(FROZEN_EXECUTION));
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
             ACCOUNT_ID, ORG_ID, null, Arrays.asList(MANUAL_FREEZE_2)))
        .thenReturn(manualFreezeConfig2);
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, Arrays.asList(MANUAL_FREEZE_1)))
        .thenReturn(manualFreezeConfig1);
    when(freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, null))
        .thenReturn(Optional.of(globalFreezeConfig1));
    when(freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(
             ACCOUNT_ID, ORG_ID, null, null))
        .thenReturn(Optional.of(globalFreezeConfig2));
    when(notificationHelper.getGlobalFreezeUrl(any(), any(), any(), any())).thenReturn(URL);
    when(notificationHelper.getManualFreezeUrl(any(), any(), any(), any(), any())).thenReturn(URL);

    FrozenExecutionDetails frozenExecutionDetails =
        freezeCRUDService.getFrozenExecutionDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, URL);
    assertThat(frozenExecutionDetails.getFreezeList())
        .containsExactlyInAnyOrder(
            getFrozenExecutionDetails().getFreezeList().toArray(new FrozenExecutionDetails.FrozenExecutionDetail[0]));
    verify(frozenExecutionService).getFrozenExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID);
    verify(freezeConfigRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
            ACCOUNT_ID, ORG_ID, null, Arrays.asList(MANUAL_FREEZE_2));
    verify(freezeConfigRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, Arrays.asList(MANUAL_FREEZE_1));
    verify(freezeConfigRepository)
        .findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(ACCOUNT_ID, ORG_ID, null, null);
    verify(freezeConfigRepository)
        .findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(ACCOUNT_ID, ORG_ID, PROJECT_ID, null);
    verify(notificationHelper).getManualFreezeUrl(URL, ACCOUNT_ID, ORG_ID, PROJECT_ID, MANUAL_FREEZE_1);
    verify(notificationHelper).getManualFreezeUrl(URL, ACCOUNT_ID, ORG_ID, null, MANUAL_FREEZE_2);
    verify(notificationHelper).getGlobalFreezeUrl(URL, ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(notificationHelper).getGlobalFreezeUrl(URL, ACCOUNT_ID, ORG_ID, null);
  }
}