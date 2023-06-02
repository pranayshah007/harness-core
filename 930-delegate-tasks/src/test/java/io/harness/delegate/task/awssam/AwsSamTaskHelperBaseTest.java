/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.awssam;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.rule.Owner;
import io.harness.serverless.model.AwsLambdaFunctionDetails;

import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsSamTaskHelperBaseTest {
  private final String FUNCTION = "fun";
  private final String REGION = "us-east1";
  private final String RUN_TIME = "java8";
  private final String HANDLER = "index.handler";
  private final String MEMORY_SIZE = "512MiB";
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;
  @Mock private AwsSamInfraConfigHelper awsSamInfraConfigHelper;

  @InjectMocks private AwsSamTaskHelperBase awsSamTaskHelperBase;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getAwsSamServerInstanceInfosTest() {
    AwsSamInfraConfig awsSamInfraConfig = AwsSamInfraConfig.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsSamInfraConfig.getAwsConnectorDTO());
    AwsSamDeploymentReleaseData deploymentReleaseData = AwsSamDeploymentReleaseData.builder()
                                                            .awsSamInfraConfig(awsSamInfraConfig)
                                                            .functions(Arrays.asList(FUNCTION))
                                                            .region(REGION)
                                                            .build();

    AwsLambdaFunctionDetails awsLambdaFunctionDetails = AwsLambdaFunctionDetails.builder()
                                                            .functionName(FUNCTION)
                                                            .handler(HANDLER)
                                                            .runTime(RUN_TIME)
                                                            .memorySize(MEMORY_SIZE)
                                                            .build();
    doReturn(awsLambdaFunctionDetails)
        .when(awsLambdaHelperServiceDelegateNG)
        .getAwsLambdaFunctionDetails(awsInternalConfig, FUNCTION, deploymentReleaseData.getRegion());

    AwsSamServerInstanceInfo awsSamServerInstanceInfo =
        (AwsSamServerInstanceInfo) awsSamTaskHelperBase.getAwsSamServerInstanceInfos(deploymentReleaseData).get(0);

    assertThat(awsSamServerInstanceInfo.getFunctionName()).isEqualTo(deploymentReleaseData.getFunctions().get(0));
    assertThat(awsSamServerInstanceInfo.getHandler()).isEqualTo(awsLambdaFunctionDetails.getHandler());
    assertThat(awsSamServerInstanceInfo.getMemorySize()).isEqualTo(awsLambdaFunctionDetails.getMemorySize());
    assertThat(awsSamServerInstanceInfo.getRunTime()).isEqualTo(awsLambdaFunctionDetails.getRunTime());
  }
}
