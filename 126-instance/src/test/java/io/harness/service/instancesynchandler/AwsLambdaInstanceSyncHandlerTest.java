/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AwsLambdaDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AwsLambdaInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.models.infrastructuredetails.AwsLambdaInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class AwsLambdaInstanceSyncHandlerTest extends InstancesTestBase {
  private final String FUNCTION = "fun";
  private final String REGION = "us-east1";
  private final Integer MEMORY_SIZE = 512;
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";
  private final String SOURCE = "source";
  private final String VERSION = "function-867";
  private final long TIME = 74987321;

  @InjectMocks private AwsLambdaInstanceSyncHandler awsLambdaInstanceSyncHandler;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getPerpetualTaskTypeTest() {
    String perpetualTaskType = awsLambdaInstanceSyncHandler.getPerpetualTaskType();

    assertThat(perpetualTaskType).isEqualTo(PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC_NG);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstanceTypeTest() {
    InstanceType instanceType = awsLambdaInstanceSyncHandler.getInstanceType();

    assertThat(instanceType).isEqualTo(InstanceType.AWS_LAMBDA_INSTANCE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInfrastructureMappingTypeTest() {
    String infrastructureMappingType = awsLambdaInstanceSyncHandler.getInfrastructureKind();

    assertThat(infrastructureMappingType).isEqualTo(InfrastructureKind.AWS_LAMBDA);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInfrastructureDetailsTest() {
    AwsLambdaInstanceInfoDTO awsLambdaInstanceInfoDTO = AwsLambdaInstanceInfoDTO.builder()
                                                            .functionName(FUNCTION)
                                                            .version(VERSION)
                                                            .region(REGION)
                                                            .infraStructureKey(INFRA_KEY)
                                                            .build();

    AwsLambdaInfrastructureDetails awsLambdaInfrastructureDetails =
        (AwsLambdaInfrastructureDetails) awsLambdaInstanceSyncHandler.getInfrastructureDetails(
            awsLambdaInstanceInfoDTO);

    assertThat(awsLambdaInfrastructureDetails.getRegion()).isEqualTo(awsLambdaInstanceInfoDTO.getRegion());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstanceInfoForServerInstanceTest() {
    AwsLambdaServerInstanceInfo awsLambdaServerInstanceInfo = AwsLambdaServerInstanceInfo.builder()
                                                                  .functionName(FUNCTION)
                                                                  .region(REGION)
                                                                  .version(VERSION)
                                                                  .memorySize(MEMORY_SIZE)
                                                                  .runtime(RUN_TIME)
                                                                  .source(SOURCE)
                                                                  .infrastructureKey(INFRA_KEY)
                                                                  .build();

    AwsLambdaInstanceInfoDTO awsLambdaInstanceInfoDTO =
        (AwsLambdaInstanceInfoDTO) awsLambdaInstanceSyncHandler.getInstanceInfoForServerInstance(
            awsLambdaServerInstanceInfo);

    assertThat(awsLambdaInstanceInfoDTO.getVersion()).isEqualTo(awsLambdaServerInstanceInfo.getVersion());
    assertThat(awsLambdaInstanceInfoDTO.getFunctionName()).isEqualTo(awsLambdaServerInstanceInfo.getFunctionName());
    assertThat(awsLambdaInstanceInfoDTO.getRegion()).isEqualTo(awsLambdaServerInstanceInfo.getRegion());
    assertThat(awsLambdaInstanceInfoDTO.getMemorySize()).isEqualTo(awsLambdaServerInstanceInfo.getMemorySize());
    assertThat(awsLambdaInstanceInfoDTO.getRunTime()).isEqualTo(awsLambdaServerInstanceInfo.getRuntime());
    assertThat(awsLambdaInstanceInfoDTO.getSource()).isEqualTo(awsLambdaServerInstanceInfo.getSource());

    assertThat(awsLambdaInstanceInfoDTO.getInfraStructureKey())
        .isEqualTo(awsLambdaServerInstanceInfo.getInfrastructureKey());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getDeploymentInfoTest() {
    AwsLambdaServerInstanceInfo awsLambdaServerInstanceInfo = AwsLambdaServerInstanceInfo.builder()
                                                                  .version(VERSION)
                                                                  .functionName(FUNCTION)
                                                                  .region(REGION)
                                                                  .infrastructureKey(INFRA_KEY)
                                                                  .build();
    InfrastructureOutcome infrastructureOutcome = AwsLambdaInfrastructureOutcome.builder().build();
    AwsLambdaDeploymentInfoDTO awsLambdaDeploymentInfoDTO =
        (AwsLambdaDeploymentInfoDTO) awsLambdaInstanceSyncHandler.getDeploymentInfo(
            infrastructureOutcome, Arrays.asList(awsLambdaServerInstanceInfo));

    assertThat(awsLambdaDeploymentInfoDTO.getVersion()).isEqualTo(awsLambdaServerInstanceInfo.getVersion());
    assertThat(awsLambdaDeploymentInfoDTO.getFunctionName()).isEqualTo(awsLambdaServerInstanceInfo.getFunctionName());
    assertThat(awsLambdaDeploymentInfoDTO.getRegion()).isEqualTo(awsLambdaServerInstanceInfo.getRegion());
    assertThat(awsLambdaDeploymentInfoDTO.getInfraStructureKey())
        .isEqualTo(awsLambdaServerInstanceInfo.getInfrastructureKey());
  }
}
