/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AWS_KMS;
import static io.harness.delegate.beans.connector.ConnectorType.GCP_KMS;
import static io.harness.delegate.beans.connector.ConnectorType.LOCAL;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.ng.NextGenModule.CONNECTOR_DECORATOR_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.mappers.secretmanagermapper.AwsKmsMappingHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.localconnector.LocalConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.eraro.ErrorCode;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.DefaultOrganization;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.migration.NGSecretManagerMigration;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class HarnessSMManager {
  private final NGSecretManagerService ngSecretManagerService;
  private final ConnectorService secretManagerConnectorService;
  private final AccountOrgProjectValidator accountOrgProjectValidator;
  private final NGSecretManagerMigration ngSecretManagerMigration;

  @Inject
  public HarnessSMManager(NGSecretManagerService ngSecretManagerService,
      @Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService secretManagerConnectorService,
      NGSecretManagerMigration ngSecretManagerMigration, AccountOrgProjectValidator accountOrgProjectValidator) {
    this.ngSecretManagerService = ngSecretManagerService;
    this.secretManagerConnectorService = secretManagerConnectorService;
    this.accountOrgProjectValidator = accountOrgProjectValidator;
    this.ngSecretManagerMigration = ngSecretManagerMigration;
  }

  public void createGlobalSecretManager() {
    ngSecretManagerMigration.createGlobal(GLOBAL_ACCOUNT_ID, null, null, true);
  }

  @DefaultOrganization
  public void createHarnessSecretManager(
      String accountIdentifier, @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    if (isHarnessSecretManagerPresent(accountIdentifier, orgIdentifier, projectIdentifier)) {
      log.info(String.format(
          "Harness Secret Manager for accountIdentifier %s, orgIdentifier %s and projectIdentifier %s already present",
          accountIdentifier, orgIdentifier, projectIdentifier));
      return;
    }
    if (!accountOrgProjectValidator.isPresent(accountIdentifier, orgIdentifier, projectIdentifier)) {
      log.info(String.format(
          "Parent entity with accountIdentifier %s, orgIdentifier %s and projectIdentifier %s does not exist, skipping creation of Harness Secret Manager",
          accountIdentifier, orgIdentifier, projectIdentifier));
      return;
    }
    SecretManagerConfigDTO globalSecretManager = ngSecretManagerService.getGlobalSecretManager(accountIdentifier);
    globalSecretManager.setIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER);
    globalSecretManager.setName(getDefaultHarnessSecretManagerName(globalSecretManager.getEncryptionType()));
    globalSecretManager.setProjectIdentifier(projectIdentifier);
    globalSecretManager.setOrgIdentifier(orgIdentifier);
    globalSecretManager.setDefault(true);

    Scope secretScope = getSecretScope(orgIdentifier, projectIdentifier);

    ConnectorDTO connectorDTO =
        getConnectorRequestDTO(globalSecretManager, secretScope, accountIdentifier, projectIdentifier, orgIdentifier);
    secretManagerConnectorService.create(connectorDTO, accountIdentifier);
    log.info(String.format("Created Harness Secret Manager for account- %s org- %s project- %s", accountIdentifier,
        orgIdentifier, projectIdentifier));
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

  @DefaultOrganization
  private boolean isHarnessSecretManagerPresent(
      String accountIdentifier, @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    return secretManagerConnectorService
        .get(accountIdentifier, orgIdentifier, projectIdentifier, HARNESS_SECRET_MANAGER_IDENTIFIER)
        .isPresent();
  }

  @DefaultOrganization
  public boolean deleteHarnessSecretManager(
      String accountIdentifier, @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    return secretManagerConnectorService.delete(
        accountIdentifier, orgIdentifier, projectIdentifier, HARNESS_SECRET_MANAGER_IDENTIFIER, false);
  }

  public ConnectorDTO getConnectorRequestDTO(SecretManagerConfigDTO secretManagerConfigDTO, Scope secretScope,
      String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    ConnectorInfoDTO connectorInfo;
    switch (secretManagerConfigDTO.getEncryptionType()) {
      case GCP_KMS:
        GcpKmsConfigDTO gcpKmsConfig = (GcpKmsConfigDTO) secretManagerConfigDTO;

        GcpKmsConnectorDTO gcpKmsConnectorDTO =
            GcpKmsConnectorDTO.builder().isDefault(secretManagerConfigDTO.isDefault()).build();
        gcpKmsConnectorDTO.setHarnessManaged(true);
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
        AwsKmsConnectorDTO awsKmsConnectorDTO =
            AwsKmsMappingHelper.configDTOToConnectorDTO(configDTO, null, null, null);
        awsKmsConnectorDTO.setHarnessManaged(true);
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
        localConnectorDTO.setHarnessManaged(true);
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

  private String getDefaultHarnessSecretManagerName(EncryptionType encryptionType) {
    switch (encryptionType) {
      case GCP_KMS:
        return "Harness Built-in Secret Manager";
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
