/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO.builder;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;

import software.wings.beans.AzureConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AzureConnectorImpl implements BaseConnector {
  @Override
  public String getSecretId(SettingAttribute settingAttribute) {
    return ((AzureConfig) settingAttribute.getValue()).getEncryptedKey();
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.AZURE;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(SettingAttribute settingAttribute, Set<CgEntityId> childEntities,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    List<CgEntityId> cgEntityIdList =
        childEntities.stream()
            .filter(cgEntityId -> cgEntityId.getType().equals(NGMigrationEntityType.SECRET))
            .collect(Collectors.toList());
    if (isEmpty(cgEntityIdList)) {
      throw new UnsupportedOperationException("Unsupported Operation: Secret not found in migration entities");
    }
    SecretRefData secretRefData = new SecretRefData(
        MigratorUtility.getScope(migratedEntities.get(cgEntityIdList.get(0))) + this.getSecretId(settingAttribute));
    AzureConfig clusterConfig = (AzureConfig) settingAttribute.getValue();
    return builder()
        .azureEnvironmentType(clusterConfig.getAzureEnvironmentType())
        .executeOnDelegate(true)
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                        .config(AzureManualDetailsDTO.builder()
                                    .clientId(clusterConfig.getClientId())
                                    .tenantId(clusterConfig.getTenantId())
                                    .authDTO(AzureAuthDTO.builder()
                                                 .azureSecretType(AzureSecretType.SECRET_KEY)
                                                 .credentials(
                                                     AzureClientSecretKeyDTO.builder().secretKey(secretRefData).build())
                                                 .build())
                                    .build())
                        .build())
        .build();
  }
}
