/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.shell.ScriptType.BASH;

import static software.wings.beans.ConnectionType.SSH;
import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.rule.Owner;
import io.harness.shell.AccessType;
import io.harness.shell.SshSessionConfig;

import software.wings.WingsBaseTest;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.delegatetasks.validation.capabilities.ShellConnectionCapability;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.WingsTestConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellConnectionCapabilityCheckTest extends WingsBaseTest {
  @Mock private EncryptionService encryptionService;
  @Spy @InjectMocks ShellConnectionCapabilityCheck shellConnectionCapabilityCheck;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCapabilityCheck() throws Exception {
    doNothing().when(shellConnectionCapabilityCheck).performTest(any(SshSessionConfig.class));
    CapabilityResponse capabilityResponse = shellConnectionCapabilityCheck.performCapabilityCheck(
        ShellConnectionCapability.builder()
            .shellScriptParameters(ShellScriptParameters.builder()
                                       .accountId(ACCOUNT_ID)
                                       .appId(APP_ID)
                                       .activityId(ACTIVITY_ID)
                                       .executeOnDelegate(false)
                                       .connectionType(SSH)
                                       .scriptType(BASH)
                                       .hostConnectionAttributes(aHostConnectionAttributes()
                                                                     .withAccessType(AccessType.USER_PASSWORD)
                                                                     .withAccountId(WingsTestConstants.ACCOUNT_ID)
                                                                     .build())
                                       .build())
            .build());
    assertThat(capabilityResponse).isNotNull();
    assertThat(capabilityResponse.isValidated()).isTrue();
  }
}
