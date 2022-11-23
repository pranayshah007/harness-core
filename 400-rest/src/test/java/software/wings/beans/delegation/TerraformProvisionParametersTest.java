/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.delegation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.KmsConfig;
import software.wings.beans.dto.SettingAttribute;

import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class TerraformProvisionParametersTest extends WingsBaseTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void fetchRequiredExecutionCapabilities() {
    testWithGitConfig();
    testWithGitConfigSSHConnection();
    testWithoutGitConfig();
    testWithSecretManagerConfig();
  }

  private void testWithoutGitConfig() {
    assertThat(TerraformProvisionParameters.builder()
                   .build()
                   .fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR);
  }

  private void testWithGitConfig() {
    TerraformProvisionParameters parameters =
        getTerraformProvisionParameters(true, "https://github.com/abc", null, null);
    assertThat(parameters.fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR, CapabilityType.HTTP);

    parameters = getTerraformProvisionParameters(false, "https://github.com/abc", null, null);
    assertThat(parameters.fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR, CapabilityType.GIT_CONNECTION);
  }

  private void testWithGitConfigSSHConnection() {
    HostConnectionAttributes hostConnectionAttributes = new HostConnectionAttributes();
    hostConnectionAttributes.setSshPort(22);
    SettingAttribute sshSettingAttribute = SettingAttribute.builder().build();
    sshSettingAttribute.setValue(hostConnectionAttributes);
    TerraformProvisionParameters parameters =
        getTerraformProvisionParameters(true, "git@github.com:abc", sshSettingAttribute, null);

    assertThat(parameters.fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR, CapabilityType.SOCKET);

    parameters = getTerraformProvisionParameters(false, "git@github.com:abc", sshSettingAttribute, null);
    assertThat(parameters.fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR, CapabilityType.GIT_CONNECTION);
  }

  private void testWithSecretManagerConfig() {
    TerraformProvisionParameters parameters =
        getTerraformProvisionParameters(true, "https://github.com/abc", null, KmsConfig.builder().build());
    assertThat(parameters.fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR, CapabilityType.HTTP, CapabilityType.HTTP);

    parameters = getTerraformProvisionParameters(false, "https://github.com/abc", null, KmsConfig.builder().build());
    assertThat(parameters.fetchRequiredExecutionCapabilities(null)
                   .stream()
                   .map(ExecutionCapability::getCapabilityType)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(CapabilityType.PROCESS_EXECUTOR, CapabilityType.GIT_CONNECTION, CapabilityType.HTTP);
  }

  private TerraformProvisionParameters getTerraformProvisionParameters(boolean isGitHostConnectivityCheck,
      String repoUrl, SettingAttribute sshSettingAttribute, SecretManagerConfig secretManagerConfig) {
    return TerraformProvisionParameters.builder()
        .sourceRepo(GitConfig.builder().repoUrl(repoUrl).sshSettingAttribute(sshSettingAttribute).build())
        .secretManagerConfig(secretManagerConfig)
        .isGitHostConnectivityCheck(isGitHostConnectivityCheck)
        .build();
  }
}
