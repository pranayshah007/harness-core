/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessModule._890_SM_CORE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.EncryptionType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.secretmanagerclient.NGEncryptedDataMetadata.NGEncryptedDataMetadataKeys;
import io.harness.secretmanagerclient.NGMetadata.NGMetadataKeys;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.NGSecretManagerMetadata.NGSecretManagerMetadataKeys;
import io.harness.secretmanagerclient.dto.SecretManagerConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;
import io.harness.secretmanagerclient.dto.VaultAppRoleCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultAuthTokenCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataRequestSpecDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataSpecDTO;
import io.harness.secretmanagerclient.dto.VaultSecretEngineDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultMetadataRequestSpecDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultMetadataSpecDTO;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.AccessType;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AzureVaultConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.VaultConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.security.AzureSecretsManagerService;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.NGSecretManagerService;
import software.wings.service.intfc.security.VaultService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@TargetModule(_890_SM_CORE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGSecretManagerServiceImpl implements NGSecretManagerService {
  private final VaultService vaultService;
  private final AzureSecretsManagerService azureSecretsManagerService;
  private final LocalSecretManagerService localSecretManagerService;
  private final GcpSecretsManagerService gcpSecretsManagerService;
  private final KmsService kmsService;
  private final SecretManagerConfigService secretManagerConfigService;
  private final WingsPersistence wingsPersistence;

  private static final String ACCOUNT_IDENTIFIER_KEY =
      SecretManagerConfigKeys.ngMetadata + "." + NGSecretManagerMetadataKeys.accountIdentifier;

  private static final String ORG_IDENTIFIER_KEY =
      SecretManagerConfigKeys.ngMetadata + "." + NGSecretManagerMetadataKeys.orgIdentifier;

  private static final String PROJECT_IDENTIFIER_KEY =
      SecretManagerConfigKeys.ngMetadata + "." + NGSecretManagerMetadataKeys.projectIdentifier;

  private static final String IDENTIFIER_KEY = SecretManagerConfigKeys.ngMetadata + "." + NGMetadataKeys.identifier;

  private static final String SECRET_MANAGER_IDENTIFIER_KEY =
      SecretManagerConfigKeys.ngMetadata + "." + NGEncryptedDataMetadataKeys.secretManagerIdentifier;

  private static final String DELETED_KEY =
      SecretManagerConfigKeys.ngMetadata + "." + NGSecretManagerMetadataKeys.deleted;

  @Override
  public SecretManagerConfig create(SecretManagerConfig secretManagerConfig) {
    NGSecretManagerMetadata ngMetadata = secretManagerConfig.getNgMetadata();
    if (Optional.ofNullable(ngMetadata).isPresent()) {
      boolean duplicatePresent = checkForDuplicate(ngMetadata.getAccountIdentifier(), ngMetadata.getOrgIdentifier(),
          ngMetadata.getProjectIdentifier(), ngMetadata.getIdentifier());
      if (duplicatePresent) {
        throw new DuplicateFieldException("Secret manager with same configuration exists");
      }
      validateSecretFieldsPresent(secretManagerConfig);
      switch (secretManagerConfig.getEncryptionType()) {
        case VAULT:
          vaultService.saveOrUpdateVaultConfig(
              secretManagerConfig.getAccountId(), (VaultConfig) secretManagerConfig, false);
          return secretManagerConfig;
        case AZURE_VAULT:
          azureSecretsManagerService.saveAzureSecretsManagerConfig(
              secretManagerConfig.getAccountId(), (AzureVaultConfig) secretManagerConfig);
          return secretManagerConfig;
        case GCP_KMS:
          gcpSecretsManagerService.saveGcpKmsConfig(
              secretManagerConfig.getAccountId(), (GcpKmsConfig) secretManagerConfig, false);
          return secretManagerConfig;
        case KMS:
          kmsService.saveKmsConfig(secretManagerConfig.getAccountId(), (KmsConfig) secretManagerConfig);
          return secretManagerConfig;
        case LOCAL:
          localSecretManagerService.saveLocalEncryptionConfig(
              secretManagerConfig.getAccountId(), (LocalEncryptionConfig) secretManagerConfig);
          return secretManagerConfig;
        default:
          throw new UnsupportedOperationException("secret manager not supported in NG");
      }
    }
    throw new InvalidRequestException("No such secret manager found", INVALID_REQUEST, USER);
  }

  private void validateSecretFieldsPresent(SecretManagerConfig secretManagerConfig) {
    switch (secretManagerConfig.getEncryptionType()) {
      case VAULT:
        VaultConfig vaultConfig = (VaultConfig) secretManagerConfig;
        if (AccessType.TOKEN.equals(vaultConfig.getAccessType()) && isEmpty(vaultConfig.getAuthToken())) {
          throw new InvalidRequestException("authToken should be provided for Vault.");
        }
        if (AccessType.APP_ROLE.equals(vaultConfig.getAccessType())
            && (isEmpty(vaultConfig.getAppRoleId()) || isEmpty(vaultConfig.getSecretId()))) {
          throw new InvalidRequestException("Both appRoleId and secretId should be provided for Vault.");
        }
        return;
      case AZURE_VAULT:
        AzureVaultConfig azureVaultConfig = (AzureVaultConfig) secretManagerConfig;
        if (isEmpty(azureVaultConfig.getSecretKey())) {
          throw new InvalidRequestException("secretKey should be provided for Azure Key Vault.");
        }
        return;
      default:
    }
  }

  @Override
  public ConnectorValidationResult testConnection(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Optional<SecretManagerConfig> secretManagerConfigOptional =
        get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, true);
    if (secretManagerConfigOptional.isPresent()) {
      secretManagerConfigService.decryptEncryptionConfigSecrets(
          accountIdentifier, secretManagerConfigOptional.get(), false);
      try {
        switch (secretManagerConfigOptional.get().getEncryptionType()) {
          case VAULT:
            vaultService.validateVaultConfig(accountIdentifier, (VaultConfig) secretManagerConfigOptional.get());
            return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
          case AZURE_VAULT:
            azureSecretsManagerService.validateAzureSecretsManagerConfig(
                accountIdentifier, (AzureVaultConfig) secretManagerConfigOptional.get());
            return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
          case GCP_KMS:
            gcpSecretsManagerService.validateSecretsManagerConfig(
                accountIdentifier, (GcpKmsConfig) secretManagerConfigOptional.get());
            return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
          case KMS:
            kmsService.validateSecretsManagerConfig(accountIdentifier, (KmsConfig) secretManagerConfigOptional.get());
            return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();

          case LOCAL:
            localSecretManagerService.validateLocalEncryptionConfig(
                accountIdentifier, (LocalEncryptionConfig) secretManagerConfigOptional.get());
            return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
          default:
            return ConnectorValidationResult.builder().status(ConnectivityStatus.FAILURE).build();
        }
      } catch (SecretManagementException secretManagementException) {
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errorSummary(secretManagementException.getMessage())
            .build();
      }
    }
    return ConnectorValidationResult.builder().status(ConnectivityStatus.FAILURE).build();
  }

  boolean checkForDuplicate(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, true).isPresent();
  }

  Query<SecretManagerConfig> getQuery(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return wingsPersistence.createQuery(SecretManagerConfig.class)
        .field(ACCOUNT_IDENTIFIER_KEY)
        .equal(accountIdentifier)
        .field(ORG_IDENTIFIER_KEY)
        .equal(orgIdentifier)
        .field(PROJECT_IDENTIFIER_KEY)
        .equal(projectIdentifier)
        .field(DELETED_KEY)
        .notEqual(Boolean.TRUE);
  }

  Query<SecretManagerConfig> getQueryWithIdentifiers(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    return wingsPersistence.createQuery(SecretManagerConfig.class)
        .field(ACCOUNT_IDENTIFIER_KEY)
        .equal(accountIdentifier)
        .field(ORG_IDENTIFIER_KEY)
        .equal(orgIdentifier)
        .field(PROJECT_IDENTIFIER_KEY)
        .equal(projectIdentifier)
        .field(IDENTIFIER_KEY)
        .in(identifiers)
        .field(DELETED_KEY)
        .notEqual(Boolean.TRUE);
  }

  @Override
  public List<SecretManagerConfig> list(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    Query<SecretManagerConfig> secretManagerConfigQuery;
    secretManagerConfigQuery =
        getQueryWithIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier, identifiers);
    return secretManagerConfigQuery.asList();
  }

  @Override
  public Optional<SecretManagerConfig> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, boolean maskSecrets) {
    Query<SecretManagerConfig> secretManagerConfigQuery = getQuery(accountIdentifier, orgIdentifier, projectIdentifier);
    secretManagerConfigQuery.field(IDENTIFIER_KEY).equal(identifier);
    SecretManagerConfig secretManagerConfig = secretManagerConfigQuery.get();
    if (secretManagerConfig != null && !maskSecrets) {
      secretManagerConfigService.decryptEncryptionConfigSecrets(accountIdentifier, secretManagerConfig, false);
    }
    return Optional.ofNullable(secretManagerConfig);
  }

  @Override
  public SecretManagerConfig getGlobalSecretManager(String accountIdentifier) {
    SecretManagerConfig accountSecretManagerConfig =
        secretManagerConfigService.getGlobalSecretManager(accountIdentifier);

    if (accountSecretManagerConfig == null || accountSecretManagerConfig.getEncryptionType() != GCP_KMS) {
      accountSecretManagerConfig = localSecretManagerService.getEncryptionConfig(accountIdentifier);
      accountSecretManagerConfig.setUuid(null);
      accountSecretManagerConfig.setEncryptionType(LOCAL);
    }
    return accountSecretManagerConfig;
  }

  @Override
  public long getCountOfSecretsCreatedUsingSecretManager(
      String accountIdentifier, String orgIentifier, String projectIdentifier, String secretManagerIdentifier) {
    return wingsPersistence.createQuery(EncryptedData.class)
        .field(ACCOUNT_IDENTIFIER_KEY)
        .equal(accountIdentifier)
        .field(ORG_IDENTIFIER_KEY)
        .equal(orgIentifier)
        .field(PROJECT_IDENTIFIER_KEY)
        .equal(projectIdentifier)
        .field(SECRET_MANAGER_IDENTIFIER_KEY)
        .equal(secretManagerIdentifier)
        .count();
  }

  @Override
  public SecretManagerConfig update(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, SecretManagerConfigUpdateDTO updateDTO) throws InvalidRequestException {
    Optional<SecretManagerConfig> secretManagerConfigOptional =
        get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, true);

    if (secretManagerConfigOptional.isPresent()) {
      NGSecretManagerMetadata metadata = secretManagerConfigOptional.get().getNgMetadata();

      if (Boolean.TRUE.equals(metadata.getHarnessManaged())) {
        throw new UnsupportedOperationException(
            "Update operation not supported for secret manager managed by Harness.");
      }

      boolean secretsPresentInSM =
          getCountOfSecretsCreatedUsingSecretManager(metadata.getAccountIdentifier(), metadata.getOrgIdentifier(),
              metadata.getProjectIdentifier(), metadata.getIdentifier())
          > 0;

      SecretManagerConfig oldConfig = secretManagerConfigOptional.get();
      secretManagerConfigService.decryptEncryptionConfigSecrets(accountIdentifier, oldConfig, false);
      SecretManagerConfig secretManagerConfig =
          SecretManagerConfigMapper.applyUpdate(oldConfig, updateDTO, secretsPresentInSM);

      switch (secretManagerConfig.getEncryptionType()) {
        case VAULT:
          vaultService.saveOrUpdateVaultConfig(
              secretManagerConfig.getAccountId(), (VaultConfig) secretManagerConfig, false);
          return secretManagerConfig;
        case AZURE_VAULT:
          azureSecretsManagerService.saveAzureSecretsManagerConfig(
              secretManagerConfig.getAccountId(), (AzureVaultConfig) secretManagerConfig);
          return secretManagerConfig;
        case GCP_KMS:
          gcpSecretsManagerService.updateGcpKmsConfig(
              secretManagerConfig.getAccountId(), (GcpKmsConfig) secretManagerConfig, false);
          return secretManagerConfig;
        case KMS:
          kmsService.saveKmsConfig(secretManagerConfig.getAccountId(), (KmsConfig) secretManagerConfig);
          return secretManagerConfig;
        default:
          throw new UnsupportedOperationException("Secret Manager not supported.");
      }
    } else {
      throw new InvalidRequestException("No such secret manager found.", INVALID_REQUEST, USER);
    }
  }

  @Override
  public boolean delete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, boolean softDelete) {
    Optional<SecretManagerConfig> secretManagerConfigOptional =
        get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, true);
    if (!secretManagerConfigOptional.isPresent()) {
      return false;
    }
    NGSecretManagerMetadata metadata = secretManagerConfigOptional.get().getNgMetadata();
    boolean secretsPresentInSM =
        getCountOfSecretsCreatedUsingSecretManager(metadata.getAccountIdentifier(), metadata.getOrgIdentifier(),
            metadata.getProjectIdentifier(), metadata.getIdentifier())
        > 0;
    if (secretsPresentInSM) {
      throw new InvalidRequestException(String.format(
          "Cannot delete secret manager %s with active secrets, please delete/migrate secrets and try again",
          metadata.getIdentifier()));
    }

    SecretManagerConfig secretManagerConfig = secretManagerConfigOptional.get();
    if (softDelete) {
      secretManagerConfig.getNgMetadata().setDeleted(true);
      wingsPersistence.save(secretManagerConfig);
      return true;
    } else {
      switch (secretManagerConfig.getEncryptionType()) {
        case VAULT:
          VaultConfig vaultConfig = (VaultConfig) secretManagerConfig;
          return vaultService.deleteVaultConfig(vaultConfig.getAccountId(), vaultConfig.getUuid());
        case AZURE_VAULT:
          AzureVaultConfig azureVaultConfig = (AzureVaultConfig) secretManagerConfig;
          return azureSecretsManagerService.deleteConfig(azureVaultConfig.getAccountId(), azureVaultConfig.getUuid());
        case GCP_KMS:
          GcpKmsConfig gcpKmsConfig = (GcpKmsConfig) secretManagerConfig;
          return gcpSecretsManagerService.deleteGcpKmsConfig(gcpKmsConfig.getAccountId(), gcpKmsConfig.getUuid());
        case KMS:
          KmsConfig kmsConfig = (KmsConfig) secretManagerConfig;
          return kmsService.deleteKmsConfig(kmsConfig.getAccountId(), kmsConfig.getUuid());
        case LOCAL:
          LocalEncryptionConfig localEncryptionConfig = (LocalEncryptionConfig) secretManagerConfig;
          return localSecretManagerService.deleteLocalEncryptionConfig(
              localEncryptionConfig.getAccountId(), localEncryptionConfig.getUuid());
        default:
          throw new UnsupportedOperationException(
              "This API is not supported for secret manager of type: " + secretManagerConfig.getEncryptionType());
      }
    }
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

  @Override
  public SecretManagerMetadataDTO getMetadata(String accountIdentifier, SecretManagerMetadataRequestDTO requestDTO) {
    if (requestDTO.getEncryptionType() == EncryptionType.VAULT) {
      VaultMetadataRequestSpecDTO specDTO = (VaultMetadataRequestSpecDTO) requestDTO.getSpec();
      Optional<String> urlFromRequest = Optional.ofNullable(specDTO).map(VaultMetadataRequestSpecDTO::getUrl);
      Optional<String> tokenFromRequest =
          Optional.ofNullable(specDTO)
              .filter(x -> x.getAccessType() == AccessType.TOKEN)
              .map(
                  x -> String.valueOf(((VaultAuthTokenCredentialDTO) (x.getSpec())).getAuthToken().getDecryptedValue()))
              .filter(x -> !x.isEmpty());
      Optional<String> appRoleIdFromRequest = Optional.ofNullable(specDTO)
                                                  .filter(x -> x.getAccessType() == AccessType.APP_ROLE)
                                                  .map(x -> ((VaultAppRoleCredentialDTO) (x.getSpec())).getAppRoleId())
                                                  .filter(x -> !x.isEmpty());
      Optional<String> secretIdFromRequest =
          Optional.ofNullable(specDTO)
              .filter(x -> x.getAccessType() == AccessType.APP_ROLE)
              .map(x -> String.valueOf(((VaultAppRoleCredentialDTO) (x.getSpec())).getSecretId().getDecryptedValue()))
              .filter(x -> !x.isEmpty());

      Optional<SecretManagerConfig> secretManagerConfigOptional = get(accountIdentifier, requestDTO.getOrgIdentifier(),
          requestDTO.getProjectIdentifier(), requestDTO.getIdentifier(), true);
      VaultConfig vaultConfig;
      if (secretManagerConfigOptional.isPresent()) {
        vaultConfig = (VaultConfig) secretManagerConfigOptional.get();
        secretManagerConfigService.decryptEncryptionConfigSecrets(vaultConfig.getAccountId(), vaultConfig, false);
      } else {
        vaultConfig = VaultConfig.builder().build();
        vaultConfig.setAccountId(accountIdentifier);
        vaultConfig.setNgMetadata(NGSecretManagerMetadata.builder()
                                      .orgIdentifier(requestDTO.getOrgIdentifier())
                                      .projectIdentifier(requestDTO.getProjectIdentifier())
                                      .build());
      }
      urlFromRequest.ifPresent(vaultConfig::setVaultUrl);
      tokenFromRequest.ifPresent(x -> {
        vaultConfig.setAuthToken(x);
        vaultConfig.setAppRoleId(null);
        vaultConfig.setSecretId(null);
      });
      appRoleIdFromRequest.ifPresent(approleId -> {
        vaultConfig.setAppRoleId(approleId);
        vaultConfig.setSecretId(null);
        vaultConfig.setAuthToken(null);
      });
      secretIdFromRequest.ifPresent(secretId -> {
        vaultConfig.setSecretId(secretId);
        vaultConfig.setAuthToken(null);
      });

      List<SecretEngineSummary> secretEngineSummaryList = vaultService.listSecretEngines(vaultConfig);
      return SecretManagerMetadataDTO.builder()
          .encryptionType(VAULT)
          .spec(
              VaultMetadataSpecDTO.builder()
                  .secretEngines(
                      secretEngineSummaryList.stream().map(this::fromSecretEngineSummary).collect(Collectors.toList()))
                  .build())
          .build();
    } else if (requestDTO.getEncryptionType() == EncryptionType.AZURE_VAULT) {
      AzureKeyVaultMetadataRequestSpecDTO specDTO = (AzureKeyVaultMetadataRequestSpecDTO) requestDTO.getSpec();
      Optional<SecretManagerConfig> secretManagerConfigOptional = get(accountIdentifier, requestDTO.getOrgIdentifier(),
          requestDTO.getProjectIdentifier(), requestDTO.getIdentifier(), true);
      AzureVaultConfig azureVaultConfig;
      if (secretManagerConfigOptional.isPresent()) {
        azureVaultConfig = (AzureVaultConfig) secretManagerConfigOptional.get();
        secretManagerConfigService.decryptEncryptionConfigSecrets(
            azureVaultConfig.getAccountId(), azureVaultConfig, false);
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
      Optional.ofNullable(specDTO.getUseManagedIdentity()).ifPresent(azureVaultConfig::setUseManagedIdentity);
      Optional.ofNullable(specDTO.getManagedClientId()).ifPresent(azureVaultConfig::setManagedClientId);
      Optional.ofNullable(specDTO.getAzureManagedIdentityType())
          .ifPresent(azureVaultConfig::setAzureManagedIdentityType);
      List<String> vaultNames = azureSecretsManagerService.listAzureVaults(accountIdentifier, azureVaultConfig);
      return SecretManagerMetadataDTO.builder()
          .encryptionType(AZURE_VAULT)
          .spec(AzureKeyVaultMetadataSpecDTO.builder().vaultNames(vaultNames).build())
          .build();
    }
    throw new UnsupportedOperationException(
        "This API is not supported for secret manager of type: " + requestDTO.getEncryptionType());
  }
}
