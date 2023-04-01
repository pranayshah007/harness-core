/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRASHANT;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.infrastructure.Host;
import software.wings.delegatetasks.validation.capabilities.BasicValidationInfo;
import software.wings.delegatetasks.validation.capabilities.WinrmHostValidationCapability;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.WingsTestConstants;

import com.jcraft.jsch.JSchException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class WinrmHostValidationCapabilityCheckTest extends WingsBaseTest {
  @Mock private WinRmSession mockSession;
  @Mock private EncryptionService encryptionService;
  @Spy @InjectMocks private WinrmHostValidationCapabilityCheck spyCapabilityCheck;

  private Host host = Host.Builder.aHost().withPublicDns(WingsTestConstants.PUBLIC_DNS).build();
  private WinRmConnectionAttributes winrmConnectionAttributes = WinRmConnectionAttributes.builder()
                                                                    .username("userName")
                                                                    .password("password".toCharArray())
                                                                    .authenticationScheme(AuthenticationScheme.NTLM)
                                                                    .port(22)
                                                                    .skipCertChecks(true)
                                                                    .accountId(ACCOUNT_ID)
                                                                    .useSSL(true)
                                                                    .domain("")
                                                                    .build();

  private WinrmHostValidationCapability capability = WinrmHostValidationCapability.builder()
                                                         .validationInfo(BasicValidationInfo.builder()
                                                                             .accountId(ACCOUNT_ID)
                                                                             .appId(APP_ID)
                                                                             .activityId(ACTIVITY_ID)
                                                                             .executeOnDelegate(false)
                                                                             .publicDns(host.getPublicDns())
                                                                             .build())
                                                         .winRmConnectionAttributes(winrmConnectionAttributes)
                                                         .winrmConnectionEncryptedDataDetails(new ArrayList<>())
                                                         .build();

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheck() throws JSchException {
    doReturn(mockSession).when(spyCapabilityCheck).makeSession(any(WinRmSessionConfig.class), any(LogCallback.class));
    CapabilityResponse capabilityResponse = spyCapabilityCheck.performCapabilityCheck(capability);
    assertThat(capabilityResponse).isNotNull();
    assertThat(capabilityResponse.isValidated()).isTrue();
  }
}
