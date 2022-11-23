/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.beans.command.KubernetesResizeParams.KubernetesResizeParamsBuilder.aKubernetesResizeParams;
import static software.wings.beans.command.KubernetesSetupParams.KubernetesSetupParamsBuilder.aKubernetesSetupParams;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.DOMAIN;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.delegatetasks.validation.capabilities.SSHHostValidationCapability;
import software.wings.delegatetasks.validation.capabilities.WinrmHostValidationCapability;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CommandExecutionContextTest extends WingsBaseTest {
  private static final String MASTER_URL = "http://example.com";

  CommandExecutionContext.Builder contextBuilder =
      aCommandExecutionContext()
          .appId(APP_ID)
          .envId(ENV_ID)
          .accountId(ACCOUNT_ID)
          .activityId(ACTIVITY_ID)
          .host(aHost().withPublicDns(WingsTestConstants.PUBLIC_DNS).build())
          .winRmConnectionAttributes(WinRmConnectionAttributes.builder()
                                         .accountId(ACCOUNT_ID)
                                         .username(USER_NAME)
                                         .password(PASSWORD)
                                         .domain(DOMAIN)
                                         .port(22)
                                         .useSSL(true)
                                         .skipCertChecks(true)
                                         .build());

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesK8sWithDelegate() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.KUBERNETES.name())
            .cloudProviderSetting(SettingAttributeTestHelper.obtainKubernetesClusterSettingAttribute(true).toDTO())
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0)).isExactlyInstanceOf(SelectorCapability.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesWinrm() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.WINRM.name())
            .cloudProviderSetting(SettingAttributeTestHelper.obtainWinrmSettingAttribute().toDTO())
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0)).isExactlyInstanceOf(WinrmHostValidationCapability.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesWithDelegateSelectorsWinrm() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.WINRM.name())
            .cloudProviderSetting(SettingAttributeTestHelper.obtainWinrmSettingAttribute().toDTO())
            .delegateSelectors(Arrays.asList("selector1", "selector2", "selector3"))
            .executeOnDelegate(false)
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(2);
    assertThat(executionCapabilities.get(0)).isExactlyInstanceOf(WinrmHostValidationCapability.class);
    assertThat(executionCapabilities.get(1)).isExactlyInstanceOf(SelectorCapability.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesSSH() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.SSH.name())
            .cloudProviderSetting(SettingAttributeTestHelper.obtainSshSettingAttribute().toDTO())
            .executionCredential(aSSHExecutionCredential().withSshUser(USER_NAME).withSshPassword(PASSWORD).build())
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0)).isExactlyInstanceOf(SSHHostValidationCapability.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesWithDelegateSelectorsSSH() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.SSH.name())
            .cloudProviderSetting(SettingAttributeTestHelper.obtainSshSettingAttribute().toDTO())
            .executionCredential(aSSHExecutionCredential().withSshUser(USER_NAME).withSshPassword(PASSWORD).build())
            .delegateSelectors(Arrays.asList("selector1", "selector2", "selector3"))
            .executeOnDelegate(true)
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(2);
    assertThat(executionCapabilities.get(0)).isExactlyInstanceOf(SSHHostValidationCapability.class);
    assertThat(executionCapabilities.get(1)).isExactlyInstanceOf(SelectorCapability.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesECSResize() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.ECS.name())
            .containerResizeParams(SettingAttributeTestHelper.obtainECSResizeParams())
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).isEmpty();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesECSSetup() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.ECS.name())
            .containerSetupParams(SettingAttributeTestHelper.obtainECSSetupParams())
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).isEmpty();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesAWSCodeDeploy() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.AWS_CODEDEPLOY.name()).build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesAMI() {
    CommandExecutionContext executionContext = contextBuilder.deploymentType(DeploymentType.AMI.name()).build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(0);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesLambda() {
    CommandExecutionContext executionContext = contextBuilder.deploymentType(DeploymentType.AWS_LAMBDA.name()).build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(0);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilitiesK8sWithParams() {
    CommandExecutionContext executionContext =
        contextBuilder.deploymentType(DeploymentType.KUBERNETES.name())
            .cloudProviderSetting(SettingAttributeTestHelper.obtainKubernetesClusterSettingAttribute(false).toDTO())
            .containerSetupParams(aKubernetesSetupParams().withMasterUrl(MASTER_URL).build())
            .build();

    List<ExecutionCapability> executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0).fetchCapabilityBasis()).isEqualTo(MASTER_URL);

    executionContext.setContainerSetupParams(null);
    executionContext.setContainerResizeParams(aKubernetesResizeParams().withMasterUrl(MASTER_URL + "Resize").build());
    executionCapabilities = executionContext.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0).fetchCapabilityBasis()).isEqualTo(MASTER_URL + "Resize");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldNotSetEnvVariablesWithFF() {
    CommandExecutionContext context;
    context = aCommandExecutionContext()
                  .host(aHost().build())
                  .winRmConnectionAttributes(WinRmConnectionAttributes.builder().useKeyTab(true).build())
                  .envVariables(ImmutableMap.of("k1", "v1"))
                  .build();
    assertThat(context.winrmSessionConfig("Execute", "foo").getEnvironment()).containsKey("k1");

    context = aCommandExecutionContext()
                  .envVariables(ImmutableMap.of("k1", "v1"))
                  .host(aHost().build())
                  .winRmConnectionAttributes(WinRmConnectionAttributes.builder().useKeyTab(true).build())
                  .disableWinRMEnvVariables(true)
                  .build();
    assertThat(context.winrmSessionConfig("Execute", "foo").getEnvironment()).isEmpty();
  }
}
