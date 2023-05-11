/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.models.infrastructuredetails.GoogleFunctionInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class GoogleFunctionInstanceSyncHandlerTest extends InstancesTestBase {
  private final String FUNCTION = "fun";
  private final String PROJECT = "cd-play";
  private final String REGION = "us-east1";
  private final String MEMORY_SIZE = "512MiB";
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";
  private final String SOURCE = "source";
  private final String REVISION = "function-867";
  private final long TIME = 74987321;

  @InjectMocks private GoogleFunctionInstanceSyncHandler googleFunctionInstanceSyncHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getPerpetualTaskTypeTest() {
    String perpetualTaskType = googleFunctionInstanceSyncHandler.getPerpetualTaskType();

    assertThat(perpetualTaskType).isEqualTo(PerpetualTaskType.GOOGLE_CLOUD_FUNCTION_INSTANCE_SYNC_NG);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInstanceTypeTest() {
    InstanceType instanceType = googleFunctionInstanceSyncHandler.getInstanceType();

    assertThat(instanceType).isEqualTo(InstanceType.GOOGLE_CLOUD_FUNCTIONS_INSTANCE);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInfrastructureMappingTypeTest() {
    String infrastructureMappingType = googleFunctionInstanceSyncHandler.getInfrastructureKind();

    assertThat(infrastructureMappingType).isEqualTo(InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInfrastructureDetailsTest() {
    GoogleFunctionInstanceInfoDTO googleFunctionInstanceInfoDTO =
        GoogleFunctionInstanceInfoDTO.builder().functionName(FUNCTION).project(PROJECT).region(REGION).build();

    GoogleFunctionInfrastructureDetails googleFunctionInfrastructureDetails =
        (GoogleFunctionInfrastructureDetails) googleFunctionInstanceSyncHandler.getInfrastructureDetails(
            googleFunctionInstanceInfoDTO);

    assertThat(googleFunctionInfrastructureDetails.getProject()).isEqualTo(googleFunctionInstanceInfoDTO.getProject());
    assertThat(googleFunctionInfrastructureDetails.getRegion()).isEqualTo(googleFunctionInstanceInfoDTO.getRegion());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInstanceInfoForServerInstanceTest() {
    GoogleFunctionServerInstanceInfo googleFunctionServerInstanceInfo = GoogleFunctionServerInstanceInfo.builder()
                                                                            .revision(REVISION)
                                                                            .functionName(FUNCTION)
                                                                            .project(PROJECT)
                                                                            .region(REGION)
                                                                            .memorySize(MEMORY_SIZE)
                                                                            .runTime(RUN_TIME)
                                                                            .source(SOURCE)
                                                                            .infraStructureKey(INFRA_KEY)
                                                                            .updatedTime(TIME)
                                                                            .build();

    GoogleFunctionInstanceInfoDTO googleFunctionInstanceInfoDTO =
        (GoogleFunctionInstanceInfoDTO) googleFunctionInstanceSyncHandler.getInstanceInfoForServerInstance(
            googleFunctionServerInstanceInfo);

    assertThat(googleFunctionInstanceInfoDTO.getRevision()).isEqualTo(googleFunctionServerInstanceInfo.getRevision());
    assertThat(googleFunctionInstanceInfoDTO.getFunctionName())
        .isEqualTo(googleFunctionServerInstanceInfo.getFunctionName());
    assertThat(googleFunctionInstanceInfoDTO.getProject()).isEqualTo(googleFunctionServerInstanceInfo.getProject());
    assertThat(googleFunctionInstanceInfoDTO.getRegion()).isEqualTo(googleFunctionServerInstanceInfo.getRegion());
    assertThat(googleFunctionInstanceInfoDTO.getMemorySize())
        .isEqualTo(googleFunctionServerInstanceInfo.getMemorySize());
    assertThat(googleFunctionInstanceInfoDTO.getRunTime()).isEqualTo(googleFunctionServerInstanceInfo.getRunTime());
    assertThat(googleFunctionInstanceInfoDTO.getSource()).isEqualTo(googleFunctionServerInstanceInfo.getSource());
    assertThat(googleFunctionInstanceInfoDTO.getUpdatedTime())
        .isEqualTo(googleFunctionServerInstanceInfo.getUpdatedTime());
    assertThat(googleFunctionInstanceInfoDTO.getInfraStructureKey())
        .isEqualTo(googleFunctionServerInstanceInfo.getInfraStructureKey());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getDeploymentInfoTest() {
    GoogleFunctionServerInstanceInfo googleFunctionServerInstanceInfo = GoogleFunctionServerInstanceInfo.builder()
                                                                            .revision(REVISION)
                                                                            .functionName(FUNCTION)
                                                                            .project(PROJECT)
                                                                            .region(REGION)
                                                                            .infraStructureKey(INFRA_KEY)
                                                                            .build();
    InfrastructureOutcome infrastructureOutcome = GoogleFunctionsInfrastructureOutcome.builder().build();
    GoogleFunctionDeploymentInfoDTO googleFunctionDeploymentInfoDTO =
        (GoogleFunctionDeploymentInfoDTO) googleFunctionInstanceSyncHandler.getDeploymentInfo(
            infrastructureOutcome, Arrays.asList(googleFunctionServerInstanceInfo));

    assertThat(googleFunctionDeploymentInfoDTO.getEnvironmentType())
        .isEqualTo(googleFunctionServerInstanceInfo.getEnvironmentType());
    assertThat(googleFunctionDeploymentInfoDTO.getFunctionName())
        .isEqualTo(googleFunctionServerInstanceInfo.getFunctionName());
    assertThat(googleFunctionDeploymentInfoDTO.getProject()).isEqualTo(googleFunctionServerInstanceInfo.getProject());
    assertThat(googleFunctionDeploymentInfoDTO.getRegion()).isEqualTo(googleFunctionServerInstanceInfo.getRegion());
    assertThat(googleFunctionDeploymentInfoDTO.getInfraStructureKey())
        .isEqualTo(googleFunctionServerInstanceInfo.getInfraStructureKey());
  }
}
