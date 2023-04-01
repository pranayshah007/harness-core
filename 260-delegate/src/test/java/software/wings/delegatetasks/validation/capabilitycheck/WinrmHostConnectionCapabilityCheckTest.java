/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.WinrmConnectivityExecutionCapability;
import io.harness.delegate.task.executioncapability.SocketConnectivityCapabilityCheck;
import io.harness.delegate.task.ssh.PdcWinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.WinRmAuthScheme;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.WingsBaseTest;

import com.google.common.collect.Sets;
import com.jcraft.jsch.JSchException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class WinrmHostConnectionCapabilityCheckTest extends WingsBaseTest {
  private static final char[] DECRYPTED_PASSWORD_VALUE = {'t', 'e', 's', 't'};
  @Mock private WinRmSession mockSession;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Spy @InjectMocks private WinrmHostConnectionCapabilityCheck spyCapabilityCheck;

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheckKerberosPwd() throws JSchException {
    WinrmConnectivityExecutionCapability capability =
        WinrmConnectivityExecutionCapability.builder()
            .winRmInfraDelegateConfig(
                PdcWinRmInfraDelegateConfig.builder()
                    .winRmCredentials(WinRmCredentialsSpecDTO.builder()
                                          .port(1234)
                                          .auth(WinRmAuthDTO.builder()
                                                    .type(WinRmAuthScheme.Kerberos)
                                                    .spec(KerberosWinRmConfigDTO.builder()
                                                              .tgtGenerationMethod(TGTGenerationMethod.Password)
                                                              .build())
                                                    .build())
                                          .build())
                    .hosts(Sets.newHashSet("host1"))
                    .build())
            .useWinRMKerberosUniqueCacheFile(true)
            .host("host1")
            .build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(TGTPasswordSpecDTO.builder()
                        .password(SecretRefData.builder().decryptedValue(DECRYPTED_PASSWORD_VALUE).build())
                        .build());

    doReturn(mockSession).when(spyCapabilityCheck).connect(any(WinRmSessionConfig.class));
    CapabilityResponse capabilityResponse = spyCapabilityCheck.performCapabilityCheck(capability);
    assertThat(capabilityResponse).isNotNull();
    assertThat(capabilityResponse.isValidated()).isTrue();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheckKerberosTGT() throws JSchException {
    WinrmConnectivityExecutionCapability capability =
        WinrmConnectivityExecutionCapability.builder()
            .winRmInfraDelegateConfig(
                PdcWinRmInfraDelegateConfig.builder()
                    .winRmCredentials(
                        WinRmCredentialsSpecDTO.builder()
                            .port(1234)
                            .auth(WinRmAuthDTO.builder()
                                      .type(WinRmAuthScheme.Kerberos)
                                      .spec(KerberosWinRmConfigDTO.builder()
                                                .tgtGenerationMethod(TGTGenerationMethod.KeyTabFilePath)
                                                .spec(TGTKeyTabFilePathSpecDTO.builder().keyPath("path").build())
                                                .build())
                                      .build())
                            .build())
                    .hosts(Sets.newHashSet("host1"))
                    .build())
            .useWinRMKerberosUniqueCacheFile(true)
            .host("host1")
            .build();

    doReturn(mockSession).when(spyCapabilityCheck).connect(any(WinRmSessionConfig.class));
    CapabilityResponse capabilityResponse = spyCapabilityCheck.performCapabilityCheck(capability);
    assertThat(capabilityResponse).isNotNull();
    assertThat(capabilityResponse.isValidated()).isTrue();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheckNtlm() {
    WinrmConnectivityExecutionCapability capability =
        WinrmConnectivityExecutionCapability.builder()
            .winRmInfraDelegateConfig(PdcWinRmInfraDelegateConfig.builder()
                                          .winRmCredentials(WinRmCredentialsSpecDTO.builder()
                                                                .port(1234)
                                                                .auth(WinRmAuthDTO.builder()
                                                                          .type(WinRmAuthScheme.NTLM)
                                                                          .spec(NTLMConfigDTO.builder().build())
                                                                          .build())
                                                                .build())
                                          .hosts(Sets.newHashSet("host1"))
                                          .build())
            .useWinRMKerberosUniqueCacheFile(true)
            .host("host1")
            .build();

    try (MockedStatic<SocketConnectivityCapabilityCheck> utilities =
             Mockito.mockStatic(SocketConnectivityCapabilityCheck.class)) {
      utilities.when(() -> SocketConnectivityCapabilityCheck.connectableHost(anyString(), anyInt())).thenReturn(true);
      CapabilityResponse capabilityResponse = spyCapabilityCheck.performCapabilityCheck(capability);
      assertThat(capabilityResponse).isNotNull();
      assertThat(capabilityResponse.isValidated()).isTrue();
    }
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheckFalse() throws JSchException {
    WinrmConnectivityExecutionCapability capability =
        WinrmConnectivityExecutionCapability.builder()
            .winRmInfraDelegateConfig(
                PdcWinRmInfraDelegateConfig.builder()
                    .winRmCredentials(WinRmCredentialsSpecDTO.builder()
                                          .port(1234)
                                          .auth(WinRmAuthDTO.builder()
                                                    .type(WinRmAuthScheme.Kerberos)
                                                    .spec(KerberosWinRmConfigDTO.builder()
                                                              .tgtGenerationMethod(TGTGenerationMethod.Password)
                                                              .build())
                                                    .build())
                                          .build())
                    .hosts(Sets.newHashSet("host1"))
                    .build())
            .useWinRMKerberosUniqueCacheFile(true)
            .host("host1")
            .build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(TGTPasswordSpecDTO.builder()
                        .password(SecretRefData.builder().decryptedValue(DECRYPTED_PASSWORD_VALUE).build())
                        .build());

    doThrow(new RuntimeException()).when(spyCapabilityCheck).connect(any(WinRmSessionConfig.class));
    CapabilityResponse capabilityResponse = spyCapabilityCheck.performCapabilityCheck(capability);
    assertThat(capabilityResponse).isNotNull();
    assertThat(capabilityResponse.isValidated()).isFalse();
  }
}
