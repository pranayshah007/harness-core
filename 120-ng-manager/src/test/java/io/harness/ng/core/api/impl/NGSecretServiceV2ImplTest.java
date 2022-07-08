/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.secrets.SSHConfigValidationTaskResponse;
import io.harness.delegate.beans.secrets.WinRmConfigValidationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.api.NGSecretActivityService;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.models.KerberosConfig;
import io.harness.ng.core.models.KerberosWinRmConfig;
import io.harness.ng.core.models.NTLMConfig;
import io.harness.ng.core.models.SSHAuth;
import io.harness.ng.core.models.SSHConfig;
import io.harness.ng.core.models.SSHExecutionCredentialSpec;
import io.harness.ng.core.models.SSHKeyCredential;
import io.harness.ng.core.models.SSHKeyPathCredential;
import io.harness.ng.core.models.SSHPasswordCredential;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.TGTKeyTabFilePathSpec;
import io.harness.ng.core.models.TGTPasswordSpec;
import io.harness.ng.core.models.WinRmAuth;
import io.harness.ng.core.models.WinRmCredentialsSpec;
import io.harness.ng.core.remote.SSHKeyValidationMetadata;
import io.harness.ng.core.remote.SecretValidationResultDTO;
import io.harness.ng.core.remote.WinRmCredentialsValidationMetadata;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.SecretRepository;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.WinRmAuthScheme;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class NGSecretServiceV2ImplTest extends CategoryTest {
  private SecretRepository secretRepository;
  private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private NGSecretServiceV2Impl secretServiceV2;
  private NGSecretServiceV2Impl secretServiceV2Spy;
  private NGSecretActivityService ngSecretActivityService;
  private OutboxService outboxService;
  private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private TransactionTemplate transactionTemplate;
  private AccessControlClient accessControlClient;
  @Before
  public void setup() {
    secretRepository = mock(SecretRepository.class);
    delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);
    ngSecretActivityService = mock(NGSecretActivityService.class);
    outboxService = mock(OutboxService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    taskSetupAbstractionHelper = new TaskSetupAbstractionHelper();
    accessControlClient = mock(AccessControlClient.class);
    SshKeySpecDTOHelper sshKeySpecDTOHelper = mock(SshKeySpecDTOHelper.class);
    WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper = mock(WinRmCredentialsSpecDTOHelper.class);

    secretServiceV2 = new NGSecretServiceV2Impl(secretRepository, delegateGrpcClientWrapper, sshKeySpecDTOHelper,
        ngSecretActivityService, outboxService, transactionTemplate, taskSetupAbstractionHelper,
        winRmCredentialsSpecDTOHelper, accessControlClient);
    secretServiceV2Spy = spy(secretServiceV2);
  }

  private SecretDTOV2 getSecretDTO() {
    return SecretDTOV2.builder()
        .name("name")
        .type(SecretType.SecretText)
        .identifier("identifier")
        .tags(Maps.newHashMap(ImmutableMap.of("a", "b")))
        .build();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGet() {
    when(secretRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Optional<Secret> secretOptional = secretServiceV2.get("account", null, null, "identifier");
    assertThat(secretOptional).isEqualTo(Optional.empty());
    verify(secretRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete() {
    Secret secret = Secret.builder().build();
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    doNothing().when(secretRepository).delete(any());
    when(transactionTemplate.execute(any())).thenReturn(true);
    boolean success = secretServiceV2Spy.delete("account", "org", "proj", "identifier");
    assertThat(success).isTrue();
    verify(secretServiceV2Spy).get(any(), any(), any(), any());
    verify(secretRepository, times(0)).delete(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreate() {
    SecretDTOV2 secretDTOV2 = getSecretDTO();
    Secret secret = secretDTOV2.toEntity();
    when(secretRepository.save(any())).thenReturn(secret);
    when(transactionTemplate.execute(any())).thenReturn(secret);

    Secret savedSecret = secretServiceV2.create("account", secretDTOV2, false);
    assertThat(secret).isNotNull();
    assertThat(secret).isEqualTo(savedSecret);
    verify(secretRepository, times(0)).save(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate() {
    Secret secret = Secret.builder().build();
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    SecretDTOV2 secretDTOV2 = getSecretDTO();
    when(secretRepository.save(any())).thenReturn(secret);
    when(transactionTemplate.execute(any())).thenReturn(secret);

    Secret success = secretServiceV2Spy.update("account", secretDTOV2, false);
    assertThat(success).isNotNull();
    verify(secretServiceV2Spy).get(any(), any(), any(), any());
    verify(secretRepository, times(0)).save(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidateForNonSSHType() {
    Secret secret = Secret.builder().type(SecretType.SecretText).build();
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    SecretValidationResultDTO secretValidationResultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", null);
    assertThat(secretValidationResultDTO.isSuccess()).isEqualTo(false);
  }

  private SSHKeyValidationMetadata getMetadata() {
    return SSHKeyValidationMetadata.builder().host("1.2.3.4").build();
  }

  private Secret getSecret() {
    return Secret.builder().type(SecretType.SSHKey).build();
  }

  private WinRmCredentialsValidationMetadata getWinRmMetaData() {
    return WinRmCredentialsValidationMetadata.builder().host("test").build();
  }

  private Secret getWinRmSecret() {
    return Secret.builder().type(SecretType.WinRmCredentials).build();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidationForSSHWithPassword() {
    Secret secret = getSecret();
    secret.setSecretSpec(SSHExecutionCredentialSpec.builder()
                             .port(22)
                             .auth(SSHAuth.builder()
                                       .type(SSHAuthScheme.SSH)
                                       .sshSpec(SSHConfig.builder()
                                                    .credentialType(SSHCredentialType.Password)
                                                    .spec(SSHPasswordCredential.builder()
                                                              .userName("username")
                                                              .password(SecretRefData.builder().build())
                                                              .build())
                                                    .build())
                                       .build())
                             .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getMetadata());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidationForSSHWithKeyReference() {
    Secret secret = getSecret();
    secret.setSecretSpec(SSHExecutionCredentialSpec.builder()
                             .port(22)
                             .auth(SSHAuth.builder()
                                       .type(SSHAuthScheme.SSH)
                                       .sshSpec(SSHConfig.builder()
                                                    .credentialType(SSHCredentialType.KeyReference)
                                                    .spec(SSHKeyCredential.builder()
                                                              .userName("username")
                                                              .key(SecretRefData.builder().build())
                                                              .build())
                                                    .build())
                                       .build())
                             .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getMetadata());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidationForSSHWithKeyPath() {
    Secret secret = getSecret();
    secret.setSecretSpec(
        SSHExecutionCredentialSpec.builder()
            .port(22)
            .auth(SSHAuth.builder()
                      .type(SSHAuthScheme.SSH)
                      .sshSpec(SSHConfig.builder()
                                   .credentialType(SSHCredentialType.KeyPath)
                                   .spec(SSHKeyPathCredential.builder().userName("username").keyPath("/a/b/c").build())
                                   .build())
                      .build())
            .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getMetadata());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidationForKerberos() {
    Secret secret = getSecret();
    secret.setSecretSpec(SSHExecutionCredentialSpec.builder()
                             .port(22)
                             .auth(SSHAuth.builder()
                                       .type(SSHAuthScheme.Kerberos)
                                       .sshSpec(KerberosConfig.builder()
                                                    .principal("principal")
                                                    .realm("realm")
                                                    .tgtGenerationMethod(TGTGenerationMethod.KeyTabFilePath)
                                                    .spec(TGTKeyTabFilePathSpec.builder().keyPath("/a/b/c").build())
                                                    .build())
                                       .build())
                             .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getMetadata());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidationForWinRmNTLM() {
    Secret secret = getWinRmSecret();
    secret.setSecretSpec(
        WinRmCredentialsSpec.builder()
            .port(5986)
            .auth(WinRmAuth.builder()
                      .type(WinRmAuthScheme.NTLM)
                      .spec(NTLMConfig.builder().username("user").password(SecretRefData.builder().build()).build())
                      .build())
            .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(WinRmConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getWinRmMetaData());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidationForWinRmKerberosPassword() {
    Secret secret = getWinRmSecret();
    secret.setSecretSpec(
        WinRmCredentialsSpec.builder()
            .port(5986)
            .auth(WinRmAuth.builder()
                      .type(WinRmAuthScheme.Kerberos)
                      .spec(KerberosWinRmConfig.builder()
                                .principal("principal")
                                .realm("realm")
                                .tgtGenerationMethod(TGTGenerationMethod.Password)
                                .spec(TGTPasswordSpec.builder().password(SecretRefData.builder().build()).build())
                                .build())
                      .build())
            .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(WinRmConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getWinRmMetaData());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidationForWinRmKerberosKeyTab() {
    Secret secret = getWinRmSecret();
    secret.setSecretSpec(WinRmCredentialsSpec.builder()
                             .port(5986)
                             .auth(WinRmAuth.builder()
                                       .type(WinRmAuthScheme.Kerberos)
                                       .spec(KerberosWinRmConfig.builder()
                                                 .principal("principal")
                                                 .realm("realm")
                                                 .tgtGenerationMethod(TGTGenerationMethod.KeyTabFilePath)
                                                 .spec(TGTKeyTabFilePathSpec.builder().keyPath("/a/b/c").build())
                                                 .build())
                                       .build())
                             .build());
    doReturn(Optional.of(secret)).when(secretServiceV2Spy).get(any(), any(), any(), any());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(WinRmConfigValidationTaskResponse.builder().connectionSuccessful(true).build());
    SecretValidationResultDTO resultDTO =
        secretServiceV2Spy.validateSecret("account", null, null, "identifier", getWinRmMetaData());
    assertThat(resultDTO.isSuccess()).isEqualTo(true);
  }
}
