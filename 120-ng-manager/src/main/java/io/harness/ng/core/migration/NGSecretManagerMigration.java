/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AWS_KMS;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_KEY_VAULT;
import static io.harness.delegate.beans.connector.ConnectorType.GCP_KMS;
import static io.harness.delegate.beans.connector.ConnectorType.LOCAL;
import static io.harness.delegate.beans.connector.ConnectorType.VAULT;
import static io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialType.MANUAL_CONFIG;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.remote.client.CGRestUtils.getResponse;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.azurekeyvaultconnector.AzureKeyVaultConnector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.mappers.secretmanagermapper.AwsKmsMappingHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.localconnector.LocalConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO.GcpKmsConfigDTOKeys;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsManualCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsManualCredentialConfig.AwsKmsManualCredentialConfigKeys;
import io.harness.secretmanagerclient.dto.awskms.BaseAwsKmsConfigDTO.BaseAwsKmsConfigDTOKeys;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(PL)
public class NGSecretManagerMigration {
  public static final String CONNECTOR_STRING = "_";
  public static final int BATCH_SIZE = 1000;
  public final String UUID;
  private MongoTemplate mongoTemplate;
  private final ConnectorService connectorService;
  private SecretManagerConfigDTO cgGlobal;

  private final NGSecretManagerService ngSecretManagerService;
  private final SecretCrudService secretCrudService;
  private final ConnectorMapper connectorMapper;
  private final NGEncryptedDataService ngEncryptedDataService;
  private final SecretManagerClient secretManagerClient;

  @Inject
  public NGSecretManagerMigration(MongoTemplate mongoTemplate,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      NGSecretManagerService ngSecretManagerService, SecretCrudService secretCrudService,
      ConnectorMapper connectorMapper, NGEncryptedDataService ngEncryptedDataService,
      SecretManagerClient secretManagerClient) {
    this.secretManagerClient = secretManagerClient;
    this.UUID = UUIDGenerator.generateUuid();
    this.mongoTemplate = mongoTemplate;
    this.connectorService = connectorService;
    this.ngSecretManagerService = ngSecretManagerService;
    this.secretCrudService = secretCrudService;
    this.connectorMapper = connectorMapper;
    this.ngEncryptedDataService = ngEncryptedDataService;
  }

  public void migrate() {
    log.info("[NGSecretManagerMigration] Starting NGSecretManageMigration migration");

    // Create GLOBAL KMS
    ConnectorDTO globalConnectorDTO = createGlobal(GLOBAL_ACCOUNT_ID, null, null, true);
    log.info("[NGSecretManagerMigration] Global Secret Manager Created");

    // Step #1 - Get All accounts/orgs/projs
    List<String> allAccounts = fetchAllAccounts();

    // Step #2 - Set up HarnessManaged connectors
    populateHarnessManagedDefaultKms(allAccounts, globalConnectorDTO);
    log.info("[NGSecretManagerMigration] HarnessManaged KMS Created/Updated");

    // Step #3 - All connectors
    // Run for each account in a loop
    log.info("[NGSecretManagerMigration] Starting to process non HarnessManaged KMS. Batch Size: " + BATCH_SIZE);

    CloseableIterator<Connector> iterator = runQueryWithBatch(getAllConnectors(), BATCH_SIZE);
    while (iterator.hasNext()) {
      Connector nonHMConnector = iterator.next();
      processNonHMConnector(nonHMConnector);
    }

    log.info("[NGSecretManagerMigration] Completed NGSecretManageMigration migration");
  }

  private void processNonHMConnector(Connector nonHMConnector) {
    ConnectorInfoDTO connectorFromDB = connectorMapper.getConnectorInfoDTO(nonHMConnector);
    try {
      migrateFromCGToNG(nonHMConnector.getAccountIdentifier(), connectorFromDB, false);
    } catch (Exception e) {
      log.error(
          "[NGSecretManagerMigration] Moving to next connector. Could not migrate: " + connectorFromDB.getIdentifier(),
          e);
    }
  }

  public void populateHarnessManagedDefaultKms(List<String> accounts, ConnectorDTO globalConnectorDTO) {
    for (String accountIdentifier : accounts) {
      // Process Accounts
      handleHarnessManagedSM(accountIdentifier, null, null, true, globalConnectorDTO);
      List<String> orgsForAccount = fetchOrgsForAccount(accountIdentifier);
      for (String orgIdentifier : orgsForAccount) {
        // Process Orgs inside this account
        handleHarnessManagedSM(accountIdentifier, orgIdentifier, null, true, globalConnectorDTO);
        List<String> projectsForAccountAndOrg = fetchProjectsForAccountAndOrg(accountIdentifier, orgIdentifier);
        for (String proIdentifier : projectsForAccountAndOrg) {
          // Process Projects inside this org
          handleHarnessManagedSM(accountIdentifier, orgIdentifier, proIdentifier, true, globalConnectorDTO);
        }
      }
    }
  }

  private void handleHarnessManagedSM(String accountIdentifier, String orgIdentifier, String projIdentifier,
      boolean isDefault, ConnectorDTO globalConnectorDTO) {
    Scope secretScope = getSecretScope(orgIdentifier, projIdentifier);
    ConnectorInfoDTO connectorFromDB = getConnectorFromDB(accountIdentifier, orgIdentifier, projIdentifier);
    if (null == connectorFromDB) {
      log.info("[NGSecretManagerMigration] Need to create HM_KMS in NG by using global secret manager in CG");
      ConnectorDTO fromCGToNG = createHMFromCGToNG(
          accountIdentifier, orgIdentifier, projIdentifier, secretScope, isDefault, globalConnectorDTO);
      connectorService.create(fromCGToNG, accountIdentifier, ChangeType.NONE);
    } else {
      log.info(
          "[NGSecretManagerMigration] Need to replace existing HM_KMS in NG by creating a new global SM just like above");
      ConnectorDTO fromCGToNG = createHMFromCGToNG(
          accountIdentifier, orgIdentifier, projIdentifier, secretScope, isDefault, globalConnectorDTO);
      try {
        connectorService.update(fromCGToNG, accountIdentifier, ChangeType.NONE);
      } catch (NotFoundException exception) {
        log.error("[NGSecretManagerMigration] NotFoundException in updating connector", exception);
      } catch (InvalidRequestException exception) {
        log.error("[NGSecretManagerMigration] InvalidRequestException in updating connector", exception);
      } catch (Exception e) {
        log.error("[NGSecretManagerMigration] Unexpected exception occured during updation of HarnessManaged SM", e);
      }

      log.info(
          "[NGSecretManagerMigration] Successfully Completed creating/updating harness Managed KMS for accountIdentifier: "
          + accountIdentifier + ", orgIdentifier: " + orgIdentifier + ",projIdentifier: " + projIdentifier);
    }
  }

  // Get for an account
  private Criteria getAllConnectors() {
    Criteria criteria = new Criteria();
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    criteria.and(ConnectorKeys.categories).in(ConnectorCategory.SECRET_MANAGER);
    criteria.and(ConnectorKeys.identifier).ne(HARNESS_SECRET_MANAGER_IDENTIFIER);
    return criteria;
  }

  private ConnectorDTO createHMFromCGToNG(String accountIdentifier, String orgIdentifier, String projIdentifier,
      Scope secretScope, boolean isAccountDefault, ConnectorDTO globalConnectorDTO) {
    log.info("[NGSecretManagerMigration] Started creating/updating harness Managed KMS for accountIdentifier: "
        + accountIdentifier + ", orgIdentifier: " + orgIdentifier + ",projIdentifier: " + projIdentifier);

    ConnectorInfoDTO connectorInfoDTO = globalConnectorDTO.getConnectorInfo();
    SecretManagerConfigDTO globalSecretManagerConfigDTO =
        getGlobalSMFromCG(accountIdentifier, orgIdentifier, projIdentifier);
    ConnectorDTO connectorDTO = getConnectorRequestDTO(connectorInfoDTO, globalSecretManagerConfigDTO, secretScope,
        accountIdentifier, projIdentifier, orgIdentifier, isAccountDefault, true);
    connectorDTO.getConnectorInfo().setName(
        getDefaultHarnessSecretManagerName(globalSecretManagerConfigDTO.getEncryptionType()));
    return connectorDTO;
  }

  public ConnectorDTO createGlobal(
      String accountIdentifier, String orgIdentifier, String projIdentifier, boolean isAccountDefault) {
    Optional<ConnectorResponseDTO> connectorResponseDTO =
        connectorService.get(accountIdentifier, orgIdentifier, projIdentifier, HARNESS_SECRET_MANAGER_IDENTIFIER);
    if (connectorResponseDTO.isPresent()) {
      return ConnectorDTO.builder().connectorInfo(connectorResponseDTO.get().getConnector()).build();
    }

    SecretManagerConfigDTO globalSecretManagerConfigDTO =
        getGlobalSMFromCG(accountIdentifier, orgIdentifier, projIdentifier);
    Scope secretScope = getSecretScope(orgIdentifier, projIdentifier);
    if (EncryptionType.LOCAL != globalSecretManagerConfigDTO.getEncryptionType()) {
      LocalConfigDTO localConnector = createLocalConnector(accountIdentifier, this.UUID);
      connectorService.create(getConnectorRequestDTO(null, localConnector, secretScope, accountIdentifier,
                                  projIdentifier, orgIdentifier, false, false),
          accountIdentifier, ChangeType.NONE);
    }
    ConnectorDTO connectorDTO = getConnectorRequestDTO(null, globalSecretManagerConfigDTO, secretScope,
        accountIdentifier, projIdentifier, orgIdentifier, isAccountDefault, false);
    if (isAccountDefault) {
      connectorDTO.getConnectorInfo().setName(
          getDefaultHarnessSecretManagerName(globalSecretManagerConfigDTO.getEncryptionType()));
    }
    connectorService.create(connectorDTO, accountIdentifier, ChangeType.NONE);

    return connectorDTO;
  }

  public ConnectorDTO createGlobalGcpKmsSM(
      String accountIdentifier, String orgIdentifier, String projIdentifier, boolean isAccountDefault) {
    SecretManagerConfigDTO globalSecretManagerConfigDTO =
        getGlobalGcpKmsSMFromCG(accountIdentifier, orgIdentifier, projIdentifier);
    Scope secretScope = getSecretScope(orgIdentifier, projIdentifier);
    if (EncryptionType.LOCAL != globalSecretManagerConfigDTO.getEncryptionType()) {
      LocalConfigDTO localConnector = createLocalConnector(accountIdentifier, this.UUID);
      connectorService.create(getConnectorRequestDTO(null, localConnector, secretScope, accountIdentifier,
                                  projIdentifier, orgIdentifier, false, false),
          accountIdentifier, ChangeType.NONE);
    }
    ConnectorDTO connectorDTO = getConnectorRequestDTO(null, globalSecretManagerConfigDTO, secretScope,
        accountIdentifier, projIdentifier, orgIdentifier, isAccountDefault, false);
    if (isAccountDefault) {
      connectorDTO.getConnectorInfo().setName(
          getDefaultHarnessSecretManagerName(globalSecretManagerConfigDTO.getEncryptionType()));
    }
    connectorService.create(connectorDTO, accountIdentifier, ChangeType.NONE);
    return connectorDTO;
  }

  private LocalConfigDTO createLocalConnector(String accountIdentifier, String uuid) {
    return LocalConfigDTO.builder()
        .accountIdentifier(accountIdentifier)
        .encryptionType(EncryptionType.LOCAL)
        .identifier(uuid)
        .name(uuid)
        .build();
  }

  private void migrateFromCGToNG(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO, boolean isAccountDefault) {
    ConnectorConfigDTO connectorConfig = connectorInfoDTO.getConnectorConfig();
    List<Field> secretReferenceFields = connectorConfig.getSecretReferenceFields();
    boolean migrate = true;
    for (Field field : secretReferenceFields) {
      SecretRefData secretRefData = getSecretRefData(connectorConfig, field);
      if (null != secretRefData) {
        if (isNotEmpty(secretRefData.getIdentifier())) {
          if (AWS_KMS == connectorInfoDTO.getConnectorType()) {
            NGEncryptedData encryptedData =
                ngEncryptedDataService.get(accountIdentifier, connectorInfoDTO.getOrgIdentifier(),
                    connectorInfoDTO.getProjectIdentifier(), secretRefData.getIdentifier());
            if (null == encryptedData) {
              break;
            }
          }
          migrate = false;
          break;
        }
      }
    }
    if (migrate) {
      migrate(accountIdentifier, connectorInfoDTO, isAccountDefault);
    }
  }

  private void migrate(String accountIdentifier, ConnectorInfoDTO connectorInfoDTO, boolean isAccountDefault) {
    log.info("[NGSecretManagerMigration] Started updating from CG: " + connectorInfoDTO.getIdentifier());
    SecretManagerConfigDTO cgSM = getSecretManagerConfigDTOFromCG(accountIdentifier, connectorInfoDTO);
    Scope secretScope = getSecretScope(connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier());
    if (null != cgSM) {
      ConnectorDTO connectorDTO = getConnectorRequestDTO(connectorInfoDTO, cgSM, secretScope, accountIdentifier,
          connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getOrgIdentifier(), isAccountDefault, false);
      if (isAccountDefault) {
        connectorDTO.getConnectorInfo().setName(getDefaultHarnessSecretManagerName(cgSM.getEncryptionType()));
      }
      try {
        connectorService.update(connectorDTO, accountIdentifier, ChangeType.NONE);
        log.info("[NGSecretManagerMigration] Successfully updated from CG: "
            + connectorDTO.getConnectorInfo().getIdentifier());
      } catch (Exception e) {
        log.error("[NGSecretManagerMigration] Moving to next connector. Could not migrate: "
                + connectorInfoDTO.getIdentifier(),
            e);
      }
    } else {
      log.info("[NGSecretManagerMigration] Data Not found in CG for KMS: " + connectorInfoDTO.getIdentifier());
    }
  }

  @Nullable
  private SecretRefData getSecretRefData(ConnectorConfigDTO connectorConfig, Field field) {
    field.setAccessible(true);
    SecretRefData secretRefData = null;
    try {
      secretRefData = (SecretRefData) field.get(connectorConfig);
    } catch (IllegalAccessException e) {
      log.error("[NGSecretManagerMigration] Error in getting SecretField ", e);
    }
    return secretRefData;
  }

  private SecretManagerConfigDTO getSecretManagerConfigDTOFromCG(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    return getResponse(secretManagerClient.getSecretManager(connectorInfoDTO.getIdentifier(), accountIdentifier,
        connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(), false));
  }

  private SecretManagerConfigDTO getGlobalSMFromCG(
      String accountIdentifier, String orgIdentifier, String projIdentifier) {
    if (null == this.cgGlobal) {
      this.cgGlobal = ngSecretManagerService.getGlobalSecretManager(accountIdentifier);
    }
    SecretManagerConfigDTO globalSecretManager = this.cgGlobal;
    globalSecretManager.setIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER);
    globalSecretManager.setName(getDefaultHarnessSecretManagerName(globalSecretManager.getEncryptionType()));
    globalSecretManager.setProjectIdentifier(projIdentifier);
    globalSecretManager.setOrgIdentifier(orgIdentifier);
    globalSecretManager.setDefault(true);
    return globalSecretManager;
  }

  private SecretManagerConfigDTO getGlobalGcpKmsSMFromCG(
      String accountIdentifier, String orgIdentifier, String projIdentifier) {
    if (null == this.cgGlobal) {
      this.cgGlobal = ngSecretManagerService.getGlobalSecretManagerFromCG(accountIdentifier);
    }
    SecretManagerConfigDTO globalSecretManager = this.cgGlobal;
    globalSecretManager.setIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER);
    globalSecretManager.setName(getDefaultHarnessSecretManagerName(globalSecretManager.getEncryptionType()));
    globalSecretManager.setProjectIdentifier(projIdentifier);
    globalSecretManager.setOrgIdentifier(orgIdentifier);
    globalSecretManager.setDefault(true);
    return globalSecretManager;
  }

  private ConnectorInfoDTO getConnectorFromDB(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    List<Connector> connectors = runQuery(getHarnessManagedKms(accountIdentifier, orgIdentifier, projIdentifier));
    if (isNotEmpty(connectors)) {
      return connectorMapper.getConnectorInfoDTO(connectors.get(0));
    }
    return null;
  }

  public List<String> fetchAllAccounts() {
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.categories).in(ConnectorCategory.SECRET_MANAGER);
    criteria.and(ConnectorKeys.accountIdentifier).ne(GLOBAL_ACCOUNT_ID);
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, ConnectorKeys.accountIdentifier, Connector.class, String.class);
  }

  private List<String> fetchOrgsForAccount(String accountIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(OrganizationKeys.accountIdentifier).is(accountIdentifier);
    criteria.orOperator(where(OrganizationKeys.deleted).exists(false), where(OrganizationKeys.deleted).is(false));
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, OrganizationKeys.identifier, Organization.class, String.class);
  }

  private List<String> fetchProjectsForAccountAndOrg(String accountIdentifier, String orgIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(ProjectKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(ProjectKeys.orgIdentifier).is(orgIdentifier);
    criteria.orOperator(where(ProjectKeys.deleted).exists(false), where(ProjectKeys.deleted).is(false));
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, ProjectKeys.identifier, Project.class, String.class);
  }

  private Criteria getHarnessManagedKms(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    Criteria criteria = new Criteria();
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    criteria.and(ConnectorKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(ConnectorKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(ConnectorKeys.projectIdentifier).is(projIdentifier);
    criteria.and(ConnectorKeys.identifier).is(HARNESS_SECRET_MANAGER_IDENTIFIER);
    return criteria;
  }

  private List<Connector> runQuery(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, Connector.class);
  }

  private CloseableIterator<Connector> runQueryWithBatch(Criteria criteria, int batchSize) {
    Query query = new Query(criteria);
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, Connector.class);
  }

  private ConnectorDTO getConnectorRequestDTO(ConnectorInfoDTO connectorInfoDTO,
      SecretManagerConfigDTO secretManagerConfigDTO, Scope secretScope, String accountIdentifier,
      String projectIdentifier, String orgIdentifier, boolean harnessManaged, boolean referenceGlobalKms) {
    ConnectorInfoDTO connectorInfo;
    String identifier;
    switch (secretManagerConfigDTO.getEncryptionType()) {
      case VAULT:
        VaultConfigDTO vaultConfigDTO = (VaultConfigDTO) secretManagerConfigDTO;
        identifier = secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING + VaultConnectorKeys.authTokenRef;
        SecretRefData authTokenRefData = populateSecretRefData(identifier, vaultConfigDTO.getAuthToken().toCharArray(),
            secretScope, accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);
        SecretRefData secretKey = null;
        if (null != vaultConfigDTO.getSecretId()) {
          identifier = secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING + VaultConnectorKeys.secretIdRef;
          secretKey = populateSecretRefData(identifier, vaultConfigDTO.getSecretId().toCharArray(), secretScope,
              accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);
        }
        VaultConnectorDTO vaultConnectorDTO =
            VaultConnectorDTO.builder()
                .authToken(authTokenRefData)
                .isDefault(vaultConfigDTO.isDefault())
                .isReadOnly(vaultConfigDTO.isReadOnly())
                .vaultUrl(vaultConfigDTO.getVaultUrl())
                .secretEngineName(vaultConfigDTO.getSecretEngineName())
                .secretEngineVersion(vaultConfigDTO.getSecretEngineVersion())
                .renewalIntervalMinutes(vaultConfigDTO.getRenewalIntervalMinutes())
                .basePath(vaultConfigDTO.getBasePath())
                .secretEngineManuallyConfigured(vaultConfigDTO.isEngineManuallyEntered())
                .appRoleId(vaultConfigDTO.getAppRoleId())
                .secretId(secretKey)
                .build();
        connectorInfo = ConnectorInfoDTO.builder()
                            .connectorType(VAULT)
                            .identifier(secretManagerConfigDTO.getIdentifier())
                            .name(secretManagerConfigDTO.getName())
                            .orgIdentifier(secretManagerConfigDTO.getOrgIdentifier())
                            .projectIdentifier(secretManagerConfigDTO.getProjectIdentifier())
                            .description(secretManagerConfigDTO.getDescription())
                            .connectorConfig(vaultConnectorDTO)
                            .build();
        break;
      case AZURE_VAULT:
        AzureKeyVaultConfigDTO azureKeyVaultConfigDTO = (AzureKeyVaultConfigDTO) secretManagerConfigDTO;
        identifier = secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING
            + AzureKeyVaultConnector.VaultConnectorKeys.secretKeyRef;
        SecretRefData azureSecretRef =
            populateSecretRefData(identifier, azureKeyVaultConfigDTO.getSecretKey().toCharArray(), secretScope,
                accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);
        AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO =
            AzureKeyVaultConnectorDTO.builder()
                .isDefault(azureKeyVaultConfigDTO.isDefault())
                .clientId(azureKeyVaultConfigDTO.getClientId())
                .tenantId(azureKeyVaultConfigDTO.getTenantId())
                .vaultName(azureKeyVaultConfigDTO.getVaultName())
                .secretKey(azureSecretRef)
                .subscription(azureKeyVaultConfigDTO.getSubscription())
                .azureEnvironmentType(azureKeyVaultConfigDTO.getAzureEnvironmentType())
                .build();
        connectorInfo = ConnectorInfoDTO.builder()
                            .connectorType(AZURE_KEY_VAULT)
                            .identifier(secretManagerConfigDTO.getIdentifier())
                            .name(secretManagerConfigDTO.getName())
                            .orgIdentifier(secretManagerConfigDTO.getOrgIdentifier())
                            .projectIdentifier(secretManagerConfigDTO.getProjectIdentifier())
                            .description(secretManagerConfigDTO.getDescription())
                            .connectorConfig(azureKeyVaultConnectorDTO)
                            .build();
        break;
      case GCP_KMS:
        GcpKmsConfigDTO gcpKmsConfig = (GcpKmsConfigDTO) secretManagerConfigDTO;
        GcpKmsConnectorDTO gcpKmsConnectorDTO =
            GcpKmsConnectorDTO.builder().isDefault(secretManagerConfigDTO.isDefault()).build();
        if (!referenceGlobalKms) {
          identifier = secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING + GcpKmsConfigDTOKeys.credentials;
          SecretRefData credentialsRefData = populateSecretRefData(identifier, gcpKmsConfig.getCredentials(),
              secretScope, accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);
          gcpKmsConnectorDTO.setRegion(gcpKmsConfig.getRegion());
          gcpKmsConnectorDTO.setProjectId(gcpKmsConfig.getProjectId());
          gcpKmsConnectorDTO.setKeyName(gcpKmsConfig.getKeyName());
          gcpKmsConnectorDTO.setKeyRing(gcpKmsConfig.getKeyRing());
          gcpKmsConnectorDTO.setCredentials(credentialsRefData);
        }
        gcpKmsConnectorDTO.setHarnessManaged(harnessManaged);
        connectorInfo = ConnectorInfoDTO.builder()
                            .connectorType(GCP_KMS)
                            .identifier(secretManagerConfigDTO.getIdentifier())
                            .name(secretManagerConfigDTO.getName())
                            .orgIdentifier(secretManagerConfigDTO.getOrgIdentifier())
                            .projectIdentifier(secretManagerConfigDTO.getProjectIdentifier())
                            .description(secretManagerConfigDTO.getDescription())
                            .connectorConfig(gcpKmsConnectorDTO)
                            .build();
        break;
      case KMS:
        AwsKmsConfigDTO configDTO = (AwsKmsConfigDTO) secretManagerConfigDTO;
        SecretRefData kmsArnRefData = null;
        SecretRefData accessRefData = null;
        SecretRefData secretRefData = null;
        if (!referenceGlobalKms) {
          if (null != connectorInfoDTO) {
            String region = ((AwsKmsConnectorDTO) connectorInfoDTO.getConnectorConfig()).getRegion();
            configDTO.getBaseAwsKmsConfigDTO().setRegion(region);
          }
          identifier = secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING + BaseAwsKmsConfigDTOKeys.kmsArn;
          kmsArnRefData =
              populateSecretRefData(identifier, configDTO.getBaseAwsKmsConfigDTO().getKmsArn().toCharArray(),
                  secretScope, accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);

          if (configDTO.getBaseAwsKmsConfigDTO().getCredentialType() == MANUAL_CONFIG) {
            identifier =
                secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING + AwsKmsManualCredentialConfigKeys.accessKey;
            accessRefData = populateSecretRefData(identifier,
                ((AwsKmsManualCredentialConfig) configDTO.getBaseAwsKmsConfigDTO().getCredential())
                    .getAccessKey()
                    .toCharArray(),
                secretScope, accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);
            identifier =
                secretManagerConfigDTO.getIdentifier() + CONNECTOR_STRING + AwsKmsManualCredentialConfigKeys.secretKey;
            secretRefData = populateSecretRefData(identifier,
                ((AwsKmsManualCredentialConfig) configDTO.getBaseAwsKmsConfigDTO().getCredential())
                    .getSecretKey()
                    .toCharArray(),
                secretScope, accountIdentifier, projectIdentifier, orgIdentifier, harnessManaged);
          }
        }
        AwsKmsConnectorDTO awsKmsConnectorDTO =
            AwsKmsMappingHelper.configDTOToConnectorDTO(configDTO, kmsArnRefData, accessRefData, secretRefData);

        awsKmsConnectorDTO.setHarnessManaged(harnessManaged);
        connectorInfo = ConnectorInfoDTO.builder()
                            .connectorType(AWS_KMS)
                            .identifier(secretManagerConfigDTO.getIdentifier())
                            .name(secretManagerConfigDTO.getName())
                            .orgIdentifier(secretManagerConfigDTO.getOrgIdentifier())
                            .projectIdentifier(secretManagerConfigDTO.getProjectIdentifier())
                            .description(secretManagerConfigDTO.getDescription())
                            .connectorConfig(awsKmsConnectorDTO)
                            .build();
        break;
      case LOCAL:
        LocalConnectorDTO localConnectorDTO =
            LocalConnectorDTO.builder().isDefault(secretManagerConfigDTO.isDefault()).build();
        localConnectorDTO.setHarnessManaged(harnessManaged);
        connectorInfo = ConnectorInfoDTO.builder()
                            .connectorType(LOCAL)
                            .identifier(secretManagerConfigDTO.getIdentifier())
                            .name(secretManagerConfigDTO.getName())
                            .orgIdentifier(secretManagerConfigDTO.getOrgIdentifier())
                            .projectIdentifier(secretManagerConfigDTO.getProjectIdentifier())
                            .description(secretManagerConfigDTO.getDescription())
                            .connectorConfig(localConnectorDTO)
                            .build();
        break;
      default:
        throw new SecretManagementException(
            ErrorCode.SECRET_MANAGEMENT_ERROR, "Unsupported Secret Manager", WingsException.USER);
    }
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  private Scope getSecretScope(@OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    Scope secretScope = Scope.ACCOUNT;
    if (isNotEmpty(projectIdentifier)) {
      secretScope = Scope.PROJECT;
    } else if (isNotEmpty(orgIdentifier)) {
      secretScope = Scope.ORG;
    }
    return secretScope;
  }
  @NotNull
  private SecretRefData populateSecretRefData(String secretIdentifier, char[] decryptedValue, Scope secretScope,
      String accountIdentifier, String projectIdentifier, String orgIdentifier, boolean isAccountDefault) {
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().value(String.valueOf(decryptedValue)).valueType(ValueType.Inline).build();
    if (!isAccountDefault) {
      secretTextSpecDTO.setSecretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER);
    } else {
      secretTextSpecDTO.setSecretManagerIdentifier(this.UUID);
    }
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .identifier(secretIdentifier)
                                  .name(secretIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .spec(secretTextSpecDTO)
                                  .build();
    Optional<SecretResponseWrapper> secretOptional =
        secretCrudService.get(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
    if (secretOptional.isPresent()) {
      return new SecretRefData(secretIdentifier, secretScope, decryptedValue);
    }
    secretCrudService.create(accountIdentifier, secretDTOV2);
    return new SecretRefData(secretIdentifier, secretScope, decryptedValue);
  }

  private String getDefaultHarnessSecretManagerName(EncryptionType encryptionType) {
    switch (encryptionType) {
      case GCP_KMS:
        return "Harness Secrets Manager Google KMS";
      case KMS:
        return "Harness Secrets Manager AWS KMS";
      case LOCAL:
        return "Harness Vault";
      default:
        throw new SecretManagementException(
            ErrorCode.SECRET_MANAGEMENT_ERROR, "Unsupported Harness Secret Manager", WingsException.USER);
    }
  }
}
