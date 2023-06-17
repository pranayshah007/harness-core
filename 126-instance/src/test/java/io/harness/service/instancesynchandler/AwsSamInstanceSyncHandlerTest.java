/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AwsSamDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AwsSamInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.models.infrastructuredetails.AwsSamInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsSamInstanceSyncHandlerTest {
  private final String FUNCTION = "fun";
  private final String REGION = "us-east1";
  private final String MEMORY_SIZE = "512MiB";
  private final String RUN_TIME = "java8";
  private final String HANDLER = "index.handler";
  private final String INFRA_KEY = "198398123";

  @InjectMocks private AwsSamInstanceSyncHandler awsSamInstanceSyncHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getPerpetualTaskTypeTest() {
    String perpetualTaskType = awsSamInstanceSyncHandler.getPerpetualTaskType();

    assertThat(perpetualTaskType).isEqualTo(PerpetualTaskType.AWS_SAM_INSTANCE_SYNC_NG);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInstanceTypeTest() {
    InstanceType instanceType = awsSamInstanceSyncHandler.getInstanceType();

    assertThat(instanceType).isEqualTo(InstanceType.AWS_SAM_INSTANCE);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInfrastructureMappingTypeTest() {
    String infrastructureMappingType = awsSamInstanceSyncHandler.getInfrastructureKind();

    assertThat(infrastructureMappingType).isEqualTo(InfrastructureKind.AWS_SAM);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInfrastructureDetailsTest() {
    AwsSamInstanceInfoDTO awsSamInstanceInfoDTO =
        AwsSamInstanceInfoDTO.builder().functionName(FUNCTION).region(REGION).build();

    AwsSamInfrastructureDetails awsSamInfrastructureDetails =
        (AwsSamInfrastructureDetails) awsSamInstanceSyncHandler.getInfrastructureDetails(awsSamInstanceInfoDTO);

    assertThat(awsSamInfrastructureDetails.getRegion()).isEqualTo(awsSamInstanceInfoDTO.getRegion());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getInstanceInfoForServerInstanceTest() {
    AwsSamServerInstanceInfo awsSamServerInstanceInfo = AwsSamServerInstanceInfo.builder()
                                                            .functionName(FUNCTION)
                                                            .region(REGION)
                                                            .memorySize(MEMORY_SIZE)
                                                            .runTime(RUN_TIME)
                                                            .handler(HANDLER)
                                                            .infraStructureKey(INFRA_KEY)
                                                            .build();

    AwsSamInstanceInfoDTO awsSamInstanceInfoDTO =
        (AwsSamInstanceInfoDTO) awsSamInstanceSyncHandler.getInstanceInfoForServerInstance(awsSamServerInstanceInfo);

    assertThat(awsSamInstanceInfoDTO.getFunctionName()).isEqualTo(awsSamServerInstanceInfo.getFunctionName());
    assertThat(awsSamInstanceInfoDTO.getRegion()).isEqualTo(awsSamServerInstanceInfo.getRegion());
    assertThat(awsSamInstanceInfoDTO.getMemorySize()).isEqualTo(awsSamServerInstanceInfo.getMemorySize());
    assertThat(awsSamInstanceInfoDTO.getRunTime()).isEqualTo(awsSamServerInstanceInfo.getRunTime());
    assertThat(awsSamInstanceInfoDTO.getHandler()).isEqualTo(awsSamServerInstanceInfo.getHandler());
    assertThat(awsSamInstanceInfoDTO.getInfraStructureKey()).isEqualTo(awsSamServerInstanceInfo.getInfraStructureKey());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getDeploymentInfoTest() {
    AwsSamServerInstanceInfo awsSamServerInstanceInfo =
        AwsSamServerInstanceInfo.builder().functionName(FUNCTION).region(REGION).infraStructureKey(INFRA_KEY).build();
    InfrastructureOutcome infrastructureOutcome = AwsSamInfrastructureOutcome.builder().build();
    AwsSamDeploymentInfoDTO awsSamDeploymentInfoDTO =
        (AwsSamDeploymentInfoDTO) awsSamInstanceSyncHandler.getDeploymentInfo(
            infrastructureOutcome, Arrays.asList(awsSamServerInstanceInfo));

    assertThat(awsSamDeploymentInfoDTO.getFunctions().get(0)).isEqualTo(awsSamServerInstanceInfo.getFunctionName());
    assertThat(awsSamDeploymentInfoDTO.getRegion()).isEqualTo(awsSamServerInstanceInfo.getRegion());
    assertThat(awsSamDeploymentInfoDTO.getInfraStructureKey())
        .isEqualTo(awsSamServerInstanceInfo.getInfraStructureKey());
  }
}
