/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsTaskHelperBaseTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Mock private EcsCommandTaskNGHelper ecsCommandTaskNGHelper;

  @Spy @InjectMocks private EcsTaskHelperBase ecsTaskHelperBase;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getLogCallbackTest() {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsTaskHelperBase.getLogCallback(logStreamingTaskClient, "commandName", true, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getEcsServerInstanceInfosTest() {
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                        .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                        .region("us-east-1")
                                        .cluster("cluster")
                                        .infraStructureKey("infraKey")
                                        .build();
    EcsDeploymentReleaseData deploymentReleaseData =
        EcsDeploymentReleaseData.builder().ecsInfraConfig(ecsInfraConfig).serviceName("service").build();
    EcsTask ecsTask = EcsTask.builder().clusterArn("arn").launchType("FARGATE").build();

    doReturn(Arrays.asList(ecsTask))
        .when(ecsCommandTaskNGHelper)
        .getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
            deploymentReleaseData.getServiceName(), ecsInfraConfig.getRegion());
    ecsCommandTaskNGHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
        deploymentReleaseData.getServiceName(), ecsInfraConfig.getRegion());
    EcsServerInstanceInfo ecsServerInstanceInfo =
        (EcsServerInstanceInfo) ecsTaskHelperBase.getEcsServerInstanceInfos(deploymentReleaseData).get(0);
    assertThat(ecsServerInstanceInfo.getClusterArn()).isEqualTo(ecsTask.getClusterArn());
    assertThat(ecsServerInstanceInfo.getLaunchType()).isEqualTo(ecsTask.getLaunchType());
    assertThat(ecsServerInstanceInfo.getRegion()).isEqualTo(ecsInfraConfig.getRegion());
  }
}
