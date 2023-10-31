/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.spotconnector.SpotCredentialType.PERMANENT_TOKEN;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO.SpotConnectorDTOBuilder;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDP)
public class SpotConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    List<String> secrets = new ArrayList<>();
    secrets.add(((SpotInstConfig) settingAttribute.getValue()).getEncryptedSpotInstToken());
    return secrets;
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.SPOT;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    SpotInstConfig spotInstConfig = (SpotInstConfig) settingAttribute.getValue();

    SpotConnectorDTOBuilder builder = SpotConnectorDTO.builder();
    SpotCredentialDTO spotCredentialDTO = getPermanentTokenCredentials(spotInstConfig, migratedEntities);

    return builder.executeOnDelegate(true).credential(spotCredentialDTO).build();
  }

  private SpotCredentialDTO getPermanentTokenCredentials(
      SpotInstConfig spotInstConfig, Map<CgEntityId, NGYamlFile> migratedEntities) {
    SecretRefData tokenRefData =
        MigratorUtility.getSecretRef(migratedEntities, spotInstConfig.getEncryptedSpotInstToken());
    SpotPermanentTokenConfigSpecDTO configSpecDTO =
        SpotPermanentTokenConfigSpecDTO.builder()
            .spotAccountId(String.valueOf(spotInstConfig.getSpotInstAccountId()))
            .apiTokenRef(tokenRefData)
            .build();
    return getSpotCredentialDTO(configSpecDTO);
  }

  private SpotCredentialDTO getSpotCredentialDTO(SpotPermanentTokenConfigSpecDTO configSpecDTO) {
    return SpotCredentialDTO.builder().spotCredentialType(PERMANENT_TOKEN).config(configSpecDTO).build();
  }
}
