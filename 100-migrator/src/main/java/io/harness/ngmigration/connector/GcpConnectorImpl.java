/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO.builder;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.MANUAL_CREDENTIALS;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO.GcpConnectorDTOBuilder;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.MigratorUtility;

import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GcpConnectorImpl implements BaseConnector {
  @Override
  public String getSecretId(SettingAttribute settingAttribute) {
    return ((GcpConfig) settingAttribute.getValue()).getEncryptedServiceAccountKeyFileContent();
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.GCP;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    GcpConfig clusterConfig = (GcpConfig) settingAttribute.getValue();
    Set<String> delegateSelectors = new HashSet<>();
    if (clusterConfig.getDelegateSelectors() != null) {
      delegateSelectors.addAll(clusterConfig.getDelegateSelectors());
    }
    GcpConnectorDTOBuilder builder = builder().delegateSelectors(delegateSelectors);
    GcpConnectorCredentialDTO credentialDTO;
    if (clusterConfig.isUseDelegateSelectors()) {
      credentialDTO = GcpConnectorCredentialDTO.builder().gcpCredentialType(INHERIT_FROM_DELEGATE).build();
    } else {
      SecretRefData secretRefData = new SecretRefData(MigratorUtility.getIdentifierWithScope(
          migratedEntities
              .get(CgEntityId.builder()
                       .type(NGMigrationEntityType.SECRET)
                       .id(((GcpConfig) settingAttribute.getValue()).getEncryptedServiceAccountKeyFileContent())
                       .build())
              .getNgEntityDetail()));
      credentialDTO = GcpConnectorCredentialDTO.builder()
                          .gcpCredentialType(MANUAL_CREDENTIALS)
                          .config(GcpManualDetailsDTO.builder().secretKeyRef(secretRefData).build())
                          .build();
    }

    return builder.credential(credentialDTO).build();
  }
}
