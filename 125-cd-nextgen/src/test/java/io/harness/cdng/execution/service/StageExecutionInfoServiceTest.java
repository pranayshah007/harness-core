/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionBasicSummaryProjection;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.repositories.executions.StageExecutionInfoRepository;
import io.harness.rule.Owner;
import io.harness.utils.StageStatus;

import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class StageExecutionInfoServiceTest extends CategoryTest {
  private static final String ENV_IDENTIFIER = "envIdentifier";
  private static final String INFRA_IDENTIFIER = "infraIdentifier";
  private static final String SERVICE_IDENTIFIER = "serviceIdentifier";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String EXECUTION_ID = "executionId";
  private static final List<String> STAGE_EXECUTION_IDENTIFIERS = List.of("id1", "id2");
  @Mock private StageExecutionInfoRepository stageExecutionInfoRepository;

  @Mock private StageExecutionBasicSummaryProjection stageExecutionBasicSummaryProjection1;
  @Mock private StageExecutionBasicSummaryProjection stageExecutionBasicSummaryProjection2;
  @InjectMocks private StageExecutionInfoServiceImpl stageExecutionInfoService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void saveStageExecutionInfo() {
    StageExecutionInfo stageExecutionInfo = StageExecutionInfo.builder()
                                                .envIdentifier(ENV_IDENTIFIER)
                                                .infraIdentifier(INFRA_IDENTIFIER)
                                                .serviceIdentifier(SERVICE_IDENTIFIER)
                                                .build();

    stageExecutionInfoService.save(stageExecutionInfo);

    verify(stageExecutionInfoRepository).save(eq(stageExecutionInfo));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateStatus() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    when(stageExecutionInfoRepository.updateStatus(scope, EXECUTION_ID, StageStatus.SUCCEEDED))
        .thenReturn(UpdateResult.acknowledged(1, null, null));

    stageExecutionInfoService.updateStatus(scope, EXECUTION_ID, StageStatus.SUCCEEDED);

    verify(stageExecutionInfoRepository).updateStatus(scope, EXECUTION_ID, StageStatus.SUCCEEDED);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdate() {
    Map<String, Object> updates = new HashMap<>();
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    when(stageExecutionInfoRepository.update(scope, EXECUTION_ID, updates))
        .thenReturn(UpdateResult.acknowledged(1, null, null));

    stageExecutionInfoService.update(scope, EXECUTION_ID, updates);

    verify(stageExecutionInfoRepository).update(scope, EXECUTION_ID, updates);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateStatusWithUnacknowledged() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    when(stageExecutionInfoRepository.updateStatus(scope, EXECUTION_ID, StageStatus.SUCCEEDED))
        .thenReturn(UpdateResult.unacknowledged());

    assertThatThrownBy(() -> stageExecutionInfoService.updateStatus(scope, EXECUTION_ID, StageStatus.SUCCEEDED))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Unable to update stage execution status, accountIdentifier: accountIdentifier, orgIdentifier: orgIdentifier, projectIdentifier: projectIdentifier, executionId: executionId, stageStatus: SUCCEEDED");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateStatusInvalidExecutionId() {
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThatThrownBy(() -> stageExecutionInfoService.updateStatus(scope, null, StageStatus.SUCCEEDED))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLatestSuccessfulStageExecutionInfo() {
    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .build();
    stageExecutionInfoService.getLatestSuccessfulStageExecutionInfo(executionInfoKey, EXECUTION_ID);

    verify(stageExecutionInfoRepository)
        .listSucceededStageExecutionNotIncludeCurrent(executionInfoKey, EXECUTION_ID, 1);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListLatestSuccessfulStageExecutionInfo() {
    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .build();
    stageExecutionInfoService.listLatestSuccessfulStageExecutionInfo(executionInfoKey, EXECUTION_ID, 2);

    verify(stageExecutionInfoRepository)
        .listSucceededStageExecutionNotIncludeCurrent(executionInfoKey, EXECUTION_ID, 2);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLatestSuccessfulStageExecutionInfoInvalidExecutionId() {
    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .build();

    assertThatThrownBy(() -> stageExecutionInfoService.getLatestSuccessfulStageExecutionInfo(executionInfoKey, null))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListStageExecutionFormattedSummaryByStageExecutionIdentifiersForNegativeCases() {
    // stage execution ids required
    AssertionsForClassTypes
        .assertThatThrownBy(
            ()
                -> stageExecutionInfoService.listStageExecutionFormattedSummaryByStageExecutionIdentifiers(
                    Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), new ArrayList<>()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage execution identifiers are required");

    // invalid scope
    AssertionsForClassTypes
        .assertThatThrownBy(
            ()
                -> stageExecutionInfoService.listStageExecutionFormattedSummaryByStageExecutionIdentifiers(
                    Scope.of(ACCOUNT_IDENTIFIER), STAGE_EXECUTION_IDENTIFIERS))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid scope provided, project scope expected");
    AssertionsForClassTypes
        .assertThatThrownBy(
            ()
                -> stageExecutionInfoService.listStageExecutionFormattedSummaryByStageExecutionIdentifiers(
                    Scope.builder().accountIdentifier(ACCOUNT_IDENTIFIER).orgIdentifier(ORG_IDENTIFIER).build(),
                    STAGE_EXECUTION_IDENTIFIERS))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid scope provided, project scope expected");

    // with empty records
    Mockito
        .when(
            stageExecutionInfoRepository
                .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndStageExecutionIdIn(
                    ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, new HashSet<>(STAGE_EXECUTION_IDENTIFIERS)))
        .thenReturn(new ArrayList<>());
    assertThat(stageExecutionInfoService.listStageExecutionFormattedSummaryByStageExecutionIdentifiers(
                   Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), STAGE_EXECUTION_IDENTIFIERS))
        .isEmpty();
    verify(stageExecutionInfoRepository, times(1))
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndStageExecutionIdIn(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, new HashSet<>(STAGE_EXECUTION_IDENTIFIERS));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListStagePlanCremationFormattedSummaryByStageIdentifiers() {
    when(stageExecutionBasicSummaryProjection1.getStageExecutionId()).thenReturn("id1");
    when(stageExecutionBasicSummaryProjection1.getServiceIdentifier()).thenReturn(SERVICE_IDENTIFIER);
    when(stageExecutionBasicSummaryProjection1.getEnvIdentifier()).thenReturn(ENV_IDENTIFIER);
    when(stageExecutionBasicSummaryProjection1.getInfraIdentifier()).thenReturn(INFRA_IDENTIFIER);

    when(stageExecutionBasicSummaryProjection2.getStageExecutionId()).thenReturn("id2");
    when(stageExecutionBasicSummaryProjection2.getServiceIdentifier()).thenReturn(SERVICE_IDENTIFIER);
    when(stageExecutionBasicSummaryProjection2.getEnvIdentifier()).thenReturn("  ");
    when(stageExecutionBasicSummaryProjection2.getInfraIdentifier()).thenReturn(INFRA_IDENTIFIER);

    Mockito
        .when(
            stageExecutionInfoRepository
                .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndStageExecutionIdIn(
                    ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, new HashSet<>(STAGE_EXECUTION_IDENTIFIERS)))
        .thenReturn(List.of(stageExecutionBasicSummaryProjection1, stageExecutionBasicSummaryProjection2));
    Map<String, CDStageSummaryResponseDTO> stageMap =
        stageExecutionInfoService.listStageExecutionFormattedSummaryByStageExecutionIdentifiers(
            Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER), STAGE_EXECUTION_IDENTIFIERS);
    assertThat(stageMap)
        .hasSize(2)
        .containsEntry("id1",
            CDStageSummaryResponseDTO.builder()
                .service(SERVICE_IDENTIFIER)
                .environment(ENV_IDENTIFIER)
                .infra(INFRA_IDENTIFIER)
                .build())
        .containsEntry("id2",
            CDStageSummaryResponseDTO.builder()
                .service(SERVICE_IDENTIFIER)
                .environment("NA")
                .infra(INFRA_IDENTIFIER)
                .build());

    verify(stageExecutionInfoRepository, times(1))
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndStageExecutionIdIn(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, new HashSet<>(STAGE_EXECUTION_IDENTIFIERS));
  }
}
