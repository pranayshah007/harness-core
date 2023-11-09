/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.service;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.cdng.creator.plan.stage.SingleServiceEnvDeploymentStageDetailsInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.repositories.executions.DeploymentStagePlanCreationInfoRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DeploymentStagePlanCreationInfoServiceImplTest extends CategoryTest {
  @Mock private DeploymentStagePlanCreationInfoRepository deploymentStagePlanCreationInfoRepository;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String PLAN_ID = "PLAN_ID";
  private static final List<String> STAGE_IDENTIFIERS = List.of("id1", "id2", "id3");
  private static final String INFRA_IDENTIFIER = "infra_identifier";
  private static final String SERVICE_IDENTIFIER = "service_identifier";
  private static final String ENVIRONMENT_IDENTIFIER = "environment_identifier";
  private static final String INFRA_NAME = "infra_name";
  private static final String SERVICE_NAME = "service_name";
  private static final String ENVIRONMENT_NAME = "environment_name";
  @InjectMocks @Inject private DeploymentStagePlanCreationInfoServiceImpl deploymentStagePlanCreationInfoService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testSave() {
    DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfo = DeploymentStagePlanCreationInfo.builder().build();
    deploymentStagePlanCreationInfoService.save(deploymentStagePlanCreationInfo);
    verify(deploymentStagePlanCreationInfoRepository, times(1)).save(deploymentStagePlanCreationInfo);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListStagePlanCreationFormattedSummaryByStageIdentifiersForNegativeCases() {
    // plan execution id blank
    assertThatThrownBy(
        ()
            -> deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
                Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), "  ", STAGE_IDENTIFIERS))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty plan execution identifier or stage identifiers provided");
    // stage ids empty
    assertThatThrownBy(
        ()
            -> deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
                Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), PLAN_ID, Collections.emptyList()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty plan execution identifier or stage identifiers provided");
    // invalid scope
    assertThatThrownBy(
        ()
            -> deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
                Scope.of(ACCOUNT_ID), PLAN_ID, STAGE_IDENTIFIERS))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid scope provided, project scope expected");
    assertThatThrownBy(
        ()
            -> deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
                Scope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).build(), PLAN_ID,
                STAGE_IDENTIFIERS))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid scope provided, project scope expected");

    // with empty records
    Mockito
        .when(deploymentStagePlanCreationInfoRepository
                  .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndStageIdentifierIn(
                      ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_ID, new HashSet<>(STAGE_IDENTIFIERS)))
        .thenReturn(new ArrayList<>());
    assertThat(deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
                   Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), PLAN_ID, STAGE_IDENTIFIERS))
        .isEmpty();
    verify(deploymentStagePlanCreationInfoRepository, times(1))
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndStageIdentifierIn(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_ID, new HashSet<>(STAGE_IDENTIFIERS));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testListStagePlanCremationFormattedSummaryByStageIdentifiers() {
    DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfoWithNames =
        DeploymentStagePlanCreationInfo.builder()
            .stageIdentifier("id1")
            .deploymentStageDetailsInfo(SingleServiceEnvDeploymentStageDetailsInfo.builder()
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .serviceName(SERVICE_NAME)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .infraName(INFRA_NAME)
                                            .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                            .envName(ENVIRONMENT_NAME)
                                            .build())
            .build();
    DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfoWithoutNames =
        DeploymentStagePlanCreationInfo.builder()
            .stageIdentifier("id2")
            .deploymentStageDetailsInfo(SingleServiceEnvDeploymentStageDetailsInfo.builder()
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                            .build())
            .build();
    DeploymentStagePlanCreationInfo deploymentStagePlanCreationInfoWithNA =
        DeploymentStagePlanCreationInfo.builder()
            .stageIdentifier("id3")
            .deploymentStageDetailsInfo(SingleServiceEnvDeploymentStageDetailsInfo.builder()
                                            .serviceIdentifier(SERVICE_NAME)
                                            .infraIdentifier("  ")
                                            .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                            .build())
            .build();

    Mockito
        .when(deploymentStagePlanCreationInfoRepository
                  .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndStageIdentifierIn(
                      ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_ID, new HashSet<>(STAGE_IDENTIFIERS)))
        .thenReturn(List.of(deploymentStagePlanCreationInfoWithoutNames, deploymentStagePlanCreationInfoWithNames,
            deploymentStagePlanCreationInfoWithNA));
    Map<String, CDStageSummaryResponseDTO> stageMap =
        deploymentStagePlanCreationInfoService.listStagePlanCreationFormattedSummaryByStageIdentifiers(
            Scope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID), PLAN_ID, STAGE_IDENTIFIERS);
    assertThat(stageMap)
        .hasSize(3)
        .containsEntry("id1",
            CDStageSummaryResponseDTO.builder()
                .service(SERVICE_NAME)
                .environment(ENVIRONMENT_NAME)
                .infra(INFRA_NAME)
                .build())
        .containsEntry("id2",
            CDStageSummaryResponseDTO.builder()
                .service(SERVICE_IDENTIFIER)
                .environment(ENVIRONMENT_IDENTIFIER)
                .infra(INFRA_IDENTIFIER)
                .build())
        .containsEntry("id3",
            CDStageSummaryResponseDTO.builder()
                .service(SERVICE_NAME)
                .environment(ENVIRONMENT_IDENTIFIER)
                .infra("NA")
                .build());

    verify(deploymentStagePlanCreationInfoRepository, times(1))
        .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndStageIdentifierIn(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_ID, new HashSet<>(STAGE_IDENTIFIERS));
  }
}
