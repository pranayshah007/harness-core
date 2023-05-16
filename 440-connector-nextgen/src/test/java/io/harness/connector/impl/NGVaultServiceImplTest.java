/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.RICHA;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.VAULT;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_OWNER_CONSTANT;

import static software.wings.beans.TaskType.NG_VAULT_FETCHING_TASK;
import static software.wings.beans.TaskType.NG_VAULT_TOKEN_LOOKUP;

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
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.connector.mappers.secretmanagermapper.VaultDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.VaultEntityToDTO;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.delegatetasks.NGVaultFetchEngineTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalAppRoleTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalTaskParameters;
import io.harness.delegatetasks.NGVaultRenewalTaskResponse;
import io.harness.delegatetasks.NGVaultTokenLookupTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.GeneralException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.encryptors.NGManagerEncryptorHelper;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.repositories.ConnectorRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;
import io.harness.secretmanagerclient.dto.VaultAuthTokenCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultAwsIamRoleCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataRequestSpecDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultMetadataRequestSpecDTO;
import io.harness.security.encryption.AccessType;
import io.harness.security.encryption.EncryptionType;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.BaseVaultConfig;
import software.wings.helpers.ext.vault.VaultTokenLookupResult;
import software.wings.service.impl.security.NGEncryptorService;
import software.wings.settings.SettingVariableTypes;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class NGVaultServiceImplTest extends CategoryTest {
  @InjectMocks VaultEntityToDTO vaultEntityToDTO;
  @InjectMocks VaultDTOToEntity vaultDTOToEntity;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

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
  private final String CONNECTOR_NAME = "VAULT_CONNECTOR";
  private final String CONNECTOR_ID = "VAULT_CONNECTOR_ID";
  private final String ORG_IDENTIFIER = "HARNESS";
  private final String PROJECT_IDENTIFIER = "SECRET_MANAGEMENT";
  private final String KMS_IDENTIFIER = "KMS_ID";
  public static final String HTTP_VAULT_URL = "http://vault.com";
  private String encryptedValue = randomAlphabetic(10);

  @Before
  public void doSetup() {
    MockitoAnnotations.initMocks(this);

    delegateService = mock(DelegateGrpcClientWrapper.class);
    ngConnectorSecretManagerService = mock(NGConnectorSecretManagerService.class);
    connectorRepository = mock(ConnectorRepository.class);
    accountClient = mock(AccountClient.class);
    ngEncryptedDataService = mock(NGEncryptedDataService.class);
    ngEncryptorService = mock(NGEncryptorService.class);
    secretCrudService = mock(SecretCrudService.class);
    ngManagerEncryptorHelper = new NGManagerEncryptorHelper(delegateService, new TaskSetupAbstractionHelper());
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
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(NGVaultFetchEngineTaskResponse.builder().secretEngineSummaryList(new ArrayList<>()).build());
    Call<RestResponse<Boolean>> request = mock(Call.class);
    doReturn(request).when(accountClient).isFeatureFlagEnabled(any(), any());
    RestResponse<Boolean> mockResponse = new RestResponse<>(false);
    doReturn(Response.success(mockResponse)).when(request).execute();
    SecretManagerMetadataDTO metadataDTO = ngVaultService.getListOfEngines(ACCOUNT_IDENTIFIER, requestDTO);
    ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateService, times(1)).executeSyncTaskV2(taskRequestArgumentCaptor.capture());
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

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void test_ListSecretEngines_azureKeyVault_oldDelegate() {
    String secretManagerIdentifier = randomAlphabetic(10);
    AzureKeyVaultMetadataRequestSpecDTO specDTO = new AzureKeyVaultMetadataRequestSpecDTO();
    specDTO.setUseManagedIdentity(true);
    specDTO.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
    specDTO.setAzureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY);
    SecretManagerMetadataRequestDTO requestDTO = SecretManagerMetadataRequestDTO.builder()
                                                     .encryptionType(AZURE_VAULT)
                                                     .identifier(secretManagerIdentifier)
                                                     .spec(specDTO)
                                                     .build();
    when(ngConnectorSecretManagerService.getUsingIdentifier(ACCOUNT_IDENTIFIER, null, null, "authtoken", false))
        .thenReturn(LocalConfigDTO.builder().build());
    when(ngEncryptedDataService.get(any(), any(), any(), any()))
        .thenReturn(NGEncryptedData.builder().encryptionType(AZURE_VAULT).build());
    GeneralException generalException = new GeneralException("Null Pointer Exception");
    WingsException wingsException = mock(WingsException.class);
    when(wingsException.getCause()).thenReturn(generalException);
    when(delegateService.executeSyncTaskV2(any())).thenThrow(wingsException);
    exceptionRule.expect(WingsException.class);
    exceptionRule.expectMessage("Listing secret engines failed. Please check if delegate version is 791xx or later.");
    ngVaultService.getListOfEngines(ACCOUNT_IDENTIFIER, requestDTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void test_ListSecretEngines_withANonExistingSecret_authToken() {
    String secretManagerIdentifier = randomAlphabetic(10);
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("authtoken").decryptedValue(randomAlphabetic(10).toCharArray()).build();
    SecretManagerMetadataRequestDTO requestDTO =
        SecretManagerMetadataRequestDTO.builder()
            .encryptionType(VAULT)
            .identifier(secretManagerIdentifier)
            .spec(VaultMetadataRequestSpecDTO.builder()
                      .url(HTTP_VAULT_URL)
                      .accessType(AccessType.TOKEN)
                      .spec(VaultAuthTokenCredentialDTO.builder().authToken(secretRefData).build())
                      .build())
            .build();
    when(ngConnectorSecretManagerService.getUsingIdentifier(ACCOUNT_IDENTIFIER, null, null, "authtoken", false))
        .thenReturn(LocalConfigDTO.builder().build());
    when(ngEncryptedDataService.get(any(), any(), any(), any())).thenReturn(null);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage("Secret [authtoken] not found or has been deleted.");
    ngVaultService.getListOfEngines(ACCOUNT_IDENTIFIER, requestDTO);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void test_decryptSecretRefData_withSecretEncryptionTypeLocal() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("authtoken").decryptedValue(encryptedValue.toCharArray()).build();

    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                                           .orgIdentifier(null)
                                           .projectIdentifier(null)
                                           .encryptionType(EncryptionType.LOCAL)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                           .build();
    when(ngEncryptedDataService.get(ACCOUNT_IDENTIFIER, null, null, secretRefData.getIdentifier()))
        .thenReturn(encryptedDataDTO);
    when(ngEncryptorService.fetchSecretValue(any(), any(), any())).thenReturn(randomAlphabetic(10).toCharArray());

    ngVaultService.decryptSecretRefData(ACCOUNT_IDENTIFIER, null, null, secretRefData);

    verify(ngConnectorSecretManagerService, times(1)).getLocalConfigDTO(ACCOUNT_IDENTIFIER);
    verify(ngConnectorSecretManagerService, times(0))
        .getUsingIdentifier(ACCOUNT_IDENTIFIER, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void test_decryptSecretRefData_withSecretEncryptionTypeOtherThanLocal() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("authtoken").decryptedValue(encryptedValue.toCharArray()).build();

    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                                           .orgIdentifier(null)
                                           .projectIdentifier(null)
                                           .encryptionType(EncryptionType.GCP_KMS)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                           .build();
    when(ngEncryptedDataService.get(ACCOUNT_IDENTIFIER, null, null, secretRefData.getIdentifier()))
        .thenReturn(encryptedDataDTO);
    when(ngEncryptorService.fetchSecretValue(any(), any(), any())).thenReturn(randomAlphabetic(10).toCharArray());

    ngVaultService.decryptSecretRefData(ACCOUNT_IDENTIFIER, null, null, secretRefData);

    verify(ngConnectorSecretManagerService, times(0)).getLocalConfigDTO(ACCOUNT_IDENTIFIER);
    verify(ngConnectorSecretManagerService, times(1))
        .getUsingIdentifier(ACCOUNT_IDENTIFIER, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processTokenLookup_withTaskNotFoundInDelegate_shouldNotCallTokenLookup() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(
        VaultConnector.builder().accessType(AccessType.TOKEN).renewalIntervalMinutes(10L).build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.isTaskTypeSupported(any(), any())).thenReturn(false);

    // Act.
    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);

    // Assert.
    verify(delegateService, times(0)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processTokenLookup_nonVaultTypeConnector_doesNotExecuteAnyDelegateTask() throws IOException {
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .connectorType(ConnectorType.AWS)
                                                         .build())
                                      .build();
    setUpCommonMocks();

    // Act.
    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);

    // Assert.
    verify(delegateService, times(0)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processTokenLookup_vaultConnectorWithoutTokenBasedAuth_doesNotExecuteAnyDelegateTask()
      throws IOException {
    VaultConnectorDTO vaultConnectorDTO =
        vaultEntityToDTO.createConnectorDTO(VaultConnector.builder().accessType(AccessType.APP_ROLE).build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();

    // Act.
    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);

    // Assert.
    verify(delegateService, times(0)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processTokenLookup_vaultConnectorWithTokenBasedAuth_doesExecuteDelegateTask() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(
        VaultConnector.builder().accessType(AccessType.TOKEN).renewalIntervalMinutes(10L).build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(NGVaultTokenLookupTaskResponse.builder()
                        .vaultTokenLookupResult(VaultTokenLookupResult.builder()
                                                    .expiryTime(randomAlphabetic(10))
                                                    .name(randomAlphabetic(10))
                                                    .renewable(true)
                                                    .build())
                        .delegateMetaInfo(DelegateMetaInfo.builder().hostName("hostName").id("id").build())
                        .build());
    when(delegateService.isTaskTypeSupported(any(), any())).thenReturn(true);

    // Act.
    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);

    // Assert.
    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateService, times(1)).executeSyncTaskV2(argumentCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertEquals(NG_VAULT_TOKEN_LOOKUP.toString(), delegateTaskRequest.getTaskType());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processTokenLookup_vaultConnectorWithTokenBasedAuth_doesThrowExceptionInCaseOfRootToken()
      throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(
        VaultConnector.builder().accessType(AccessType.TOKEN).renewalIntervalMinutes(10L).build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(NGVaultTokenLookupTaskResponse.builder()
                        .vaultTokenLookupResult(VaultTokenLookupResult.builder()
                                                    .expiryTime(null)
                                                    .name(randomAlphabetic(10))
                                                    .renewable(true)
                                                    .build())
                        .delegateMetaInfo(DelegateMetaInfo.builder().hostName("hostName").id("id").build())
                        .build());
    when(delegateService.isTaskTypeSupported(any(), any())).thenReturn(true);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(
        "The token used is a root token. Please set renewal interval as zero if you are using root token.");

    // Act.
    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);

    // Assert.
    verify(delegateService, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void processTokenLookup_vaultConnectorWithTokenBasedAuth_nonExistentSecret() {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(VaultConnector.builder()
                                                                                  .accessType(AccessType.TOKEN)
                                                                                  .authTokenRef("authToken")
                                                                                  .renewalIntervalMinutes(10L)
                                                                                  .build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    when(delegateService.isTaskTypeSupported(any(), any())).thenReturn(true);
    when(ngEncryptedDataService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "authToken"))
        .thenReturn(null);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage("Secret [authToken] not found or has been deleted.");

    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void
  processTokenLookup_vaultConnectorWithTokenBasedAuth_doesThrowExceptionInCaseOfNonRootToken_whichIsNonRenewable()
      throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(
        VaultConnector.builder().accessType(AccessType.TOKEN).renewalIntervalMinutes(10L).build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(NGVaultTokenLookupTaskResponse.builder()
                        .vaultTokenLookupResult(VaultTokenLookupResult.builder()
                                                    .expiryTime(randomAlphabetic(10))
                                                    .name(randomAlphabetic(10))
                                                    .renewable(false)
                                                    .build())
                        .delegateMetaInfo(DelegateMetaInfo.builder().hostName("hostName").id("id").build())
                        .build());
    when(delegateService.isTaskTypeSupported(any(), any())).thenReturn(true);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(
        "The token used is a non-renewable token. Please set renewal interval as zero or use a renewable token.");
    // Act.
    ngVaultService.processTokenLookup(inputConnector, ACCOUNT_IDENTIFIER);

    // Assert.
    verify(delegateService, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.GAURAV_NANDA)
  @Category(UnitTests.class)
  public void processAppRole_nonVaultTypeConnector_doesNotExecuteAnyDelegateTask() throws IOException {
    // Arrange.
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .connectorType(ConnectorType.AWS)
                                                         .build())
                                      .build();
    setUpCommonMocks();

    // Act.
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, false);

    // Assert.
    verify(delegateService, times(0)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.GAURAV_NANDA)
  @Category(UnitTests.class)
  public void processAppRole_vaultConnectorWithoutAppRoleAuth_doesNotExecuteAnyDelegateTask() throws IOException {
    // Arrange.
    VaultConnectorDTO vaultConnectorDTO =
        vaultEntityToDTO.createConnectorDTO(VaultConnector.builder().accessType(AccessType.AWS_IAM).build());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();

    // Act.
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, false);

    // Assert.
    verify(delegateService, times(0)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.GAURAV_NANDA)
  @Category(UnitTests.class)
  public void processAppRole_orgLevelConnector_createsDelegateTaskHasOwnerSet() throws IOException {
    // Arrange.
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildAppRoleVaultConnector());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();

    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(NGVaultRenewalAppRoleTaskResponse.builder()
                        .vaultAppRoleLoginResult(VaultAppRoleLoginResult.builder().clientToken("clientToken").build())
                        .build());

    // Act.
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, false);

    // Assert.
    ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateService, times(1)).executeSyncTaskV2(taskRequestArgumentCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = taskRequestArgumentCaptor.getValue();

    assertEquals(delegateTaskRequest.getTaskSetupAbstractions().get(NG_DELEGATE_OWNER_CONSTANT),
        ORG_IDENTIFIER + "/" + PROJECT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processAppRole_shouldCreateAuthTokenRef_whenDoNotRenewAppRoleTokenFF_disabled() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildAppRoleVaultConnector());
    vaultConnectorDTO.setRenewAppRoleToken(true);
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(
            NGVaultRenewalAppRoleTaskResponse.builder()
                .vaultAppRoleLoginResult(VaultAppRoleLoginResult.builder().clientToken(randomAlphabetic(10)).build())
                .build());
    ArgumentCaptor<SecretDTOV2> argumentCaptor = ArgumentCaptor.forClass(SecretDTOV2.class);
    when(secretCrudService.create(any(), argumentCaptor.capture())).thenReturn(SecretResponseWrapper.builder().build());
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, true);
    verify(secretCrudService, times(1)).create(any(), any());
    SecretDTOV2 secretDTOV2 = argumentCaptor.getValue();
    assertNotNull(secretDTOV2);
    assertThat(secretDTOV2.getIdentifier()).isEqualTo(CONNECTOR_ID + "_" + VaultConnectorKeys.authTokenRef);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void processAppRole_withNonExistentAppRoleSecret() {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildAppRoleVaultConnector());
    vaultConnectorDTO.setRenewAppRoleToken(true);
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    when(ngEncryptedDataService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "test")).thenReturn(null);
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage("Secret [test] not found or has been deleted.");
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, true);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void processAppRole_shouldNotCreateAuthTokenRef_whenDoNotRenewAppRoleTokenFF_enabled() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildAppRoleVaultConnector());
    vaultConnectorDTO.setRenewAppRoleToken(false);
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(
            NGVaultRenewalAppRoleTaskResponse.builder()
                .vaultAppRoleLoginResult(VaultAppRoleLoginResult.builder().clientToken(randomAlphabetic(10)).build())
                .build());
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, true);
    verify(secretCrudService, times(0)).create(any(), any());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testProcessAppRole_VaultConfigHasRequiredLoginParams() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildAppRoleVaultConnector());
    ConnectorDTO inputConnector = ConnectorDTO.builder()
                                      .connectorInfo(ConnectorInfoDTO.builder()
                                                         .name(CONNECTOR_NAME)
                                                         .identifier(CONNECTOR_ID)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .connectorType(ConnectorType.VAULT)
                                                         .connectorConfig(vaultConnectorDTO)
                                                         .build())
                                      .build();
    setUpCommonMocks();
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(
            NGVaultRenewalAppRoleTaskResponse.builder()
                .vaultAppRoleLoginResult(VaultAppRoleLoginResult.builder().clientToken(randomAlphabetic(10)).build())
                .build());
    ngVaultService.processAppRole(inputConnector, null, ACCOUNT_IDENTIFIER, false);
    ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateService, times(1)).executeSyncTaskV2(taskRequestArgumentCaptor.capture());
    BaseVaultConfig capturedConfig =
        ((NGVaultRenewalTaskParameters) taskRequestArgumentCaptor.getValue().getTaskParameters()).getEncryptionConfig();
    assertThat(capturedConfig.getNamespace()).isEqualTo(vaultConnectorDTO.getNamespace());
    assertThat(capturedConfig.getAppRoleId()).isEqualTo(vaultConnectorDTO.getAppRoleId());
    assertThat(capturedConfig.getSecretId())
        .isEqualTo(String.valueOf(vaultConnectorDTO.getSecretId().getDecryptedValue()));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenewAppRoleClientToken_willUpdateCorrespondingPPT() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildAppRoleVaultConnector());
    vaultConnectorDTO.setRenewAppRoleToken(true);
    VaultConnector vaultConnector = vaultDTOToEntity.toConnectorEntity(vaultConnectorDTO);
    VaultConfigDTO vaultConfigDTO = (VaultConfigDTO) getVaultConfigDTOWithAppRoleAuth();
    vaultConfigDTO.setEncryptionType(VAULT);
    Call<RestResponse<Boolean>> request = mock(Call.class);
    doReturn(request).when(accountClient).isFeatureFlagEnabled(any(), any());
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(
            NGVaultRenewalAppRoleTaskResponse.builder()
                .vaultAppRoleLoginResult(VaultAppRoleLoginResult.builder().clientToken(randomAlphabetic(10)).build())
                .build());
    when(ngEncryptedDataService.updateSecretText(any(), any())).thenReturn(NGEncryptedData.builder().build());
    when(connectorRepository.save(vaultConnector, ChangeType.NONE)).thenReturn(vaultConnector);
    ngVaultService.renewAppRoleClientToken(vaultConnector);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(ngConnectorSecretManagerService, times(1)).getPerpetualTaskId(any(), any(), any(), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(vaultConnector.getIdentifier());
    verify(ngConnectorSecretManagerService, times(1)).resetHeartBeatTask(any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenewVaultToken_willUpdateCorrespondingPPT() throws IOException {
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(buildTokenBasedConnector());
    vaultConnectorDTO.setRenewAppRoleToken(true);
    VaultConnector vaultConnector = vaultDTOToEntity.toConnectorEntity(vaultConnectorDTO);
    VaultConfigDTO vaultConfigDTO = (VaultConfigDTO) getVaultConfigDTOWithAuthToken();
    vaultConfigDTO.setEncryptionType(VAULT);
    Call<RestResponse<Boolean>> request = mock(Call.class);
    doReturn(request).when(accountClient).isFeatureFlagEnabled(any(), any());
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    when(delegateService.executeSyncTaskV2(any()))
        .thenReturn(NGVaultRenewalTaskResponse.builder()
                        .isSuccessful(true)
                        .delegateMetaInfo(DelegateMetaInfo.builder().hostName("hostName").id("id").build())
                        .build());
    when(ngEncryptedDataService.updateSecretText(any(), any())).thenReturn(NGEncryptedData.builder().build());
    when(connectorRepository.save(vaultConnector, ChangeType.NONE)).thenReturn(vaultConnector);
    ngVaultService.renewToken(vaultConnector);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(ngConnectorSecretManagerService, times(1)).getPerpetualTaskId(any(), any(), any(), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(vaultConnector.getIdentifier());
    verify(ngConnectorSecretManagerService, times(1)).resetHeartBeatTask(any(), any());
  }

  private VaultConnector buildAppRoleVaultConnector() {
    return VaultConnector.builder()
        .accessType(AccessType.APP_ROLE)
        .vaultUrl(HTTP_VAULT_URL)
        .appRoleId("test-role-id")
        .secretIdRef("test")
        .namespace(randomAlphabetic(10))
        .build();
  }

  private VaultConnector buildTokenBasedConnector() {
    return VaultConnector.builder()
        .accessType(AccessType.TOKEN)
        .vaultUrl(HTTP_VAULT_URL)
        .authTokenRef("tokenRef")
        .namespace(randomAlphabetic(10))
        .build();
  }

  private SecretManagerConfigDTO getVaultConfigDTOWithAuthToken() {
    String authToken = "authToken";
    String secretEngineName = "secretEngine";
    VaultConfigDTO vaultConfigDTO = VaultConfigDTO.builder().build();
    vaultConfigDTO.setIdentifier(KMS_IDENTIFIER);
    vaultConfigDTO.setVaultUrl(HTTP_VAULT_URL);
    vaultConfigDTO.setName(CONNECTOR_NAME);
    vaultConfigDTO.setAuthToken(authToken);
    vaultConfigDTO.setSecretEngineName(secretEngineName);
    vaultConfigDTO.setUseVaultAgent(false);
    vaultConfigDTO.setUseK8sAuth(false);
    vaultConfigDTO.setUseAwsIam(false);
    return vaultConfigDTO;
  }

  private SecretManagerConfigDTO getVaultConfigDTOWithAppRoleAuth() {
    String secretEngineName = "secretEngine";
    VaultConfigDTO vaultConfigDTO = VaultConfigDTO.builder().build();
    vaultConfigDTO.setIdentifier(KMS_IDENTIFIER);
    vaultConfigDTO.setName(CONNECTOR_NAME);
    vaultConfigDTO.setVaultUrl(HTTP_VAULT_URL);
    vaultConfigDTO.setAppRoleId("test-role-id");
    vaultConfigDTO.setSecretEngineName(secretEngineName);
    vaultConfigDTO.setUseVaultAgent(false);
    vaultConfigDTO.setUseK8sAuth(false);
    vaultConfigDTO.setUseAwsIam(false);
    return vaultConfigDTO;
  }

  private void setUpCommonMocks() throws IOException {
    when(ngEncryptedDataService.get(any(), any(), any(), any()))
        .thenReturn(NGEncryptedData.builder().secretManagerIdentifier(randomAlphabetic(10)).build());
    when(ngEncryptorService.fetchSecretValue(any(), any(), any())).thenReturn(randomAlphabetic(10).toCharArray());

    Call<RestResponse<Boolean>> request = mock(Call.class);
    doReturn(request).when(accountClient).isFeatureFlagEnabled(any(), any());
    RestResponse<Boolean> mockResponse = new RestResponse<>(false);
    doReturn(Response.success(mockResponse)).when(request).execute();
  }
}
