/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.cdng.secrets.tasks;

import static io.harness.cdng.secrets.tasks.SshSessionConfigHelper.generateKerberosBuilder;
import static io.harness.cdng.secrets.tasks.SshSessionConfigHelper.generateSSHBuilder;
import static io.harness.rule.OwnerRule.BOJAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshSessionConfigHelperTest extends CategoryTest {
  @Mock SecretDecryptionService secretDecryptionService;

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void generateKerberosBuilderTest_NTLM_Password() {
    SecretRefData secretRefData =
        SecretRefData.builder().decryptedValue(null).identifier("secretIdentifier").scope(Scope.ACCOUNT).build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SSHPasswordCredentialDTO.builder().password(secretRefData).build());

    SSHConfigDTO sshConfigDTO = SSHConfigDTO.builder().credentialType(SSHCredentialType.Password).build();
    assertThatThrownBy(
        ()
            -> generateSSHBuilder(SSHAuthDTO.builder().build(), sshConfigDTO,
                SshSessionConfig.Builder.aSshSessionConfig(), Lists.newArrayList(), secretDecryptionService))
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
  public void generateKerberosBuilderTest_NTLM_KeyReference() {
    SecretRefData secretRefData =
        SecretRefData.builder().decryptedValue(null).identifier("secretIdentifier").scope(Scope.ACCOUNT).build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(SSHKeyReferenceCredentialDTO.builder().key(secretRefData).build());

    SSHConfigDTO sshConfigDTO = SSHConfigDTO.builder().credentialType(SSHCredentialType.KeyReference).build();
    assertThatThrownBy(
        ()
            -> generateSSHBuilder(SSHAuthDTO.builder().build(), sshConfigDTO,
                SshSessionConfig.Builder.aSshSessionConfig(), Lists.newArrayList(), secretDecryptionService))
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
  public void generateKerberosBuilderTest_Kerberos_Password() {
    SecretRefData secretRefData =
        SecretRefData.builder().decryptedValue(null).identifier("secretIdentifier").scope(Scope.ACCOUNT).build();

    when(secretDecryptionService.decrypt(any(), any()))
        .thenReturn(TGTPasswordSpecDTO.builder().password(secretRefData).build());

    KerberosConfigDTO kerberosConfigDTO =
        KerberosConfigDTO.builder().tgtGenerationMethod(TGTGenerationMethod.Password).build();
    assertThatThrownBy(
        ()
            -> generateKerberosBuilder(SSHAuthDTO.builder().build(), kerberosConfigDTO,
                SshSessionConfig.Builder.aSshSessionConfig(), Lists.newArrayList(), secretDecryptionService))
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
