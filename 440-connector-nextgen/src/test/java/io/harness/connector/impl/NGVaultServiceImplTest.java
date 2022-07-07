/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.security.encryption.EncryptionType.VAULT;

import static software.wings.beans.TaskType.NG_VAULT_FETCHING_TASK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegatetasks.NGVaultFetchEngineTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalTaskParameters;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.encryptors.NGManagerEncryptorHelper;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.repositories.ConnectorRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;
import io.harness.secretmanagerclient.dto.VaultAwsIamRoleCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataRequestSpecDTO;
import io.harness.security.encryption.AccessType;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.service.impl.security.NGEncryptorService;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class NGVaultServiceImplTest extends CategoryTest {
  DelegateGrpcClientWrapper delegateService;
  NGConnectorSecretManagerService ngConnectorSecretManagerService;
  ConnectorRepository connectorRepository;
  AccountClient accountClient;
  NGEncryptedDataService ngEncryptedDataService;
  NGEncryptorService ngEncryptorService;
  SecretCrudService secretCrudService;
  NGManagerEncryptorHelper ngManagerEncryptorHelper;

  private NGVaultServiceImpl ngVaultService;

  private final String ACCOUNT_IDENTIFIER = "ACCOUNT_ID";
  private final String KMS_IDENTIFIER = "KMS_ID";
  public static final String HTTP_VAULT_URL = "http://vault.com";

  @Before
  public void doSetup() {
    delegateService = mock(DelegateGrpcClientWrapper.class);
    ngConnectorSecretManagerService = mock(NGConnectorSecretManagerService.class);
    connectorRepository = mock(ConnectorRepository.class);
    accountClient = mock(AccountClient.class);
    ngEncryptedDataService = mock(NGEncryptedDataService.class);
    ngEncryptorService = mock(NGEncryptorService.class);
    secretCrudService = mock(SecretCrudService.class);
    ngManagerEncryptorHelper = mock(NGManagerEncryptorHelper.class);
    ngVaultService = new NGVaultServiceImpl(delegateService, ngConnectorSecretManagerService, connectorRepository,
        accountClient, ngEncryptedDataService, ngEncryptorService, secretCrudService, ngManagerEncryptorHelper);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_ListSecretEngines_ForExistingVaultWithToken_nowUpdatedWithAWSIAM() throws IOException {
    // existing vault config which is Token based.
    SecretManagerConfigDTO vaultConfigDTO = getVaultConfigDTOWithAuthToken();
    vaultConfigDTO.setEncryptionType(VAULT);

    // now update the existing vault from Auth Token based to AWS IAM
    String awsRegion = randomAlphabetic(10);
    String vaultAwsIamRole = randomAlphabetic(10);
    SecretRefData secretRefData = SecretRefData.builder().decryptedValue(randomAlphabetic(10).toCharArray()).build();
    SecretManagerMetadataRequestDTO requestDTO = SecretManagerMetadataRequestDTO.builder().build();
    requestDTO.setIdentifier(randomAlphabetic(10));
    requestDTO.setEncryptionType(VAULT);
    requestDTO.setSpec(VaultMetadataRequestSpecDTO.builder()
                           .url(HTTP_VAULT_URL)
                           .accessType(AccessType.AWS_IAM)
                           .spec(VaultAwsIamRoleCredentialDTO.builder()
                                     .awsRegion(awsRegion)
                                     .vaultAwsIamRole(vaultAwsIamRole)
                                     .xVaultAwsIamServerId(secretRefData)
                                     .build())
                           .build());
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(LocalConfigDTO.builder().build());
    when(ngEncryptedDataService.get(any(), any(), any(), any())).thenReturn(NGEncryptedData.builder().build());
    when(ngEncryptorService.fetchSecretValue(any(), any(), any())).thenReturn(randomAlphabetic(10).toCharArray());

    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    when(delegateService.executeSyncTask(any()))
        .thenReturn(NGVaultFetchEngineTaskResponse.builder().secretEngineSummaryList(new ArrayList<>()).build());
    Call<RestResponse<Boolean>> request = mock(Call.class);
    doReturn(request).when(accountClient).isFeatureFlagEnabled(any(), any());
    RestResponse<Boolean> mockResponse = new RestResponse<>(false);
    doReturn(Response.success(mockResponse)).when(request).execute();
    SecretManagerMetadataDTO metadataDTO = ngVaultService.getListOfEngines(ACCOUNT_IDENTIFIER, requestDTO);
    ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateService, times(1)).executeSyncTask(taskRequestArgumentCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = taskRequestArgumentCaptor.getValue();
    assertEquals(NG_VAULT_FETCHING_TASK.toString(), delegateTaskRequest.getTaskType());
    assertNotNull(delegateTaskRequest.getTaskParameters());
    NGVaultRenewalTaskParameters taskParameters =
        (NGVaultRenewalTaskParameters) delegateTaskRequest.getTaskParameters();
    assertEquals(HTTP_VAULT_URL, taskParameters.getEncryptionConfig().getVaultUrl());
    assertEquals(KMS_IDENTIFIER, taskParameters.getEncryptionConfig().getIdentifier());
    assertTrue(taskParameters.getEncryptionConfig().isUseAwsIam());
    assertEquals(awsRegion, taskParameters.getEncryptionConfig().getAwsRegion());
    assertThat(secretRefData.getDecryptedValue())
        .isEqualTo(taskParameters.getEncryptionConfig().getXVaultAwsIamServerId().toCharArray());
    assertEquals(vaultAwsIamRole, taskParameters.getEncryptionConfig().getVaultAwsIamRole());
  }

  private SecretManagerConfigDTO getVaultConfigDTOWithAuthToken() {
    String authToken = "authToken";
    String secretEngineName = "secretEngine";
    VaultConfigDTO vaultConfigDTO = VaultConfigDTO.builder().build();
    vaultConfigDTO.setIdentifier(KMS_IDENTIFIER);
    vaultConfigDTO.setVaultUrl(HTTP_VAULT_URL);
    vaultConfigDTO.setAuthToken(authToken);
    vaultConfigDTO.setSecretEngineName(secretEngineName);
    vaultConfigDTO.setUseVaultAgent(false);
    vaultConfigDTO.setUseK8sAuth(false);
    vaultConfigDTO.setUseAwsIam(false);
    return vaultConfigDTO;
  }
}
