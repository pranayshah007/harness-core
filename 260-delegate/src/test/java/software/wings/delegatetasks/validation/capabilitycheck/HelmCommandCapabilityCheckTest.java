/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.task.helm.HelmCommandResponse;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.validation.capabilities.HelmCommandCapability;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.HelmDeployService;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCommandCapabilityCheckTest extends WingsBaseTest {
  @InjectMocks HelmCommandCapabilityCheck helmCommandCapabilityCheck;
  @Mock private HelmDeployService helmDeployService;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCapabilityCheck() {
    HelmCommandRequest commandRequest = HelmInstallCommandRequest.builder().helmVersion(HelmVersion.V3).build();
    HelmCommandResponse helmCommandResponse = new HelmCommandResponse(CommandExecutionStatus.SUCCESS, "");
    when(containerDeploymentDelegateHelper.createAndGetKubeConfigLocation(any())).thenReturn("kubeConfig");
    when(helmDeployService.ensureHelmInstalled(commandRequest)).thenReturn(helmCommandResponse);
    CapabilityResponse response = helmCommandCapabilityCheck.performCapabilityCheck(
        HelmCommandCapability.builder().commandRequest(commandRequest).build());
    assertThat(response).isNotNull();
    assertThat(response.isValidated()).isTrue();
  }
}
