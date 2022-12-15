/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instance;

import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.instance.InstanceOutcomeHelper;
import io.harness.cdng.instance.outcome.HostOutcome;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.steps.OutputExpressionConstants;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.service.impl.aws.model.AwsEC2Instance;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class InstanceOutcomeHelperTest extends CategoryTest {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private InstanceOutcomeHelper instanceOutcomeHelper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndGetInstancesOutcomePDC() {
    InfrastructureOutcome infraOutcome = PdcInfrastructureOutcome.builder().build();
    Set<String> hostNames = new HashSet<>();
    hostNames.add("host1");
    hostNames.add("host2");
    Ambiance ambiance = Ambiance.newBuilder().build();
    doReturn("instances")
        .when(executionSweepingOutputService)
        .consume(eq(ambiance), eq(OutputExpressionConstants.INSTANCES), any(InstancesOutcome.class),
            eq(StepCategory.STAGE.name()));

    InstancesOutcome instancesOutcome =
        instanceOutcomeHelper.saveAndGetInstancesOutcome(ambiance, infraOutcome, null, hostNames);

    List<InstanceOutcome> instances = instancesOutcome.getInstances();
    assertThat(instances.size()).isEqualTo(2);

    InstanceOutcome instanceOutcome = instances.get(0);

    assertThat(instanceOutcome.getName()).isEqualTo("host1");
    assertThat(instanceOutcome.getHostName()).isEqualTo("host1");
    assertThat(instanceOutcome.getHost()).isEqualTo(HostOutcome.builder().hostName("host1").build());

    InstanceOutcome secondInstanceOutcome = instances.get(1);

    assertThat(secondInstanceOutcome.getName()).isEqualTo("host2");
    assertThat(secondInstanceOutcome.getHostName()).isEqualTo("host2");
    assertThat(secondInstanceOutcome.getHost()).isEqualTo(HostOutcome.builder().hostName("host2").build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndGetInstancesOutcomeAzure() {
    InfrastructureOutcome infraOutcome =
        SshWinRmAzureInfrastructureOutcome.builder().hostConnectionType(HostConnectionTypeKind.PRIVATE_IP).build();
    Set<String> hostNames = new HashSet<>();
    hostNames.add("hostName");
    Ambiance ambiance = Ambiance.newBuilder().build();
    AzureHostsResponse azureHostsResponse =
        AzureHostsResponse.builder()
            .hosts(Collections.singletonList(
                AzureHostResponse.builder().publicIp("publicIp").privateIp("privateIp").hostName("hostName").build()))
            .build();
    doReturn("instances")
        .when(executionSweepingOutputService)
        .consume(eq(ambiance), eq(OutputExpressionConstants.INSTANCES), any(InstancesOutcome.class),
            eq(StepCategory.STAGE.name()));

    InstancesOutcome instancesOutcome =
        instanceOutcomeHelper.saveAndGetInstancesOutcome(ambiance, infraOutcome, azureHostsResponse, hostNames);

    List<InstanceOutcome> instances = instancesOutcome.getInstances();
    assertThat(instances.size()).isEqualTo(1);

    InstanceOutcome instanceOutcome = instances.get(0);

    assertThat(instanceOutcome.getName()).isEqualTo("hostName");
    assertThat(instanceOutcome.getHostName()).isEqualTo("hostName");
    assertThat(instanceOutcome.getHost())
        .isEqualTo(HostOutcome.builder().hostName("hostName").privateIp("privateIp").publicIp("publicIp").build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndGetInstancesOutcomeAws() {
    InfrastructureOutcome infraOutcome =
        SshWinRmAwsInfrastructureOutcome.builder().hostConnectionType(HostConnectionTypeKind.PUBLIC_IP).build();
    Set<String> hostNames = new HashSet<>();
    hostNames.add("publicIp");
    Ambiance ambiance = Ambiance.newBuilder().build();
    AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse =
        AwsListEC2InstancesTaskResponse.builder()
            .instances(Collections.singletonList(
                AwsEC2Instance.builder().publicIp("publicIp").privateIp("privateIp").hostname("hostName").build()))
            .build();
    doReturn("instances")
        .when(executionSweepingOutputService)
        .consume(eq(ambiance), eq(OutputExpressionConstants.INSTANCES), any(InstancesOutcome.class),
            eq(StepCategory.STAGE.name()));

    InstancesOutcome instancesOutcome = instanceOutcomeHelper.saveAndGetInstancesOutcome(
        ambiance, infraOutcome, awsListEC2InstancesTaskResponse, hostNames);

    List<InstanceOutcome> instances = instancesOutcome.getInstances();
    assertThat(instances.size()).isEqualTo(1);

    InstanceOutcome instanceOutcome = instances.get(0);

    assertThat(instanceOutcome.getName()).isEqualTo("publicIp");
    assertThat(instanceOutcome.getHostName()).isEqualTo("publicIp");
    assertThat(instanceOutcome.getHost())
        .isEqualTo(HostOutcome.builder().hostName("hostName").privateIp("privateIp").publicIp("publicIp").build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testMapToHostNameBasedOnHostConnectionTypeAzure() {
    AzureHostResponse azureHostResponse =
        AzureHostResponse.builder().publicIp("publicIp").privateIp("privateIp").hostName("hostName").build();

    SshWinRmAzureInfrastructureOutcome azureInfrastructureOutcome =
        SshWinRmAzureInfrastructureOutcome.builder().build();
    String hostName = instanceOutcomeHelper.mapToHostNameBasedOnHostConnectionTypeAzure(
        azureInfrastructureOutcome, azureHostResponse);
    assertThat(hostName).isEqualTo("hostName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testMapToHostNameBasedOnHostConnectionTypeAws() {
    AwsEC2Instance awsEC2Instance =
        AwsEC2Instance.builder().publicIp("publicIp").privateIp("privateIp").hostname("hostName").build();
    SshWinRmAwsInfrastructureOutcome awsInfrastructureOutcome =
        SshWinRmAwsInfrastructureOutcome.builder().hostConnectionType(HostConnectionTypeKind.PUBLIC_IP).build();
    String hostName =
        instanceOutcomeHelper.mapToHostNameBasedOnHostConnectionTypeAWS(awsInfrastructureOutcome, awsEC2Instance);
    assertThat(hostName).isEqualTo("publicIp");

    awsInfrastructureOutcome =
        SshWinRmAwsInfrastructureOutcome.builder().hostConnectionType(HostConnectionTypeKind.PRIVATE_IP).build();
    hostName =
        instanceOutcomeHelper.mapToHostNameBasedOnHostConnectionTypeAWS(awsInfrastructureOutcome, awsEC2Instance);
    assertThat(hostName).isEqualTo("privateIp");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSaveAndGetInstancesOutcomeCustomDeployment() {
    InfrastructureOutcome infraOutcome =
        CustomDeploymentInfrastructureOutcome.builder()
            .instanceFetchScript("echo '{\"hosts\":[{\"ip\":\"1.1\"}, {\"ip\":\"2.2\"}]}' > $INSTANCE_OUTPUT_PATH")
            .variables(Collections.emptyMap())
            .instancesListPath("instanceListPath")
            .instanceAttributes(Collections.emptyMap())
            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "ACCOUNT_IDENTIFIER")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "ORG_IDENTIFIER")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "PROJECT_IDENTIFIER")
                            .setStageExecutionId("EXECUTION_ID")
                            .build();

    InstancesOutcome instancesOutcome =
        instanceOutcomeHelper.saveAndGetInstancesOutcome(ambiance, infraOutcome, Collections.emptySet());

    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.INSTANCES), any(), eq(StepCategory.STAGE.name()));

    List<InstanceOutcome> instances = instancesOutcome.getInstances();
    assertThat(instances.size()).isEqualTo(0);

    Set<String> hostNames = new HashSet<>(Arrays.asList("host1", "host2"));
    instancesOutcome = instanceOutcomeHelper.saveAndGetInstancesOutcome(ambiance, infraOutcome, hostNames);
    assertThat(instancesOutcome.getInstances().size()).isEqualTo(0);
  }
}
