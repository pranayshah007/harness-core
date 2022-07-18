/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.ENABLE_CERT_VALIDATION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_AZURE_VAULT_CONFIGURATION;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.remote.client.RestClientUtils.getResponse;
import static io.harness.security.encryption.AccessType.APP_ROLE;
import static io.harness.security.encryption.AccessType.AWS_IAM;
import static io.harness.security.encryption.AccessType.TOKEN;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.VAULT;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.TaskType.NG_AZURE_VAULT_FETCH_ENGINES;
import static software.wings.beans.TaskType.NG_VAULT_FETCHING_TASK;
import static software.wings.beans.TaskType.NG_VAULT_RENEW_APP_ROLE_TOKEN;
import static software.wings.beans.TaskType.NG_VAULT_RENEW_TOKEN;

import static java.time.Duration.ofMillis;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.connector.services.NGVaultService;
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
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AzureServiceException;
import io.harness.exception.DelegateServiceDriverException;
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
import software.wings.service.impl.security.NGEncryptorService;

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

@OwnedBy(PL)
@Slf4j
public class NGVaultServiceImpl implements NGVaultService {
  private static final int NUM_OF_RETRIES = 3;
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
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }

    NGVaultRenewalTaskResponse ngVaultRenewalTaskResponse = (NGVaultRenewalTaskResponse) delegateResponseData;
    log.info("Delegate response for renewToken: " + ngVaultRenewalTaskResponse.isSuccessful());

    if (ngVaultRenewalTaskResponse.isSuccessful()) {
      vaultConnector.setRenewedAt(System.currentTimeMillis());
      connectorRepository.save(vaultConnector, ChangeType.NONE);
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
    } catch (WingsException wingsException) {
      log.error("Listing secret engines failed for account Id {}", baseVaultConfig.getAccountId());
      throw wingsException;
    } catch (DelegateServiceDriverException ex) {
      if (ex.getCause() != null) {
        throw new WingsException(ex.getCause().getMessage(), ex);
      } else {
        throw new WingsException(ex);
      }
    } catch (Exception e) {
      log.error("Listing vault engines failed for account Id {}", baseVaultConfig.getAccountId(), e);
      throw new InvalidRequestException("Failed to list Vault engines", INVALID_CREDENTIAL, USER);
    }

    return secretEngineSummaries;
  }

  @Override
  public void renewAppRoleClientToken(VaultConnector vaultConnector) {
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
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
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
  public void processAppRole(ConnectorDTO connectorDTO, ConnectorConfigDTO existingConnectorConfigDTO,
      String accountIdentifier, boolean create) {
    if (null != connectorDTO.getConnectorInfo()
        && ConnectorType.VAULT == connectorDTO.getConnectorInfo().getConnectorType()) {
      ConnectorInfoDTO connectorInfo = connectorDTO.getConnectorInfo();
      VaultConnectorDTO vaultConnectorDTO = (VaultConnectorDTO) connectorInfo.getConnectorConfig();
      if (AccessType.APP_ROLE == vaultConnectorDTO.getAccessType()) {
        SecretRefData secretRefData = vaultConnectorDTO.getSecretId();
        String orgIdentifier = connectorInfo.getOrgIdentifier();
        String projectIdentifier = connectorInfo.getProjectIdentifier();
        decryptSecretRefData(accountIdentifier, orgIdentifier, projectIdentifier, secretRefData);
        VaultConfig vaultConfig = VaultConfig.builder()
                                      .accountId(accountIdentifier)
                                      .name(connectorInfo.getName())
                                      .vaultUrl(vaultConnectorDTO.getVaultUrl())
                                      .appRoleId(vaultConnectorDTO.getAppRoleId())
                                      .secretId(String.valueOf(secretRefData.getDecryptedValue()))
                                      .build();

        VaultAppRoleLoginResult loginResult = appRoleLogin(vaultConfig);
        if (loginResult != null && isNotEmpty(loginResult.getClientToken())) {
          Scope scope = secretRefData.getScope();
          orgIdentifier = getOrgIdentifier(orgIdentifier, scope);
          projectIdentifier = getProjectIdentifier(projectIdentifier, scope);

          if (null != existingConnectorConfigDTO
              && APP_ROLE != ((VaultConnectorDTO) existingConnectorConfigDTO).getAccessType()) {
            create = true;
          }
          SecretRefData authTokenRefData =
              populateSecretRefData(connectorInfo.getIdentifier() + "_" + VaultConnectorKeys.authTokenRef,
                  loginResult.getClientToken().toCharArray(), scope, accountIdentifier, orgIdentifier,
                  projectIdentifier, create);
          vaultConnectorDTO.setAuthToken(authTokenRefData);
        } else {
          String message =
              "Was not able to login Vault using the AppRole auth method. Please check your credentials and try again";
          throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
        }
      }
    }
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
            .map(x
                -> String.valueOf(
                    ((VaultAwsIamRoleCredentialDTO) (x.getSpec())).getXVaultAwsIamServerId().getDecryptedValue()))
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
    serviceAccountTokenPath.ifPresent(saTokenPath -> { vaultConfig.setServiceAccountTokenPath(saTokenPath); });
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
    List<String> vaultNames;
    try {
      vaultNames = listVaultsInternal(accountIdentifier, azureVaultConfig);
    } catch (WingsException wingsException) {
      log.error("Listing vaults failed for account Id {}", accountIdentifier);
      throw wingsException; // for error handling framework
    } catch (DelegateServiceDriverException ex) {
      if (ex.getCause() != null) {
        throw new WingsException(ex.getCause().getMessage(), ex);
      } else {
        throw new WingsException(ex);
      }
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
            .secretKey(SecretRefData.builder().decryptedValue(azureVaultConfig.getSecretKey().toCharArray()).build())
            .subscription(azureVaultConfig.getSubscription())
            .delegateSelectors(azureVaultConfig.getDelegateSelectors())
            .azureEnvironmentType(azureVaultConfig.getAzureEnvironmentType())
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
        DelegateResponseData delegateResponseData = delegateService.executeSyncTask(delegateTaskRequest);
        DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
        if (!(delegateResponseData instanceof NGAzureKeyVaultFetchEngineResponse)) {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
        }
        return ((NGAzureKeyVaultFetchEngineResponse) delegateResponseData).getSecretEngines();
      } catch (WingsException e) {
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
        secretRefData = ((VaultAwsIamRoleCredentialDTO) spec.getSpec()).getXVaultAwsIamServerId();
        secretRefDataList.add(secretRefData);
      } else {
        // n case of VAULT_AGENT we don't have any secretref
        return null;
      }
    } else { // Azure Key Vault
      secretRefData = ((AzureKeyVaultMetadataRequestSpecDTO) requestDTO.getSpec()).getSecretKey();
      secretRefDataList.add(secretRefData);
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
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
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
    DelegateResponseData delegateResponseData = delegateService.executeSyncTask(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    return delegateResponseData;
  }

  private void decryptSecretRefData(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SecretRefData secretRefData) {
    // Get Scope of secretRefData as per RequestDTO's details
    Scope scope = secretRefData.getScope();
    orgIdentifier = getOrgIdentifier(orgIdentifier, scope);
    projectIdentifier = getProjectIdentifier(projectIdentifier, scope);

    // Get EncryptedData
    NGEncryptedData encryptedData =
        encryptedDataService.get(accountIdentifier, orgIdentifier, projectIdentifier, secretRefData.getIdentifier());

    // Get KMS Config for secret Manager of encrypted data's secret manager
    EncryptionConfig encryptionConfig = getDecryptedEncryptionConfig(
        accountIdentifier, orgIdentifier, projectIdentifier, encryptedData.getSecretManagerIdentifier());

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
}
