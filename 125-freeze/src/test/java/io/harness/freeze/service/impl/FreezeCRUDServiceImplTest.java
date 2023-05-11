/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.EntityNotFoundException;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseWrapperDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.response.FrozenExecutionDetails;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.outbox.api.OutboxService;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

public class FreezeCRUDServiceImplTest extends CategoryTest {
  @Mock private NotificationHelper notificationHelper;
  @Mock private FrozenExecutionService frozenExecutionService;
  @Mock private FreezeConfigRepository freezeConfigRepository;
  @Mock private FreezeSchemaServiceImpl freezeSchemaService;
  @Mock private OutboxService outboxService;
  @Mock private TransactionTemplate transactionTemplate;
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
  static final String GLOBAL_FREEZE_NAME = "Global Freeze";
  static final String YAML = "freeze:\n"
      + "  name: freeze\n"
      + "  identifier: freeze\n";

  static final String FREEZE_YAML_1 = "freeze:\n"
      + "  name: manualFreeze1\n"
      + "  identifier: manualFreeze1\n"
      + "  entityConfigs:\n"
      + "    - name: rule-1\n"
      + "      entities:\n"
      + "        - filterType: All\n"
      + "          type: Service\n"
      + "        - filterType: All\n"
      + "          type: Environment\n"
      + "        - filterType: All\n"
      + "          type: EnvType\n"
      + "  status: Enabled\n"
      + "  orgIdentifier: default\n"
      + "  description: \"\"\n"
      + "  windows:\n"
      + "    - timeZone: Asia/Calcutta\n"
      + "      startTime: 2023-04-27 02:48 PM\n"
      + "      duration: 30m\n"
      + "      recurrence:\n"
      + "        type: Daily";

  static final String FREEZE_YAML_2 = "freeze:\n"
      + "  name: manualFreeze2\n"
      + "  identifier: manualFreeze1\n"
      + "  entityConfigs:\n"
      + "    - name: rule-1\n"
      + "      entities:\n"
      + "        - filterType: All\n"
      + "          type: Service\n"
      + "        - filterType: All\n"
      + "          type: Environment\n"
      + "        - filterType: All\n"
      + "          type: EnvType\n"
      + "  status: Enabled\n"
      + "  orgIdentifier: default\n"
      + "  description: \"\"\n"
      + "  windows:\n"
      + "    - timeZone: Asia/Calcutta\n"
      + "      startTime: 2023-04-27 02:48 PM\n"
      + "      duration: 30m\n"
      + "      recurrence:\n"
      + "        type: Daily";

  static final String GLOBAL_FREEZE_YAML = "freeze:\n"
      + "  identifier: _GLOBAL_\n"
      + "  name: Global Freeze\n"
      + "  orgIdentifier: default\n"
      + "  projectIdentifier: TAS\n"
      + "  status: Disabled\n"
      + "  windows:\n"
      + "    - timeZone: Asia/Calcutta\n"
      + "      startTime: 2023-05-03 12:39 PM\n"
      + "      duration: 30m";

  static final String GLOBAL = "_GLOBAL_";

  static final String YAML_FOR_WINDOWS = "freeze:\n"
      + "  name: fr-4\n"
      + "  identifier: fr4\n"
      + "  entityConfigs:\n"
      + "    - name: rule-1\n"
      + "      entities:\n"
      + "        - filterType: All\n"
      + "          type: Pipeline\n"
      + "        - filterType: All\n"
      + "          type: Service\n"
      + "        - filterType: All\n"
      + "          type: Environment\n"
      + "        - filterType: All\n"
      + "          type: EnvType\n"
      + "  status: Enabled\n"
      + "  orgIdentifier: default\n"
      + "  projectIdentifier: TAS\n"
      + "  description: \"\"\n"
      + "  windows:\n"
      + "    - timeZone: Asia/Calcutta\n"
      + "      startTime: 2023-04-27 02:48 PM\n"
      + "      duration: 30m\n"
      + "      recurrence:\n"
      + "        type: Daily\n";
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

  static final FreezeConfigEntity manualFreezeConfig = FreezeConfigEntity.builder()
                                                           .accountId(ACCOUNT_ID)
                                                           .orgIdentifier(ORG_ID)
                                                           .freezeScope(Scope.ORG)
                                                           .identifier(GLOBAL_FREEZE_2)
                                                           .status(FreezeStatus.ENABLED)
                                                           .type(FreezeType.MANUAL)
                                                           .lastUpdatedAt(3L)
                                                           .yaml(YAML_FOR_WINDOWS)
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

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_updateNextIteration() {
    Mockito.mockStatic(FreezeTimeUtils.class);
    when(FreezeTimeUtils.fetchUpcomingTimeWindow(any())).thenReturn(null);
    freezeCRUDService.updateNextIterations(manualFreezeConfig);
    assertThat(manualFreezeConfig.getNextIteration()).isNull();
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_updateNextIterationForNotNullIteration() {
    Mockito.mockStatic(FreezeTimeUtils.class);
    when(FreezeTimeUtils.fetchUpcomingTimeWindow(any())).thenReturn(List.of(180000L));
    freezeCRUDService.updateNextIterations(manualFreezeConfig);
    assertThat(manualFreezeConfig.getNextIteration()).isEqualTo(180000L);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_createFreezeConfig() {
    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.ENABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .identifier(MANUAL_FREEZE_1)
                                                                .freezeScope(Scope.ORG)
                                                                .yaml(FREEZE_YAML_1)
                                                                .build());

    when(transactionTemplate.execute(any())).thenReturn(null);
    when(freezeConfigRepository.save(any())).thenReturn(freezeConfig);
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(null));

    FreezeResponseDTO freezeResponseDTO = freezeCRUDService.createFreezeConfig(FREEZE_YAML_1, ACCOUNT_ID, ORG_ID, null);

    assertThat(freezeResponseDTO.getName()).isEqualTo(MANUAL_FREEZE_1);
    assertThat(freezeResponseDTO.getIdentifier()).isEqualTo(MANUAL_FREEZE_1);
    assertThat(freezeResponseDTO.getYaml()).isEqualTo(FREEZE_YAML_1);
    assertThat(freezeResponseDTO.getFreezeScope()).isEqualTo(Scope.ORG);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_manageGlobalFreezeConfig() {
    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.DISABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .identifier(GLOBAL)
                                                                .name(GLOBAL_FREEZE_NAME)
                                                                .freezeScope(Scope.PROJECT)
                                                                .yaml(GLOBAL_FREEZE_YAML)
                                                                .build());

    when(transactionTemplate.execute(any())).thenReturn(freezeConfig.get());
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(freezeConfig);
    when(freezeConfigRepository.save(any())).thenReturn(freezeConfig.get());
    when(outboxService.save(any())).thenReturn(null);
    FreezeResponseDTO freezeResponseDTO =
        freezeCRUDService.manageGlobalFreezeConfig(GLOBAL_FREEZE_YAML, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(freezeResponseDTO.getName()).isEqualTo(GLOBAL_FREEZE_NAME);
    assertThat(freezeResponseDTO.getIdentifier()).isEqualTo(GLOBAL);
    assertThat(freezeResponseDTO.getFreezeScope()).isEqualTo(Scope.PROJECT);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_updateFreezeConfig() {
    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.ENABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .name(MANUAL_FREEZE_1)
                                                                .identifier(MANUAL_FREEZE_1)
                                                                .freezeScope(Scope.ORG)
                                                                .yaml(FREEZE_YAML_1)
                                                                .build());

    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(freezeConfig);

    FreezeConfigEntity freezeConfigEntity = freezeConfig.get();
    freezeConfigEntity.setName(MANUAL_FREEZE_2);
    freezeConfigEntity.setYaml(FREEZE_YAML_2);
    when(freezeConfigRepository.save(any())).thenReturn(freezeConfigEntity);
    when(transactionTemplate.execute(any())).thenReturn(freezeConfigEntity);
    when(outboxService.save(any())).thenReturn(null);
    FreezeResponseDTO freezeResponseDTO =
        freezeCRUDService.updateFreezeConfig(FREEZE_YAML_2, ACCOUNT_ID, ORG_ID, null, MANUAL_FREEZE_1);

    assertThat(freezeResponseDTO.getName()).isEqualTo(MANUAL_FREEZE_2);
    assertThat(freezeResponseDTO.getIdentifier()).isEqualTo(MANUAL_FREEZE_1);
    assertThat(freezeResponseDTO.getYaml()).isEqualTo(FREEZE_YAML_2);
    assertThat(freezeResponseDTO.getFreezeScope()).isEqualTo(Scope.ORG);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_deleteFreezeConfig() {
    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.ENABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .name(MANUAL_FREEZE_1)
                                                                .identifier(MANUAL_FREEZE_1)
                                                                .freezeScope(Scope.PROJECT)
                                                                .yaml(FREEZE_YAML_1)
                                                                .build());

    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(freezeConfig);

    doReturn(true).when(freezeConfigRepository).delete((Criteria) any());
    when(transactionTemplate.execute(any())).thenReturn(true);
    when(outboxService.save(any())).thenReturn(null);
    freezeCRUDService.deleteFreezeConfig(MANUAL_FREEZE_1, ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_deleteFreezeConfigForNonExistingEntity() {
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(null));

    doReturn(true).when(freezeConfigRepository).delete((Criteria) any());
    when(transactionTemplate.execute(any())).thenReturn(true);
    when(outboxService.save(any())).thenReturn(null);
    assertThatThrownBy(() -> freezeCRUDService.deleteFreezeConfig(MANUAL_FREEZE_1, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getFreezeConfig() {
    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.ENABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .name(MANUAL_FREEZE_1)
                                                                .identifier(MANUAL_FREEZE_1)
                                                                .freezeScope(Scope.PROJECT)
                                                                .yaml(FREEZE_YAML_1)
                                                                .build());

    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(freezeConfig);

    FreezeResponseDTO freezeResponseDTO =
        freezeCRUDService.getFreezeConfig(MANUAL_FREEZE_1, ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertThat(freezeResponseDTO.getYaml()).isEqualTo(FREEZE_YAML_1);
    assertThat(freezeResponseDTO.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(freezeResponseDTO.getFreezeScope()).isEqualTo(Scope.PROJECT);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getFreezeConfigForNoEntity() {
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(null));

    assertThatThrownBy(() -> freezeCRUDService.getFreezeConfig(MANUAL_FREEZE_1, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_deleteFreezeConfigs() {
    List<String> freezeIdentifiers = Arrays.asList("fr1", "fr2", "fr3", "fr4");

    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(manualFreezeConfig1.get(0)));
    doReturn(true).when(freezeConfigRepository).delete((Criteria) any());
    when(transactionTemplate.execute(any())).thenReturn(true);
    when(outboxService.save(any())).thenReturn(null);

    FreezeResponseWrapperDTO freezeResponseWrapperDTO =
        freezeCRUDService.deleteFreezeConfigs(freezeIdentifiers, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(freezeResponseWrapperDTO.getNoOfFailed()).isEqualTo(0);
    assertThat(freezeResponseWrapperDTO.getNoOfSuccess()).isEqualTo(4);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_deleteFreezeConfigsWithFailure() {
    List<String> freezeIdentifiers = Arrays.asList("fr1", "fr2");

    doReturn(true).when(freezeConfigRepository).delete((Criteria) any());
    when(transactionTemplate.execute(any())).thenReturn(true);
    when(outboxService.save(any())).thenReturn(null);

    FreezeResponseWrapperDTO freezeResponseWrapperDTO =
        freezeCRUDService.deleteFreezeConfigs(freezeIdentifiers, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(freezeResponseWrapperDTO.getNoOfFailed()).isEqualTo(2);
    assertThat(freezeResponseWrapperDTO.getNoOfSuccess()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_updateActiveStatus() {
    List<String> freezeIdentifiers = Arrays.asList(MANUAL_FREEZE_1);

    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.DISABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .name(MANUAL_FREEZE_1)
                                                                .identifier(MANUAL_FREEZE_1)
                                                                .freezeScope(Scope.PROJECT)
                                                                .yaml(FREEZE_YAML_1)
                                                                .build());
    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(freezeConfig);

    FreezeConfigEntity freezeConfigEntity = freezeConfig.get();
    freezeConfigEntity.setStatus(FreezeStatus.ENABLED);
    when(transactionTemplate.execute(any())).thenReturn(freezeConfigEntity);
    when(outboxService.save(any())).thenReturn(null);

    FreezeResponseWrapperDTO freezeResponseWrapperDTO =
        freezeCRUDService.updateActiveStatus(FreezeStatus.ENABLED, ACCOUNT_ID, ORG_ID, PROJECT_ID, freezeIdentifiers);

    assertThat(freezeResponseWrapperDTO.getNoOfFailed()).isEqualTo(0);
    assertThat(freezeResponseWrapperDTO.getNoOfSuccess()).isEqualTo(1);
    FreezeResponseDTO freezeResponseDTO = freezeResponseWrapperDTO.getSuccessfulFreezeResponseDTOList().get(0);

    assertThat(freezeResponseDTO.getStatus()).isEqualTo(FreezeStatus.ENABLED);
    assertThat(freezeResponseDTO.getIdentifier()).isEqualTo(MANUAL_FREEZE_1);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_updateActiveStatusForFailure() {
    List<String> freezeIdentifiers = Arrays.asList(MANUAL_FREEZE_1);

    when(freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(null));

    FreezeResponseWrapperDTO freezeResponseWrapperDTO =
        freezeCRUDService.updateActiveStatus(FreezeStatus.ENABLED, ACCOUNT_ID, ORG_ID, PROJECT_ID, freezeIdentifiers);

    assertThat(freezeResponseWrapperDTO.getNoOfFailed()).isEqualTo(1);
    assertThat(freezeResponseWrapperDTO.getNoOfSuccess()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_getParentGlobalFreeze() {
    Optional<FreezeConfigEntity> freezeConfig = Optional.of(FreezeConfigEntity.builder()
                                                                .status(FreezeStatus.DISABLED)
                                                                .accountId(ACCOUNT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .name(MANUAL_FREEZE_1)
                                                                .identifier(MANUAL_FREEZE_1)
                                                                .freezeScope(Scope.PROJECT)
                                                                .yaml(FREEZE_YAML_1)
                                                                .build());
    when(freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(
             ACCOUNT_ID, ORG_ID, null, null))
        .thenReturn(freezeConfig);

    List<FreezeResponseDTO> freezeResponseDTOs =
        freezeCRUDService.getParentGlobalFreezeSummary(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(freezeResponseDTOs.size()).isEqualTo(2);
    assertThat(freezeResponseDTOs.get(0).getIdentifier()).isEqualTo(MANUAL_FREEZE_1);
    assertThat(freezeResponseDTOs.get(0).getFreezeScope()).isEqualTo(Scope.PROJECT);
    assertThat(freezeResponseDTOs.get(1).getIdentifier()).isEqualTo(GLOBAL);
    assertThat(freezeResponseDTOs.get(1).getFreezeScope()).isEqualTo(Scope.ACCOUNT);
  }
}