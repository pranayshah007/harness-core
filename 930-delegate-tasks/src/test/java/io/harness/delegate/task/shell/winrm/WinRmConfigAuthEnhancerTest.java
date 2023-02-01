/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.WinRmAuthScheme;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.Silent.class)
public class WinRmConfigAuthEnhancerTest {
  @InjectMocks WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock SecretDecryptionService secretDecryptionService;

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testConfigureAuthentication_NTLM() {
    NTLMConfigDTO ntlmConfigDTO = NTLMConfigDTO.builder().build();
    WinRmAuthDTO auth = WinRmAuthDTO.builder().type(WinRmAuthScheme.NTLM).spec(ntlmConfigDTO).build();
    WinRmCredentialsSpecDTO credentialsSpecDTO = WinRmCredentialsSpecDTO.builder().auth(auth).build();
    SecretRefData secretRefData =
        SecretRefData.builder().decryptedValue(null).identifier("secretIdentifier").scope(Scope.ACCOUNT).build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(NTLMConfigDTO.builder().password(secretRefData).domain("domain").build());

    assertThatThrownBy(()
                           -> winRmConfigAuthEnhancer.configureAuthentication(
                               credentialsSpecDTO, Lists.newArrayList(), WinRmSessionConfig.builder(), false))
        .isInstanceOf(HintException.class)
        .hasMessage("Please ensure secret with identifier secretIdentifier exist.")
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .hasMessage("Decrypted value was empty.")
        .getCause()
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Decrypted value of the secret is null.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testConfigureAuthentication_Kerberos() {
    KerberosWinRmConfigDTO kerberosWinRmConfigDTO =
        KerberosWinRmConfigDTO.builder().tgtGenerationMethod(TGTGenerationMethod.Password).build();
    WinRmAuthDTO auth = WinRmAuthDTO.builder().type(WinRmAuthScheme.Kerberos).spec(kerberosWinRmConfigDTO).build();
    WinRmCredentialsSpecDTO credentialsSpecDTO = WinRmCredentialsSpecDTO.builder().auth(auth).build();
    SecretRefData secretRefData =
        SecretRefData.builder().decryptedValue(null).identifier("secretIdentifier").scope(Scope.ACCOUNT).build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(TGTPasswordSpecDTO.builder().password(secretRefData).build());

    assertThatThrownBy(()
                           -> winRmConfigAuthEnhancer.configureAuthentication(
                               credentialsSpecDTO, Lists.newArrayList(), WinRmSessionConfig.builder(), false))
        .isInstanceOf(HintException.class)
        .hasMessage("Please ensure secret with identifier secretIdentifier exist.")
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .hasMessage("Decrypted value was empty.")
        .getCause()
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Decrypted value of the secret is null.");
  }
}
