/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO.builder;
import static io.harness.ngmigration.utils.MigratorUtility.checkIfStringIsValidUrl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO.TasConnectorDTOBuilder;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO.TasManualDetailsDTOBuilder;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.PcfConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDP)
public class PcfConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    List<String> secrets = new ArrayList<>();
    PcfConfig pcfConfig = (PcfConfig) settingAttribute.getValue();
    if (pcfConfig.isUseEncryptedUsername()) {
      secrets.add(pcfConfig.getEncryptedUsername());
    }
    secrets.add(pcfConfig.getEncryptedPassword());
    return secrets;
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.TAS;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    PcfConfig pcfConfig = (PcfConfig) settingAttribute.getValue();
    TasConnectorDTOBuilder builder = builder();

    TasManualDetailsDTOBuilder credsBuilder = TasManualDetailsDTO.builder();

    if (checkIfStringIsValidUrl(pcfConfig.getEndpointUrl())) {
      credsBuilder.endpointUrl(pcfConfig.getEndpointUrl());
    } else {
      credsBuilder.endpointUrl("http://" + pcfConfig.getEndpointUrl());
    }

    if (pcfConfig.isUseEncryptedUsername()) {
      credsBuilder.usernameRef(MigratorUtility.getSecretRef(migratedEntities, pcfConfig.getEncryptedUsername()));
    } else {
      credsBuilder.username(String.valueOf(pcfConfig.getUsername()));
    }

    credsBuilder.passwordRef(MigratorUtility.getSecretRef(migratedEntities, pcfConfig.getEncryptedPassword()));

    TasCredentialDTO credentialDTO =
        TasCredentialDTO.builder().type(TasCredentialType.MANUAL_CREDENTIALS).spec(credsBuilder.build()).build();

    return builder.executeOnDelegate(true).credential(credentialDTO).build();
  }
}
