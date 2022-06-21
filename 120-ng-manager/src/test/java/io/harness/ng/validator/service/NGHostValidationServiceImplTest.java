/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.validator.service;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.pdcconnector.HostConnectivityTaskParams;
import io.harness.delegate.beans.connector.pdcconnector.HostConnectivityTaskResponse;
import io.harness.delegate.beans.secrets.SSHConfigValidationTaskResponse;
import io.harness.delegate.beans.secrets.WinRmConfigValidationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.eraro.ErrorCode;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.SecretSpec;
import io.harness.ng.validator.dto.HostValidationDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class NGHostValidationServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String SECRET_IDENTIFIER = "secretIdentifier";
  private static final String OWNER = "owner";
  private static final String HOST = "host";
  private static final String VALIDATION_HOST_FAILED_ERROR_MSG = "SSH Validation host failed";
  private static final String INVALID_HOST = ":22";
  private static final String HOST_NULL = null;
  private static final String SECRET_IDENTIFIER_NULL = null;
  public static final String DELEGATE_SELECTOR = "delegate_selector_group";

  @Mock private NGSecretServiceV2 ngSecretServiceV2;
  @Mock private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Mock private WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper;
  @Mock private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NGErrorHelper ngErrorHelper;
  @InjectMocks NGHostValidationServiceImpl hostValidationService;

  @Test
  @Owner(developers = {VLAD, IVAN})
  @Category(UnitTests.class)
  public void shouldValidateSshHost() {
    mockSecret(SecretType.SSHKey);
    mockEncryptionDetails();
    mockTaskAbstractions();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildSSHConfigValidationTaskResponseSuccess());

    HostValidationDTO result = hostValidationService.validateHost(
        HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet());

    assertThat(result.getHost()).isEqualTo(HOST);
    assertThat(result.getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.SUCCESS);
    assertThat(result.getError()).isEqualTo(ErrorDetail.builder().build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostWithFailedTaskResponse() {
    mockSecret(SecretType.SSHKey);
    mockEncryptionDetails();
    mockTaskAbstractions();

    when(ngErrorHelper.getReason(VALIDATION_HOST_FAILED_ERROR_MSG)).thenReturn(VALIDATION_HOST_FAILED_ERROR_MSG);
    when(ngErrorHelper.getCode(VALIDATION_HOST_FAILED_ERROR_MSG)).thenReturn(450);
    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildSSHConfigValidationTaskResponseFailed());

    HostValidationDTO result = hostValidationService.validateHost(
        HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet());

    assertThat(result.getHost()).isEqualTo(HOST);
    assertThat(result.getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.FAILED);
    assertThat(result.getError())
        .isEqualTo(ErrorDetail.builder()
                       .reason("SSH Validation host failed")
                       .message(VALIDATION_HOST_FAILED_ERROR_MSG)
                       .code(450)
                       .build());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldValidateSshHostViaWinRm() {
    mockWinRmSecret(SecretType.WinRmCredentials);
    mockWinRmEncryptionDetails();
    mockTaskAbstractions();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildWinRmConfigValidationTaskResponseSuccess());

    HostValidationDTO result = hostValidationService.validateHost(
        HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet());

    assertThat(result.getHost()).isEqualTo(HOST);
    assertThat(result.getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.SUCCESS);
    assertThat(result.getError()).isEqualTo(ErrorDetail.builder().build());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidateSshHostViaWinRmWithFailedTaskResponse() {
    mockWinRmSecret(SecretType.WinRmCredentials);
    mockWinRmEncryptionDetails();
    mockTaskAbstractions();

    when(ngErrorHelper.getReason(VALIDATION_HOST_FAILED_ERROR_MSG)).thenReturn(VALIDATION_HOST_FAILED_ERROR_MSG);
    when(ngErrorHelper.getCode(VALIDATION_HOST_FAILED_ERROR_MSG)).thenReturn(450);
    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildWinRmConfigValidationTaskResponseFailed());

    HostValidationDTO result = hostValidationService.validateHost(
        HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet());

    assertThat(result.getHost()).isEqualTo(HOST);
    assertThat(result.getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.FAILED);
    assertThat(result.getError())
        .isEqualTo(ErrorDetail.builder()
                       .reason("SSH Validation host failed")
                       .message(VALIDATION_HOST_FAILED_ERROR_MSG)
                       .code(450)
                       .build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostMissingSecret() {
    assertThatThrownBy(()
                           -> hostValidationService.validateHost(HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                               PROJECT_IDENTIFIER, SECRET_IDENTIFIER_NULL, Collections.emptySet()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Secret identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostMissingHost() {
    assertThatThrownBy(()
                           -> hostValidationService.validateHost(HOST_NULL, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                               PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Host cannot be null or empty");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostMissingSecretInDb() {
    when(ngSecretServiceV2.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(()
                           -> hostValidationService.validateHost(HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                               PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(String.format("Not found secret for host validation, secret identifier: %s", SECRET_IDENTIFIER));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostInvalidSecretType() {
    mockSecret(SecretType.SecretFile);

    assertThatThrownBy(()
                           -> hostValidationService.validateHost(HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                               PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(String.format("Invalid secret type, secret identifier: %s", SECRET_IDENTIFIER));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSSHHostInvalidHost() {
    mockSecret(SecretType.SSHKey);
    mockEncryptionDetails();

    assertThatThrownBy(()
                           -> hostValidationService.validateHost(INVALID_HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                               PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(String.format("Not found hostName, host: %s, extracted port: 22", INVALID_HOST));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostsWithEmptyHosts() {
    List<HostValidationDTO> hostValidationDTOs = hostValidationService.validateHosts(Collections.emptyList(),
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER, Collections.emptySet());

    assertThat(hostValidationDTOs).isEmpty();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostsMissingSecret() {
    assertThatThrownBy(()
                           -> hostValidationService.validateHosts(Collections.singletonList(HOST), ACCOUNT_IDENTIFIER,
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER_NULL, Collections.emptySet()))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Secret identifier cannot be null or empty");
  }

  @Test
  @Owner(developers = {IVAN})
  @Category(UnitTests.class)
  public void testValidateHostConnectivity() {
    mockTaskAbstractions();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildHostConnectivityTaskResponseSuccess());
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    HostValidationDTO result = hostValidationService.validateHostConnectivity(
        HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR));

    verify(delegateGrpcClientWrapper).executeSyncTask(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    HostConnectivityTaskParams hostConnectivityTaskParams =
        (HostConnectivityTaskParams) delegateTaskRequest.getTaskParameters();
    assertThat(hostConnectivityTaskParams.getHostName()).isEqualTo(HOST);
    assertThat(hostConnectivityTaskParams.getDelegateSelectors()).isEqualTo(Sets.newHashSet(DELEGATE_SELECTOR));
    assertThat(hostConnectivityTaskParams.getPort()).isEqualTo(22);

    assertThat(result.getHost()).isEqualTo(HOST);
    assertThat(result.getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.SUCCESS);
    assertThat(result.getError()).isEqualTo(ErrorDetail.builder().build());
  }

  @Test
  @Owner(developers = {IVAN})
  @Category(UnitTests.class)
  public void testValidateHostConnectivityWithFailedResponse() {
    mockTaskAbstractions();

    when(ngErrorHelper.getReason(VALIDATION_HOST_FAILED_ERROR_MSG)).thenReturn(VALIDATION_HOST_FAILED_ERROR_MSG);
    when(ngErrorHelper.getCode(VALIDATION_HOST_FAILED_ERROR_MSG)).thenReturn(450);
    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildHostConnectivityTaskResponseFailed());

    HostValidationDTO result = hostValidationService.validateHostConnectivity(
        HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR));

    assertThat(result.getHost()).isEqualTo(HOST);
    assertThat(result.getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.FAILED);
    assertThat(result.getError())
        .isEqualTo(ErrorDetail.builder()
                       .reason("SSH Validation host failed")
                       .message(VALIDATION_HOST_FAILED_ERROR_MSG)
                       .code(450)
                       .build());
  }

  @Test
  @Owner(developers = {IVAN})
  @Category(UnitTests.class)
  public void testValidateHostConnectivityWithErrorNotifyResponseData() {
    mockTaskAbstractions();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildErrorNotifyResponseData());

    assertThatThrownBy(()
                           -> hostValidationService.validateHostConnectivity(HOST, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
                               PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR)))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Please make sure that your delegates are connected. Refer https://docs.harness.io/article/migeq3achl-harness-delegate-faqs#delegate_installation for more information on delegate Installation");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateHostConnectivityWithMissingHost() {
    assertThatThrownBy(()
                           -> hostValidationService.validateHostConnectivity(HOST_NULL, ACCOUNT_IDENTIFIER,
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR)))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Host cannot be null or empty");
  }

  private void mockSecret(SecretType secretType) {
    Secret secret = mock(Secret.class);
    SecretSpec secretKeySpec = mock(SecretSpec.class);
    SSHKeySpecDTO secretSpecDTO = mock(SSHKeySpecDTO.class);

    when(secret.getType()).thenReturn(secretType);
    when(secret.getSecretSpec()).thenReturn(secretKeySpec);
    when(secretKeySpec.toDTO()).thenReturn(secretSpecDTO);

    when(ngSecretServiceV2.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER))
        .thenReturn(Optional.of(secret));
  }

  private void mockWinRmSecret(SecretType secretType) {
    Secret secret = mock(Secret.class);
    SecretSpec secretKeySpec = mock(SecretSpec.class);
    WinRmCredentialsSpecDTO secretSpecDTO = mock(WinRmCredentialsSpecDTO.class);

    when(secret.getType()).thenReturn(secretType);
    when(secret.getSecretSpec()).thenReturn(secretKeySpec);
    when(secretKeySpec.toDTO()).thenReturn(secretSpecDTO);

    when(ngSecretServiceV2.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER))
        .thenReturn(Optional.of(secret));
  }

  private void mockEncryptionDetails() {
    when(sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(any(), any())).thenReturn(getEncryptionDetails());
  }

  private void mockWinRmEncryptionDetails() {
    when(winRmCredentialsSpecDTOHelper.getWinRmEncryptionDetails(any(), any())).thenReturn(getEncryptionDetails());
  }

  private void mockTaskAbstractions() {
    when(taskSetupAbstractionHelper.getOwner(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)).thenReturn(OWNER);
  }

  @NotNull
  private List<EncryptedDataDetail> getEncryptionDetails() {
    return Collections.singletonList(EncryptedDataDetail.builder().build());
  }

  private SSHConfigValidationTaskResponse buildSSHConfigValidationTaskResponseSuccess() {
    return SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build();
  }

  private WinRmConfigValidationTaskResponse buildWinRmConfigValidationTaskResponseSuccess() {
    return WinRmConfigValidationTaskResponse.builder().connectionSuccessful(true).build();
  }

  private SSHConfigValidationTaskResponse buildSSHConfigValidationTaskResponseFailed() {
    return SSHConfigValidationTaskResponse.builder()
        .connectionSuccessful(false)
        .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
        .errorMessage(VALIDATION_HOST_FAILED_ERROR_MSG)
        .build();
  }

  private WinRmConfigValidationTaskResponse buildWinRmConfigValidationTaskResponseFailed() {
    return WinRmConfigValidationTaskResponse.builder()
        .connectionSuccessful(false)
        .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
        .errorMessage(VALIDATION_HOST_FAILED_ERROR_MSG)
        .build();
  }

  private HostConnectivityTaskResponse buildHostConnectivityTaskResponseSuccess() {
    return HostConnectivityTaskResponse.builder().connectionSuccessful(true).build();
  }

  private HostConnectivityTaskResponse buildHostConnectivityTaskResponseFailed() {
    return HostConnectivityTaskResponse.builder()
        .connectionSuccessful(false)
        .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
        .errorMessage(VALIDATION_HOST_FAILED_ERROR_MSG)
        .build();
  }

  private ErrorNotifyResponseData buildErrorNotifyResponseData() {
    return ErrorNotifyResponseData.builder().errorMessage(VALIDATION_HOST_FAILED_ERROR_MSG).build();
  }
}
