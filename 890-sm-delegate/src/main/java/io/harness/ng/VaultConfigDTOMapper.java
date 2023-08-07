/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.secretmanagerclient.dto.VaultConfigDTO.VaultConfigDTOBuilder;
import static io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO.VaultConfigUpdateDTOBuilder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO;
import io.harness.security.encryption.EncryptionType;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class VaultConfigDTOMapper {
  public static VaultConfigUpdateDTO getVaultConfigUpdateDTO(
      ConnectorDTO connectorRequestDTO, VaultConnectorDTO vaultConnectorDTO) {
    vaultConnectorDTO.validate();
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();

    VaultConfigUpdateDTOBuilder<?, ?> builder =
        VaultConfigUpdateDTO.builder()
            .basePath(vaultConnectorDTO.getBasePath())
            .namespace(vaultConnectorDTO.getNamespace())
            .useVaultAgent(vaultConnectorDTO.isUseVaultAgent())
            .sinkPath(vaultConnectorDTO.getSinkPath())
            .vaultUrl(vaultConnectorDTO.getVaultUrl())
            .isReadOnly(vaultConnectorDTO.isReadOnly())
            .renewalIntervalMinutes(vaultConnectorDTO.getRenewalIntervalMinutes())
            .secretEngineName(vaultConnectorDTO.getSecretEngineName())
            .secretEngineVersion(vaultConnectorDTO.getSecretEngineVersion())
            .appRoleId(vaultConnectorDTO.getAppRoleId())
            .isDefault(false)
            .name(connector.getName())
            .encryptionType(EncryptionType.VAULT)
            .tags(connector.getTags())
            .description(connector.getDescription())
            .useAwsIam(vaultConnectorDTO.isUseAwsIam())
            .awsRegion(vaultConnectorDTO.getAwsRegion())
            .vaultAwsIamRole(vaultConnectorDTO.getVaultAwsIamRole())
            .useK8sAuth(vaultConnectorDTO.isUseK8sAuth())
            .vaultK8sAuthRole(vaultConnectorDTO.getVaultK8sAuthRole())
            .serviceAccountTokenPath(vaultConnectorDTO.getServiceAccountTokenPath())
            .k8sAuthEndpoint(vaultConnectorDTO.getK8sAuthEndpoint())
            .renewAppRoleToken(vaultConnectorDTO.isRenewAppRoleToken());

    if (null != vaultConnectorDTO.getHeaderAwsIam()
        && null != vaultConnectorDTO.getHeaderAwsIam().getDecryptedValue()) {
      builder.xVaultAwsIamServerId(String.valueOf(vaultConnectorDTO.getHeaderAwsIam().getDecryptedValue()));
    }

    if (null != vaultConnectorDTO.getAuthToken() && null != vaultConnectorDTO.getAuthToken().getDecryptedValue()) {
      builder.authToken(String.valueOf(vaultConnectorDTO.getAuthToken().getDecryptedValue()));
    }
    if (null != vaultConnectorDTO.getSecretId() && null != vaultConnectorDTO.getSecretId().getDecryptedValue()) {
      builder.secretId(String.valueOf(vaultConnectorDTO.getSecretId().getDecryptedValue()));
    }
    return builder.build();
  }

  public static VaultConfigDTO getVaultConfigDTO(
      String accountIdentifier, ConnectorDTO connectorRequestDTO, VaultConnectorDTO vaultConnectorDTO) {
    vaultConnectorDTO.validate();
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();

    VaultConfigDTOBuilder<?, ?> builder = VaultConfigDTO.builder()
                                              .basePath(vaultConnectorDTO.getBasePath())
                                              .namespace(vaultConnectorDTO.getNamespace())
                                              .useVaultAgent(vaultConnectorDTO.isUseVaultAgent())
                                              .sinkPath(vaultConnectorDTO.getSinkPath())
                                              .vaultUrl(vaultConnectorDTO.getVaultUrl())
                                              .isReadOnly(vaultConnectorDTO.isReadOnly())
                                              .renewalIntervalMinutes(vaultConnectorDTO.getRenewalIntervalMinutes())
                                              .secretEngineName(vaultConnectorDTO.getSecretEngineName())
                                              .secretEngineVersion(vaultConnectorDTO.getSecretEngineVersion())
                                              .appRoleId(vaultConnectorDTO.getAppRoleId())
                                              .isDefault(vaultConnectorDTO.isDefault())
                                              .encryptionType(EncryptionType.VAULT)
                                              .secretEngineVersion(vaultConnectorDTO.getSecretEngineVersion())
                                              .delegateSelectors(vaultConnectorDTO.getDelegateSelectors())
                                              .name(connector.getName())
                                              .accountIdentifier(accountIdentifier)
                                              .orgIdentifier(connector.getOrgIdentifier())
                                              .projectIdentifier(connector.getProjectIdentifier())
                                              .tags(connector.getTags())
                                              .identifier(connector.getIdentifier())
                                              .description(connector.getDescription())
                                              .useAwsIam(vaultConnectorDTO.isUseAwsIam())
                                              .awsRegion(vaultConnectorDTO.getAwsRegion())
                                              .vaultAwsIamRole(vaultConnectorDTO.getVaultAwsIamRole())
                                              .useK8sAuth(vaultConnectorDTO.isUseK8sAuth())
                                              .vaultK8sAuthRole(vaultConnectorDTO.getVaultK8sAuthRole())
                                              .serviceAccountTokenPath(vaultConnectorDTO.getServiceAccountTokenPath())
                                              .k8sAuthEndpoint(vaultConnectorDTO.getK8sAuthEndpoint())
                                              .renewAppRoleToken(vaultConnectorDTO.isRenewAppRoleToken())
                                              .enableCache(vaultConnectorDTO.isEnableCache());

    if (null != vaultConnectorDTO.getHeaderAwsIam()
        && null != vaultConnectorDTO.getHeaderAwsIam().getDecryptedValue()) {
      builder.xVaultAwsIamServerId(String.valueOf(vaultConnectorDTO.getHeaderAwsIam().getDecryptedValue()));
    }

    if (null != vaultConnectorDTO.getAuthToken() && null != vaultConnectorDTO.getAuthToken().getDecryptedValue()) {
      builder.authToken(String.valueOf(vaultConnectorDTO.getAuthToken().getDecryptedValue()));
    }
    if (null != vaultConnectorDTO.getSecretId() && null != vaultConnectorDTO.getSecretId().getDecryptedValue()) {
      builder.secretId(String.valueOf(vaultConnectorDTO.getSecretId().getDecryptedValue()));
    }

    return builder.build();
  }
}
