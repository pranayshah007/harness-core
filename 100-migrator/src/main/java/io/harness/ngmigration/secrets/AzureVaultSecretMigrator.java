/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO.AzureKeyVaultConnectorDTOBuilder;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.CustomSecretRequestWrapper;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.AzureVaultConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class AzureVaultSecretMigrator implements SecretMigrator {
  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    String value =
        StringUtils.isNotBlank(encryptedData.getPath()) ? encryptedData.getPath() : encryptedData.getEncryptionKey();
    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .valueType(ValueType.Reference)
                  .value(value)
                  .secretManagerIdentifier(secretManagerIdentifier)
                  .build());
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    AzureVaultConfig azureVaultConfig = (AzureVaultConfig) secretManagerConfig;

    List<SecretDTOV2> secrets = new ArrayList<>();
    Scope scope = MigratorUtility.getDefaultScope(inputDTO,
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(azureVaultConfig.getUuid()).build(),
        Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    String secretKey = String.format("migratedAzureSecret_%s",
        MigratorUtility.generateIdentifier(azureVaultConfig.getName(), inputDTO.getIdentifierCaseFormat()));
    NgEntityDetail secretEntityDetail = NgEntityDetail.builder()
                                            .identifier(secretKey)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .build();
    SecretDTOV2 secretDTO = getSecretDTO(azureVaultConfig, inputDTO, secretKey, azureVaultConfig.getSecretKey());
    secrets.add(secretDTO);

    AzureKeyVaultConnectorDTOBuilder connectorDTO =
        AzureKeyVaultConnectorDTO.builder()
            .azureEnvironmentType(azureVaultConfig.getAzureEnvironmentType())
            .subscription(azureVaultConfig.getSubscription())
            .tenantId(azureVaultConfig.getTenantId())
            .clientId(azureVaultConfig.getClientId())
            .secretKey(SecretRefData.builder()
                           .scope(MigratorUtility.getScope(secretEntityDetail))
                           .identifier(secretKey)
                           .build())
            .vaultName(azureVaultConfig.getVaultName())
            .delegateSelectors(azureVaultConfig.getDelegateSelectors());

    return SecretManagerCreatedDTO.builder()
        .connector(connectorDTO.build())
        .secrets(secrets.stream()
                     .map(secretDTOV2 -> CustomSecretRequestWrapper.builder().secret(secretDTOV2).build())
                     .collect(Collectors.toList()))
        .build();
  }
}
