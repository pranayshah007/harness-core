/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.licensing.Edition.FREE;
import static io.harness.licensing.Edition.TEAM;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@OwnedBy(PIPELINE)

public class PipelineSettingsServiceImplTest extends OrchestrationTestBase {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";
  public static final String PIPELINE_IDENTIFIER = "PIPELINE_IDENTIFIER";
  @Mock NgLicenseHttpClient ngLicenseHttpClient;

  @Mock OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;

  @Mock PlanExecutionService planExecutionService;

  @InjectMocks PipelineSettingsServiceImpl pipelineSettingsService;

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetEdition() throws ExecutionException {
    // moduleLicenseDTO.getEdition() == FREE
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    Edition edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(FREE);

    // moduleLicenseDTO.getEdition() == TEAM
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(TEAM);

    // moduleLicenseDTO.getEdition() == ENTERPRISE
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ENTERPRISE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionNotFree() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    doReturn(3L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount(any(), any());
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionFree() {
    // editon == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    // runningExecutionsForGivenPipeline >= maxCount == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(0L)
        .when(planExecutionService)
        .countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID", "PIPELINE_IDENTIFIER");

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionNotTeam() throws UnsupportedEncodingException {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionTeamGreaterThanMaxCount() throws UnsupportedEncodingException {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == True
    // runningExecutionsForGivenPipeline >= maxCount == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(100L)
        .when(planExecutionService)
        .countRunningExecutionsForGivenPipelineInAccountExcludingWaitingStatuses("ACCOUNT_ID", "PIPELINE_IDENTIFIER");

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isTrue();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == True
    // runningExecutionsForGivenPipeline >= maxCount == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(0L)
        .when(planExecutionService)
        .countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID", "PIPELINE_IDENTIFIER");

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionNotEnterprise() throws UnsupportedEncodingException {
    // editon == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionEnterpriseGreaterThanMaxCount() throws UnsupportedEncodingException {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForENTERPRISE() == True
    // runningExecutionsForGivenPipeline >= maxCount == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(100L)
        .when(planExecutionService)
        .countRunningExecutionsForGivenPipelineInAccountExcludingWaitingStatuses("ACCOUNT_ID", "PIPELINE_IDENTIFIER");

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isTrue();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionEnterprise() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise() == True &&
    // runningExecutionsForGivenPipeline >= maxCount == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(0L)
        .when(planExecutionService)
        .countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID", "PIPELINE_IDENTIFIER");
    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID", PIPELINE_IDENTIFIER);
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForFree() {
    // editon == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForNotFree() {
    // edition != FREE || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForNotTeam() {
    // edition != TEAM || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForEnterprise() {
    // edition == ENTERPRISE || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(3L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForNotEnterprise() {
    // edition != ENTERPRISE || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionFree() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getMaxConcurrencyRestriction();
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 1L);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionNotFree() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 1L);
    assertThat(count).isEqualTo(20L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionFreeException() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    // && childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getFree() ==
    // True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Trying to run more than 1 concurrent stages/steps. Please upgrade your plan to Team (Paid) or reduce concurrency");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getMaxConcurrencyRestriction();
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 2L);
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionNotTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 2L);
    assertThat(count).isEqualTo(50L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionTeamException() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == True
    // && childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getFree() ==
    // True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Trying to run more than 2 concurrent stages/steps. Please upgrade your plan to Enterprise (Paid) or reduce concurrency");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionEnterprise() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getMaxConcurrencyRestriction();
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 3L);
    assertThat(count).isEqualTo(3L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionNotEnterprise() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 3L);
    assertThat(count).isEqualTo(100L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionEnterpriseException() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise() == True
    // && childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getEnterprise() ==
    // True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = new PlanExecutionRestrictionConfig(1, 2, 3);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition(ACCOUNT_ID, 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Trying to run more than 3 concurrent stages/steps. Please contact sales if you want to run more");
  }
}
