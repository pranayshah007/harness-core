/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.DO_NOT_RENEW_APPROLE_TOKEN;
import static io.harness.beans.FeatureName.ENABLE_CERT_VALIDATION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INTERNAL_SERVER_ERROR;
import static io.harness.eraro.ErrorCode.INVALID_AZURE_VAULT_CONFIGURATION;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_NOT_FOUND;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.git.model.ChangeType.NONE;
import static io.harness.remote.client.CGRestUtils.getResponse;
import static io.harness.security.encryption.AccessType.APP_ROLE;
import static io.harness.security.encryption.AccessType.AWS_IAM;
import static io.harness.security.encryption.AccessType.TOKEN;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.EncryptionType.VAULT;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.TaskType.NG_AZURE_VAULT_FETCH_ENGINES;
import static software.wings.beans.TaskType.NG_VAULT_FETCHING_TASK;
import static software.wings.beans.TaskType.NG_VAULT_RENEW_APP_ROLE_TOKEN;
import static software.wings.beans.TaskType.NG_VAULT_RENEW_TOKEN;
import static software.wings.beans.TaskType.NG_VAULT_TOKEN_LOOKUP;

import static java.time.Duration.ofMillis;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.connector.services.NGVaultService;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.delegatetasks.NGAzureKeyVaultFetchEngineResponse;
import io.harness.delegatetasks.NGAzureKeyVaultFetchEngineTaskParameters;
import io.harness.delegatetasks.NGVaultFetchEngineTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalAppRoleTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalTaskParameters;
import io.harness.delegatetasks.NGVaultRenewalTaskResponse;
import io.harness.delegatetasks.NGVaultTokenLookupTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AzureServiceException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.encryptors.NGManagerEncryptorHelper;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;
import io.harness.secretmanagerclient.dto.VaultAgentCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultAppRoleCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultAuthTokenCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultAwsIamRoleCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultK8sCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataRequestSpecDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataSpecDTO;
import io.harness.secretmanagerclient.dto.VaultSecretEngineDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultMetadataRequestSpecDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultMetadataSpecDTO;
import io.harness.security.encryption.AccessType;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.AzureVaultConfig;
import software.wings.beans.BaseVaultConfig;
import software.wings.beans.TaskType;
import software.wings.beans.VaultConfig;
import software.wings.helpers.ext.vault.VaultTokenLookupResult;
import software.wings.service.impl.security.NGEncryptorService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
@Slf4j
public class NGVaultServiceImpl implements NGVaultService {
  private static final int NUM_OF_RETRIES = 3;
  public static final String UNKNOWN_RESPONSE = "Unknown Response from delegate";
  private final DelegateGrpcClientWrapper delegateService;
  private final NGConnectorSecretManagerService ngConnectorSecretManagerService;
  private final ConnectorRepository connectorRepository;
  private final AccountClient accountClient;
  private final NGEncryptedDataService encryptedDataService;
  private final NGEncryptorService ngEncryptorService;
  private final SecretCrudService secretCrudService;
  private final NGManagerEncryptorHelper ngManagerEncryptorHelper;

  @Inject
  public NGVaultServiceImpl(DelegateGrpcClientWrapper delegateService,
      NGConnectorSecretManagerService ngConnectorSecretManagerService, ConnectorRepository connectorRepository,
      AccountClient accountClient, NGEncryptedDataService encryptedDataService, NGEncryptorService ngEncryptorService,
      SecretCrudService secretCrudService, NGManagerEncryptorHelper ngManagerEncryptorHelper) {
    this.delegateService = delegateService;
    this.ngConnectorSecretManagerService = ngConnectorSecretManagerService;
    this.connectorRepository = connectorRepository;
    this.accountClient = accountClient;
    this.encryptedDataService = encryptedDataService;
    this.ngEncryptorService = ngEncryptorService;
    this.secretCrudService = secretCrudService;
    this.ngManagerEncryptorHelper = ngManagerEncryptorHelper;
  }

  @Override
  public void renewToken(VaultConnector vaultConnector) {
    log.info("NG Renew Token for : " + vaultConnector.getName());

    SecretManagerConfig secretManagerConfig = getSecretManagerConfig(vaultConnector.getAccountIdentifier(),
        vaultConnector.getOrgIdentifier(), vaultConnector.getProjectIdentifier(), vaultConnector.getIdentifier());
    BaseVaultConfig baseVaultConfig = (BaseVaultConfig) secretManagerConfig;

    setCertValidation(vaultConnector.getAccountIdentifier(), baseVaultConfig);
    NGVaultRenewalTaskParameters parameters =
        NGVaultRenewalTaskParameters.builder().encryptionConfig(baseVaultConfig).build();

    DelegateResponseData delegateResponseData =
        getDelegateResponseData(vaultConnector.getAccountIdentifier(), parameters, NG_VAULT_RENEW_TOKEN);

    if (!(delegateResponseData instanceof NGVaultRenewalTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, UNKNOWN_RESPONSE, USER);
    }

    NGVaultRenewalTaskResponse ngVaultRenewalTaskResponse = (NGVaultRenewalTaskResponse) delegateResponseData;
    log.info("Delegate response for renewToken: " + ngVaultRenewalTaskResponse.isSuccessful());

    if (ngVaultRenewalTaskResponse.isSuccessful()) {
      vaultConnector.setRenewedAt(System.currentTimeMillis());
      connectorRepository.save(vaultConnector, ChangeType.NONE);
      updatePerpetualTaskWhenTokenIsRenewed(vaultConnector);
    }
  }

  @Override
  public List<SecretEngineSummary> listSecretEngines(BaseVaultConfig baseVaultConfig) {
    List<SecretEngineSummary> secretEngineSummaries = new ArrayList<>();
    setCertValidation(baseVaultConfig.getAccountId(), baseVaultConfig);
    try {
      for (SecretEngineSummary secretEngineSummary : listSecretEnginesInternal(baseVaultConfig)) {
        if (secretEngineSummary.getType() != null && secretEngineSummary.getType().equals("kv")) {
          secretEngineSummaries.add(secretEngineSummary);
        }
      }
    } catch (DelegateServiceDriverException ex) {
      if (ex.getMessage() != null) {
        throw ex;
      } else {
        throw new WingsException(
            "Listing secret engines failed. Please check if active delegates are available in the account", ex);
      }
    } catch (WingsException wingsException) {
      log.error("Listing secret engines failed for account Id {}", baseVaultConfig.getAccountId());
      throw wingsException;
    } catch (Exception e) {
      log.error("Listing vault engines failed for account Id {}", baseVaultConfig.getAccountId(), e);
      throw new InvalidRequestException("Failed to list Vault engines", INVALID_CREDENTIAL, USER);
    }

    return secretEngineSummaries;
  }

  @Override
  public void renewAppRoleClientToken(VaultConnector vaultConnector) {
    if (CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(
            DO_NOT_RENEW_APPROLE_TOKEN.name(), vaultConnector.getAccountIdentifier()))) {
      vaultConnector.setRenewAppRoleToken(false);
      connectorRepository.save(vaultConnector, ChangeType.NONE);
      return;
    }
    SecretManagerConfig secretManagerConfig = getSecretManagerConfig(vaultConnector.getAccountIdentifier(),
        vaultConnector.getOrgIdentifier(), vaultConnector.getProjectIdentifier(), vaultConnector.getIdentifier());
    BaseVaultConfig baseVaultConfig = (BaseVaultConfig) secretManagerConfig;
    VaultAppRoleLoginResult vaultAppRoleLoginResult = appRoleLogin(baseVaultConfig);

    SecretRefData secretRef = SecretRefHelper.createSecretRef(vaultConnector.getAuthTokenRef());
    Scope scope = secretRef.getScope();
    String accountIdentifier = vaultConnector.getAccountIdentifier();
    String orgIdentifier = getOrgIdentifier(vaultConnector.getOrgIdentifier(), scope);
    String projectIdentifier = getProjectIdentifier(vaultConnector.getProjectIdentifier(), scope);

    SecretTextSpecDTO secretTextSpecDTO = SecretTextSpecDTO.builder()
                                              .value(String.valueOf(vaultAppRoleLoginResult.getClientToken()))
                                              .valueType(ValueType.Inline)
                                              .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                              .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .identifier(secretRef.getIdentifier())
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .spec(secretTextSpecDTO)
                                  .build();

    try {
      encryptedDataService.updateSecretText(accountIdentifier, secretDTOV2);
    } catch (Exception e) {
      String message = "NG: Failed to update token for AppRole based login for secret manager "
          + vaultConnector.getName() + " at " + vaultConnector.getVaultUrl();
      log.error(message, e);
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, USER);
    }
    vaultConnector.setRenewedAt(System.currentTimeMillis());
    connectorRepository.save(vaultConnector, ChangeType.NONE);
    updatePerpetualTaskWhenTokenIsRenewed(vaultConnector);
  }

  @Override
  public VaultTokenLookupResult tokenLookup(BaseVaultConfig vaultConfig) {
    String name = vaultConfig.getName();
    log.info("Token lookup for vault id {}", name);
    String accountIdentifier = vaultConfig.getAccountId();
    setCertValidation(accountIdentifier, vaultConfig);
    int failedAttempts = 0;
    while (true) {
      try {
        NGVaultRenewalTaskParameters parameters =
            NGVaultRenewalTaskParameters.builder().encryptionConfig(vaultConfig).build();

        DelegateResponseData delegateResponseData =
            getDelegateResponseData(accountIdentifier, parameters, NG_VAULT_TOKEN_LOOKUP);

        if (!(delegateResponseData instanceof NGVaultTokenLookupTaskResponse)) {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, UNKNOWN_RESPONSE, USER);
        }

        NGVaultTokenLookupTaskResponse ngVaultTokenLookupTaskResponse =
            (NGVaultTokenLookupTaskResponse) delegateResponseData;

        return ngVaultTokenLookupTaskResponse.getVaultTokenLookupResult();
      } catch (WingsException e) {
        failedAttempts++;
        log.warn(
            "Failed to do Token lookup for Vault server {}. trial num: {}", vaultConfig.getName(), failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  @Override
  public boolean unsetRenewalInterval(VaultConnector vaultConnector) {
    SecretManagerConfig secretManagerConfig = getSecretManagerConfig(vaultConnector.getAccountIdentifier(),
        vaultConnector.getOrgIdentifier(), vaultConnector.getProjectIdentifier(), vaultConnector.getIdentifier());
    BaseVaultConfig baseVaultConfig = (BaseVaultConfig) secretManagerConfig;

    setCertValidation(vaultConnector.getAccountIdentifier(), baseVaultConfig);

    VaultTokenLookupResult vaultTokenLookupResult = tokenLookup(baseVaultConfig);

    if (vaultTokenLookupResult == null) {
      String message = String.format(
          "Was not able to perform token lookup for Vault %s. Please check your credentials and try again",
          vaultConnector.getIdentifier());
      throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
    }

    if (vaultTokenLookupResult.getExpiryTime() == null || !vaultTokenLookupResult.isRenewable()) {
      // 1st condition means that this token is a root token
      // 2nd condition means that this token is not renewable ; both conditions imply that renewal is not required.

      Criteria criteria = Criteria.where(ConnectorKeys.id).is(vaultConnector.getId());
      Update update = new Update()
                          .set(VaultConnectorKeys.lastTokenLookupAt, System.currentTimeMillis())
                          .set(VaultConnectorKeys.renewalIntervalMinutes, 0L);
      connectorRepository.update(criteria, update, NONE, vaultConnector.getProjectIdentifier(),
          vaultConnector.getOrgIdentifier(), vaultConnector.getAccountIdentifier());

      log.info("Renewal interval set to 0 for the Vault connector: {}", vaultConnector.getUuid());
      return true;
    }
    return false;
  }

  @Override
  public VaultAppRoleLoginResult appRoleLogin(BaseVaultConfig vaultConfig) {
    String name = vaultConfig.getName();
    log.info("Renewing Vault AppRole client token for vault id {}", name);
    String accountIdentifier = vaultConfig.getAccountId();
    setCertValidation(accountIdentifier, vaultConfig);
    int failedAttempts = 0;
    while (true) {
      try {
        NGVaultRenewalTaskParameters parameters =
            NGVaultRenewalTaskParameters.builder().encryptionConfig(vaultConfig).build();

        DelegateResponseData delegateResponseData =
            getDelegateResponseData(accountIdentifier, parameters, NG_VAULT_RENEW_APP_ROLE_TOKEN);

        if (!(delegateResponseData instanceof NGVaultRenewalAppRoleTaskResponse)) {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, UNKNOWN_RESPONSE, USER);
        }

        NGVaultRenewalAppRoleTaskResponse ngVaultRenewalAppRoleTaskResponse =
            (NGVaultRenewalAppRoleTaskResponse) delegateResponseData;
        VaultAppRoleLoginResult loginResult = ngVaultRenewalAppRoleTaskResponse.getVaultAppRoleLoginResult();
        checkNotNull(loginResult, "Login result during vault appRole login should not be null");
        checkNotNull(loginResult.getClientToken(), "Client token should not be empty");
        log.info("Login result is {} {}", loginResult.getLeaseDuration(), loginResult.getPolicies());
        return loginResult;
      } catch (WingsException e) {
        failedAttempts++;
        log.warn("Vault Decryption failed for list secret engines for Vault serverer {}. trial num: {}",
            vaultConfig.getName(), failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  @Override
  public SecretManagerMetadataDTO getListOfEngines(
      String accountIdentifier, SecretManagerMetadataRequestDTO requestDTO) {
    List<SecretRefData> secretRefDataList = getSecretRefData(requestDTO);

    if (isNotEmpty(secretRefDataList)) {
      // get Decrypted SecretRefData
      for (SecretRefData secretRefData : secretRefDataList) {
        decryptSecretRefData(
            accountIdentifier, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), secretRefData);
      }
    }
    EncryptionConfig existingVaultEncryptionConfig = getDecryptedEncryptionConfig(accountIdentifier,
        requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier(), requestDTO.getIdentifier());
    if (VAULT == requestDTO.getEncryptionType()) {
      return getHashicorpVaultMetadata(accountIdentifier, requestDTO, existingVaultEncryptionConfig);
    } else if (AZURE_VAULT == requestDTO.getEncryptionType()) {
      return getAzureKeyVaultMetadata(accountIdentifier, requestDTO, existingVaultEncryptionConfig);
    } else {
      throw new UnsupportedOperationException(
          "This API is not supported for secret manager of type: " + requestDTO.getEncryptionType());
    }
  }

  @Override
  public void processTokenLookup(ConnectorDTO connectorDTO, String accountIdentifier) {
    AccountId accountId = AccountId.newBuilder().setId(accountIdentifier).build();
    io.harness.delegate.TaskType taskType =
        io.harness.delegate.TaskType.newBuilder().setType(NG_VAULT_TOKEN_LOOKUP.name()).build();
    if (!delegateService.isTaskTypeSupported(accountId, taskType)) {
      return;
    }
    if (!isTokenLookupRequired(connectorDTO)) {
      return;
    }

    ConnectorInfoDTO connectorInfo = connectorDTO.getConnectorInfo();
    VaultConnectorDTO vaultConnectorDTO = (VaultConnectorDTO) connectorInfo.getConnectorConfig();

    SecretRefData secretRefData = vaultConnectorDTO.getAuthToken();
    String orgIdentifier = connectorInfo.getOrgIdentifier();
    String projectIdentifier = connectorInfo.getProjectIdentifier();
    decryptSecretRefData(accountIdentifier, orgIdentifier, projectIdentifier, secretRefData);
    VaultConfig vaultConfig = commonVaultConfigBuilder(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorInfo.getName(), vaultConnectorDTO);
    vaultConfig.setAuthToken(String.valueOf(secretRefData.getDecryptedValue()));

    VaultTokenLookupResult tokenLookupResult = tokenLookup(vaultConfig);
    if (tokenLookupResult == null) {
      String message = "Was not able to perform token lookup (self). Please check your credentials and try again";
      throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
    }
    if (tokenLookupResult.getExpiryTime() == null) {
      // this means this is root token
      throw new SecretManagementException(
          "The token used is a root token. Please set renewal interval as zero if you are using root token.");
    }
    if (!tokenLookupResult.isRenewable()) {
      // this means the token is not renewable
      throw new SecretManagementException(
          "The token used is a non-renewable token. Please set renewal interval as zero or use a renewable token.");
    }
  }

  @Override
  public void processAppRole(ConnectorDTO connectorDTO, ConnectorConfigDTO existingConnectorConfigDTO,
      String accountIdentifier, boolean create) {
    if (!isProcessAppRoleInputValid(connectorDTO, accountIdentifier)) {
      return;
    }

    ConnectorInfoDTO connectorInfo = connectorDTO.getConnectorInfo();
    VaultConnectorDTO vaultConnectorDTO = (VaultConnectorDTO) connectorInfo.getConnectorConfig();

    SecretRefData secretRefData = vaultConnectorDTO.getSecretId();
    String orgIdentifier = connectorInfo.getOrgIdentifier();
    String projectIdentifier = connectorInfo.getProjectIdentifier();
    decryptSecretRefData(accountIdentifier, orgIdentifier, projectIdentifier, secretRefData);
    VaultConfig vaultConfig = commonVaultConfigBuilder(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorInfo.getName(), vaultConnectorDTO);
    vaultConfig.setAppRoleId(vaultConnectorDTO.getAppRoleId());
    vaultConfig.setSecretId(String.valueOf(secretRefData.getDecryptedValue()));

    VaultAppRoleLoginResult loginResult = appRoleLogin(vaultConfig);
    if (loginResult == null || isEmpty(loginResult.getClientToken())) {
      String message =
          "Was not able to login Vault using the AppRole auth method. Please check your credentials and try again";
      throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
    }

    if (!vaultConnectorDTO.isRenewAppRoleToken()) {
      return;
    }

    Scope scope = secretRefData.getScope();
    orgIdentifier = getOrgIdentifier(orgIdentifier, scope);
    projectIdentifier = getProjectIdentifier(projectIdentifier, scope);

    if (null != existingConnectorConfigDTO
        && APP_ROLE != ((VaultConnectorDTO) existingConnectorConfigDTO).getAccessType()) {
      create = true;
    }
    SecretRefData authTokenRefData = populateSecretRefData(
        connectorInfo.getIdentifier() + "_" + VaultConnectorKeys.authTokenRef,
        loginResult.getClientToken().toCharArray(), scope, accountIdentifier, orgIdentifier, projectIdentifier, create);
    vaultConnectorDTO.setAuthToken(authTokenRefData);
  }

  private VaultConfig commonVaultConfigBuilder(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String name, VaultConnectorDTO vaultConnectorDTO) {
    return VaultConfig.builder()
        .accountId(accountIdentifier)
        .name(name)
        .vaultUrl(vaultConnectorDTO.getVaultUrl())
        .namespace(vaultConnectorDTO.getNamespace())
        .ngMetadata(NGSecretManagerMetadata.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build())
        .build();
  }

  private boolean isTokenLookupRequired(ConnectorDTO connectorDTO) {
    if (connectorDTO.getConnectorInfo() == null
        || connectorDTO.getConnectorInfo().getConnectorType() != ConnectorType.VAULT) {
      return false;
    }

    ConnectorInfoDTO connectorInfo = connectorDTO.getConnectorInfo();
    VaultConnectorDTO vaultConnectorDTO = (VaultConnectorDTO) connectorInfo.getConnectorConfig();

    return vaultConnectorDTO.getAccessType() == TOKEN && vaultConnectorDTO.getRenewalIntervalMinutes() != 0;
  }

  private boolean isProcessAppRoleInputValid(ConnectorDTO connectorDTO, String accountIdentifier) {
    if (connectorDTO.getConnectorInfo() == null
        || connectorDTO.getConnectorInfo().getConnectorType() != ConnectorType.VAULT) {
      return false;
    }

    ConnectorInfoDTO connectorInfo = connectorDTO.getConnectorInfo();
    VaultConnectorDTO vaultConnectorDTO = (VaultConnectorDTO) connectorInfo.getConnectorConfig();

    return vaultConnectorDTO.getAccessType() == AccessType.APP_ROLE;
  }

  private SecretManagerMetadataDTO getHashicorpVaultMetadata(String accountIdentifier,
      SecretManagerMetadataRequestDTO requestDTO, EncryptionConfig existingVaultEncryptionConfig) {
    BaseVaultConfig vaultConfig;
    if (null == existingVaultEncryptionConfig) {
      vaultConfig = VaultConfig.builder().accountId(accountIdentifier).build();
      vaultConfig.setNgMetadata(NGSecretManagerMetadata.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(requestDTO.getOrgIdentifier())
                                    .projectIdentifier(requestDTO.getProjectIdentifier())
                                    .build());
    } else {
      vaultConfig = (BaseVaultConfig) existingVaultEncryptionConfig;
    }

    VaultMetadataRequestSpecDTO specDTO = (VaultMetadataRequestSpecDTO) requestDTO.getSpec();

    Optional<String> urlFromRequest = Optional.ofNullable(specDTO).map(VaultMetadataRequestSpecDTO::getUrl);
    urlFromRequest.ifPresent(vaultConfig::setVaultUrl);
    Optional<String> nameSpaceFromRequest = Optional.ofNullable(specDTO).map(VaultMetadataRequestSpecDTO::getNamespace);
    nameSpaceFromRequest.ifPresent(vaultConfig::setNamespace);

    setAwsIamParams(vaultConfig, specDTO);
    setVaultAgentParams(vaultConfig, specDTO);
    setTokenParam(vaultConfig, specDTO);
    setApproleParams(vaultConfig, specDTO);
    setK8sAuthParams(vaultConfig, specDTO);

    Optional<Set<String>> delegateSelectors =
        Optional.ofNullable(specDTO).map(VaultMetadataRequestSpecDTO::getDelegateSelectors);
    delegateSelectors.ifPresent(vaultConfig::setDelegateSelectors);

    return getSecretManagerMetadataDTO(listSecretEngines(vaultConfig));
  }

  private void setApproleParams(BaseVaultConfig vaultConfig, VaultMetadataRequestSpecDTO specDTO) {
    Optional<String> appRoleIdFromRequest = Optional.ofNullable(specDTO)
                                                .filter(x -> x.getAccessType() == AccessType.APP_ROLE)
                                                .map(x -> ((VaultAppRoleCredentialDTO) (x.getSpec())).getAppRoleId())
                                                .filter(x -> !x.isEmpty());
    appRoleIdFromRequest.ifPresent(approleId -> {
      vaultConfig.setAppRoleId(approleId);
      vaultConfig.setSecretId(null);
      vaultConfig.setAuthToken(null);
      vaultConfig.setUseVaultAgent(false);
      vaultConfig.setUseAwsIam(false);
    });

    Optional<String> secretIdFromRequest =
        Optional.ofNullable(specDTO)
            .filter(x -> x.getAccessType() == AccessType.APP_ROLE)
            .map(x -> String.valueOf(((VaultAppRoleCredentialDTO) (x.getSpec())).getSecretId().getDecryptedValue()))
            .filter(x -> !x.isEmpty());
    secretIdFromRequest.ifPresent(secretId -> {
      vaultConfig.setSecretId(secretId);
      vaultConfig.setAuthToken(null);
    });
  }

  private void setTokenParam(BaseVaultConfig vaultConfig, VaultMetadataRequestSpecDTO specDTO) {
    Optional<String> tokenFromRequest =
        Optional.ofNullable(specDTO)
            .filter(x -> x.getAccessType() == TOKEN)
            .map(x -> String.valueOf(((VaultAuthTokenCredentialDTO) (x.getSpec())).getAuthToken().getDecryptedValue()))
            .filter(x -> !x.isEmpty());
    tokenFromRequest.ifPresent(x -> {
      vaultConfig.setAuthToken(x);
      vaultConfig.setAppRoleId(null);
      vaultConfig.setSecretId(null);
      vaultConfig.setUseVaultAgent(false);
      vaultConfig.setUseAwsIam(false);
    });
  }

  private void setVaultAgentParams(BaseVaultConfig vaultConfig, VaultMetadataRequestSpecDTO specDTO) {
    Optional<String> sinkPathFromRequest = Optional.ofNullable(specDTO)
                                               .filter(x -> x.getAccessType() == AccessType.VAULT_AGENT)
                                               .map(x -> ((VaultAgentCredentialDTO) (x.getSpec())).getSinkPath())
                                               .filter(x -> !x.isEmpty());
    sinkPathFromRequest.ifPresent(x -> {
      vaultConfig.setAuthToken(null);
      vaultConfig.setAppRoleId(null);
      vaultConfig.setSecretId(null);
      vaultConfig.setSinkPath(x);
      vaultConfig.setUseVaultAgent(true);
      vaultConfig.setUseAwsIam(false);
    });
  }

  private void setAwsIamParams(BaseVaultConfig vaultConfig, VaultMetadataRequestSpecDTO specDTO) {
    Optional<String> vaultAwsIamRole =
        Optional.ofNullable(specDTO)
            .filter(x -> x.getAccessType() == AccessType.AWS_IAM)
            .map(x -> ((VaultAwsIamRoleCredentialDTO) (x.getSpec())).getVaultAwsIamRole())
            .filter(x -> !x.isEmpty());
    vaultAwsIamRole.ifPresent(x -> {
      vaultConfig.setAuthToken(null);
      vaultConfig.setAppRoleId(null);
      vaultConfig.setSecretId(null);
      vaultConfig.setSinkPath(null);
      vaultConfig.setUseVaultAgent(false);
      vaultConfig.setUseAwsIam(true);
      vaultConfig.setVaultAwsIamRole(x);
    });

    Optional<String> awsRegion = Optional.ofNullable(specDTO)
                                     .filter(x -> x.getAccessType() == AccessType.AWS_IAM)
                                     .map(x -> ((VaultAwsIamRoleCredentialDTO) (x.getSpec())).getAwsRegion())
                                     .filter(x -> !x.isEmpty());
    awsRegion.ifPresent(x -> { vaultConfig.setAwsRegion(x); });

    Optional<String> xVaultAwsIamServerId =
        Optional.ofNullable(specDTO)
            .filter(x -> x.getAccessType() == AccessType.AWS_IAM)
            .map(x -> getDecryptedValueOfXheader((VaultAwsIamRoleCredentialDTO) (x.getSpec())))
            .filter(x -> !x.isEmpty());
    xVaultAwsIamServerId.ifPresent(x -> { vaultConfig.setXVaultAwsIamServerId(x); });
  }

  private void setK8sAuthParams(BaseVaultConfig vaultConfig, VaultMetadataRequestSpecDTO specDTO) {
    Optional<String> vaultK8sAuthRole =
        Optional.ofNullable(specDTO)
            .filter(metadataRequestSpec -> metadataRequestSpec.getAccessType() == AccessType.K8s_AUTH)
            .map(metadataRequestSpec -> ((VaultK8sCredentialDTO) (metadataRequestSpec.getSpec())).getVaultK8sAuthRole())
            .filter(vaultK8sRole -> !vaultK8sRole.isEmpty());
    vaultK8sAuthRole.ifPresent(vaultK8sRole -> {
      vaultConfig.setAuthToken(null);
      vaultConfig.setAppRoleId(null);
      vaultConfig.setSecretId(null);
      vaultConfig.setSinkPath(null);
      vaultConfig.setUseVaultAgent(false);
      vaultConfig.setUseAwsIam(false);
      vaultConfig.setUseK8sAuth(true);
      vaultConfig.setVaultK8sAuthRole(vaultK8sRole);
    });

    Optional<String> serviceAccountTokenPath =
        Optional.ofNullable(specDTO)
            .filter(metadataRequestSpec -> metadataRequestSpec.getAccessType() == AccessType.K8s_AUTH)
            .map(metadataRequestSpec
                -> ((VaultK8sCredentialDTO) (metadataRequestSpec.getSpec())).getServiceAccountTokenPath())
            .filter(saTokenPath -> !saTokenPath.isEmpty());
    serviceAccountTokenPath.ifPresent(vaultConfig::setServiceAccountTokenPath);

    Optional<String> k8sAuthEndpoint =
        Optional.ofNullable(specDTO)
            .filter(metadataRequestSpec -> metadataRequestSpec.getAccessType() == AccessType.K8s_AUTH)
            .map(metadataRequestSpec -> ((VaultK8sCredentialDTO) (metadataRequestSpec.getSpec())).getK8sAuthEndpoint())
            .filter(k8sPath -> !k8sPath.isEmpty());
    k8sAuthEndpoint.ifPresent(vaultConfig::setK8sAuthEndpoint);
  }

  private SecretManagerMetadataDTO getAzureKeyVaultMetadata(String accountIdentifier,
      SecretManagerMetadataRequestDTO requestDTO, EncryptionConfig existingVaultEncryptionConfig) {
    AzureKeyVaultMetadataRequestSpecDTO specDTO = (AzureKeyVaultMetadataRequestSpecDTO) requestDTO.getSpec();
    AzureVaultConfig azureVaultConfig;
    if (null != existingVaultEncryptionConfig) {
      azureVaultConfig = (AzureVaultConfig) existingVaultEncryptionConfig;
    } else {
      azureVaultConfig = AzureVaultConfig.builder().build();
      azureVaultConfig.setAccountId(accountIdentifier);
      azureVaultConfig.setNgMetadata(NGSecretManagerMetadata.builder()
                                         .orgIdentifier(requestDTO.getOrgIdentifier())
                                         .projectIdentifier(requestDTO.getProjectIdentifier())
                                         .build());
    }
    Optional.ofNullable(specDTO.getClientId()).ifPresent(azureVaultConfig::setClientId);
    Optional.ofNullable(specDTO.getTenantId()).ifPresent(azureVaultConfig::setTenantId);
    Optional.ofNullable(specDTO.getSubscription()).ifPresent(azureVaultConfig::setSubscription);
    Optional.ofNullable(specDTO.getSecretKey())
        .ifPresent(secretKey -> azureVaultConfig.setSecretKey(String.valueOf(secretKey.getDecryptedValue())));
    Optional.ofNullable(specDTO.getAzureEnvironmentType()).ifPresent(azureVaultConfig::setAzureEnvironmentType);
    Optional.ofNullable(specDTO.getDelegateSelectors()).ifPresent(azureVaultConfig::setDelegateSelectors);
    Optional.ofNullable(specDTO.getUseManagedIdentity()).ifPresent(azureVaultConfig::setUseManagedIdentity);
    Optional.ofNullable(specDTO.getManagedClientId()).ifPresent(azureVaultConfig::setManagedClientId);
    Optional.ofNullable(specDTO.getAzureManagedIdentityType()).ifPresent(azureVaultConfig::setAzureManagedIdentityType);
    List<String> vaultNames;
    try {
      vaultNames = listVaultsInternal(accountIdentifier, azureVaultConfig);
    } catch (WingsException wingsException) {
      log.error("Listing vaults failed for account Id {}", accountIdentifier);
      throw wingsException; // for error handling framework
    } catch (Exception e) {
      log.error("Listing vaults failed for account Id {}", accountIdentifier, e);
      throw new AzureServiceException("Failed to list vaults.", INVALID_AZURE_VAULT_CONFIGURATION, USER);
    }

    return SecretManagerMetadataDTO.builder()
        .encryptionType(AZURE_VAULT)
        .spec(AzureKeyVaultMetadataSpecDTO.builder().vaultNames(vaultNames).build())
        .build();
  }

  private List<String> listVaultsInternal(String accountId, AzureVaultConfig azureVaultConfig) throws IOException {
    AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO =
        AzureKeyVaultConnectorDTO.builder()
            .tenantId(azureVaultConfig.getTenantId())
            .clientId(azureVaultConfig.getClientId())
            .secretKey(azureVaultConfig.getSecretKey() != null
                    ? SecretRefData.builder().decryptedValue(azureVaultConfig.getSecretKey().toCharArray()).build()
                    : null)
            .subscription(azureVaultConfig.getSubscription())
            .delegateSelectors(azureVaultConfig.getDelegateSelectors())
            .azureEnvironmentType(azureVaultConfig.getAzureEnvironmentType())
            .useManagedIdentity(azureVaultConfig.getUseManagedIdentity())
            .azureManagedIdentityType(azureVaultConfig.getAzureManagedIdentityType())
            .managedClientId(azureVaultConfig.getManagedClientId())
            .build();
    int failedAttempts = 0;
    while (true) {
      try {
        NGAzureKeyVaultFetchEngineTaskParameters parameters = NGAzureKeyVaultFetchEngineTaskParameters.builder()
                                                                  .azureKeyVaultConnectorDTO(azureKeyVaultConnectorDTO)
                                                                  .build();
        DelegateTaskRequest delegateTaskRequest =
            DelegateTaskRequest.builder()
                .taskType(NG_AZURE_VAULT_FETCH_ENGINES.name())
                .taskParameters(parameters)
                .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
                .accountId(accountId)
                .taskSetupAbstractions(ngManagerEncryptorHelper.buildAbstractions(azureVaultConfig))
                .build();
        DelegateResponseData delegateResponseData;
        try {
          delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
        } catch (DelegateServiceDriverException ex) {
          if (ex.getMessage() != null) {
            throw new WingsException(ex.getMessage(), ex);
          } else {
            throw new WingsException(String.format(
                "Listing secret engines failed. Please check if active delegates are available in the account"));
          }
        }
        DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
        if (!(delegateResponseData instanceof NGAzureKeyVaultFetchEngineResponse)) {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, UNKNOWN_RESPONSE, USER);
        }
        return ((NGAzureKeyVaultFetchEngineResponse) delegateResponseData).getSecretEngines();
      } catch (WingsException e) {
        if (e.getCause() instanceof GeneralException && "Null Pointer Exception".equals(e.getCause().getMessage())
            && azureVaultConfig.getUseManagedIdentity() && azureVaultConfig.getSecretKey() == null) {
          throw new WingsException(INTERNAL_SERVER_ERROR,
              "Listing secret engines failed. Please check if delegate version is 791xx or later.");
        }
        failedAttempts++;
        log.warn("Azure Key Vault Decryption failed for list secret engines. trial num: {}", failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private SecretRefData populateSecretRefData(String identifier, char[] decryptedValue, Scope secretScope,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, boolean create) {
    SecretTextSpecDTO secretTextSpecDTO = SecretTextSpecDTO.builder()
                                              .value(String.valueOf(decryptedValue))
                                              .valueType(ValueType.Inline)
                                              .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                              .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .identifier(identifier)
                                  .name(identifier)
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .spec(secretTextSpecDTO)
                                  .build();
    if (create) {
      createOrUpdateToken(identifier, accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2);
    } else {
      updateToken(identifier, accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2);
    }

    return new SecretRefData(identifier, secretScope, decryptedValue);
  }

  private void createOrUpdateToken(String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SecretDTOV2 secretDTOV2) {
    try {
      secretCrudService.create(accountIdentifier, secretDTOV2);
    } catch (Exception e) {
      log.info("NG: Creating new token. Failed to create token for: " + identifier);
      updateToken(identifier, accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2);
    }
  }

  private void updateToken(String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      SecretDTOV2 secretDTOV2) {
    try {
      secretCrudService.update(accountIdentifier, orgIdentifier, projectIdentifier, identifier, secretDTOV2);
    } catch (Exception e) {
      String message = "NG: Updating token. Failed to update token for: " + identifier;
      log.error(message, e);
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, USER);
    }
  }

  private List<SecretRefData> getSecretRefData(SecretManagerMetadataRequestDTO requestDTO) {
    List<SecretRefData> secretRefDataList = new ArrayList<>();
    SecretRefData secretRefData;
    if (VAULT == requestDTO.getEncryptionType()) {
      VaultMetadataRequestSpecDTO spec = (VaultMetadataRequestSpecDTO) requestDTO.getSpec();
      if (TOKEN == spec.getAccessType()) {
        secretRefData = ((VaultAuthTokenCredentialDTO) spec.getSpec()).getAuthToken();
        secretRefDataList.add(secretRefData);
      } else if (APP_ROLE == spec.getAccessType()) {
        secretRefData = ((VaultAppRoleCredentialDTO) spec.getSpec()).getSecretId();
        secretRefDataList.add(secretRefData);
      } else if (AWS_IAM == spec.getAccessType()) {
        VaultAwsIamRoleCredentialDTO vaultCredentialDTO = (VaultAwsIamRoleCredentialDTO) spec.getSpec();
        if (null != checkIfXHeaderExistOfReturnNull(vaultCredentialDTO)) {
          secretRefDataList.add(checkIfXHeaderExistOfReturnNull(vaultCredentialDTO));
        }
      } else {
        // n case of VAULT_AGENT we don't have any secretref
        return null;
      }
    } else { // Azure Key Vault
      secretRefData = ((AzureKeyVaultMetadataRequestSpecDTO) requestDTO.getSpec()).getSecretKey();
      if (secretRefData != null) {
        secretRefDataList.add(secretRefData);
      }
    }
    return secretRefDataList;
  }

  @Nullable
  private EncryptionConfig getDecryptedEncryptionConfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretManagerIdentifier) {
    SecretManagerConfig secretManagerConfig = null;
    try {
      secretManagerConfig =
          getSecretManagerConfig(accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);
    } catch (NotFoundException e) {
      log.info("Connector not found due to ", e);
    }
    return secretManagerConfig;
  }

  private SecretManagerMetadataDTO getSecretManagerMetadataDTO(List<SecretEngineSummary> secretEngineSummaryList) {
    return SecretManagerMetadataDTO.builder()
        .encryptionType(VAULT)
        .spec(VaultMetadataSpecDTO.builder()
                  .secretEngines(
                      secretEngineSummaryList.stream().map(this::fromSecretEngineSummary).collect(Collectors.toList()))
                  .build())
        .build();
  }

  private VaultSecretEngineDTO fromSecretEngineSummary(SecretEngineSummary secretEngineSummary) {
    if (secretEngineSummary == null) {
      return null;
    }
    return VaultSecretEngineDTO.builder()
        .name(secretEngineSummary.getName())
        .description(secretEngineSummary.getDescription())
        .type(secretEngineSummary.getType())
        .version(secretEngineSummary.getVersion())
        .build();
  }

  private void setCertValidation(String accountIdentifier, BaseVaultConfig secretManagerConfig) {
    Boolean isCertValidationRequired =
        getResponse(accountClient.isFeatureFlagEnabled(ENABLE_CERT_VALIDATION.name(), accountIdentifier));
    secretManagerConfig.setCertValidationRequired(isCertValidationRequired);
  }

  @NotNull
  private SecretManagerConfig getSecretManagerConfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    SecretManagerConfigDTO secretManagerConfigDTO = ngConnectorSecretManagerService.getUsingIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    return SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO);
  }

  private List<SecretEngineSummary> listSecretEnginesInternal(BaseVaultConfig vaultConfig) {
    int failedAttempts = 0;
    while (true) {
      try {
        NGVaultRenewalTaskParameters parameters =
            NGVaultRenewalTaskParameters.builder().encryptionConfig(vaultConfig).build();

        DelegateResponseData delegateResponseData =
            getDelegateResponseData(vaultConfig.getAccountId(), parameters, NG_VAULT_FETCHING_TASK);

        if (!(delegateResponseData instanceof NGVaultFetchEngineTaskResponse)) {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, UNKNOWN_RESPONSE, USER);
        }
        return ((NGVaultFetchEngineTaskResponse) delegateResponseData).getSecretEngineSummaryList();
      } catch (WingsException e) {
        failedAttempts++;
        log.warn("Vault Decryption failed for list secret engines for Vault server {}. trial num: {}",
            vaultConfig.getName(), failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private DelegateResponseData getDelegateResponseData(
      String accountIdentifier, NGVaultRenewalTaskParameters parameters, TaskType taskType) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(taskType.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountIdentifier)
            .taskSetupAbstractions(ngManagerEncryptorHelper.buildAbstractions(parameters.getEncryptionConfig()))
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    return delegateResponseData;
  }

  @VisibleForTesting
  protected void decryptSecretRefData(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SecretRefData secretRefData) {
    // Get Scope of secretRefData as per RequestDTO's details
    Scope scope = secretRefData.getScope();
    orgIdentifier = getOrgIdentifier(orgIdentifier, scope);
    projectIdentifier = getProjectIdentifier(projectIdentifier, scope);

    // Get EncryptedData
    NGEncryptedData encryptedData =
        encryptedDataService.get(accountIdentifier, orgIdentifier, projectIdentifier, secretRefData.getIdentifier());

    if (encryptedData == null) {
      throw new SecretManagementException(SECRET_NOT_FOUND,
          String.format("Secret [%s] not found or has been deleted.", secretRefData.getIdentifier()), USER);
    }

    // Get KMS Config for secret Manager of encrypted data's secret manager
    EncryptionConfig encryptionConfig;
    if (encryptedData.getEncryptionType() == LOCAL) {
      encryptionConfig =
          SecretManagerConfigMapper.fromDTO(ngConnectorSecretManagerService.getLocalConfigDTO(accountIdentifier));
    } else {
      encryptionConfig = getDecryptedEncryptionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, encryptedData.getSecretManagerIdentifier());
    }

    // Decrypt the encypted data with above KMS Config
    char[] decryptedValue = ngEncryptorService.fetchSecretValue(
        accountIdentifier, buildEncryptedRecordData(encryptedData), encryptionConfig);

    // Set decrypted value in given secretRefData object
    secretRefData.setDecryptedValue(decryptedValue);
  }

  static void checkNotNull(Object object, String errorMessage) {
    checkNotNull(object, ErrorCode.SECRET_MANAGEMENT_ERROR, errorMessage);
  }

  static void checkNotNull(Object object, ErrorCode errorCode, String errorMessage) {
    if (object == null) {
      throw new SecretManagementException(errorCode, errorMessage, USER);
    }
  }

  private String getOrgIdentifier(String parentOrgIdentifier, @javax.validation.constraints.NotNull Scope scope) {
    if (scope != Scope.ACCOUNT) {
      return parentOrgIdentifier;
    }
    return null;
  }

  private String getProjectIdentifier(
      String parentProjectIdentifier, @javax.validation.constraints.NotNull Scope scope) {
    if (scope == Scope.PROJECT) {
      return parentProjectIdentifier;
    }
    return null;
  }

  private EncryptedRecordData buildEncryptedRecordData(NGEncryptedData encryptedData) {
    return EncryptedRecordData.builder()
        .uuid(encryptedData.getUuid())
        .name(encryptedData.getName())
        .path(encryptedData.getPath())
        .parameters(encryptedData.getParameters())
        .encryptionKey(encryptedData.getEncryptionKey())
        .encryptedValue(encryptedData.getEncryptedValue())
        .kmsId(encryptedData.getKmsId())
        .encryptionType(encryptedData.getEncryptionType())
        .base64Encoded(encryptedData.isBase64Encoded())
        .build();
  }

  private void updatePerpetualTaskWhenTokenIsRenewed(VaultConnector vaultConnector) {
    String heartBeatPerpetualTaskId =
        ngConnectorSecretManagerService.getPerpetualTaskId(vaultConnector.getAccountIdentifier(),
            vaultConnector.getOrgIdentifier(), vaultConnector.getProjectIdentifier(), vaultConnector.getIdentifier());
    ngConnectorSecretManagerService.resetHeartBeatTask(vaultConnector.getAccountIdentifier(), heartBeatPerpetualTaskId);
  }

  private String getDecryptedValueOfXheader(VaultAwsIamRoleCredentialDTO specDTO) {
    if (null != specDTO.getXVaultAwsIamServerId()) {
      return String.valueOf(specDTO.getXVaultAwsIamServerId().getDecryptedValue());
    }
    return null;
  }

  private SecretRefData checkIfXHeaderExistOfReturnNull(VaultAwsIamRoleCredentialDTO specDTO) {
    if (null != specDTO.getXVaultAwsIamServerId()) {
      return specDTO.getXVaultAwsIamServerId();
    }
    return null;
  }
}
