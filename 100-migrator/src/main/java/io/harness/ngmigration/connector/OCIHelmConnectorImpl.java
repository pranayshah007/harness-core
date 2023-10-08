/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO.OciHelmAuthenticationDTOBuilder;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.settings.helm.OciHelmRepoConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDP)
public class OCIHelmConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((OciHelmRepoConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.OCI_HELM_REPO;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    OciHelmRepoConfig clusterConfig = (OciHelmRepoConfig) settingAttribute.getValue();

    OciHelmAuthenticationDTOBuilder authBuilder = OciHelmAuthenticationDTO.builder();
    authBuilder.authType(OciHelmAuthType.ANONYMOUS);
    String username = clusterConfig.getUsername();
    String password = clusterConfig.getEncryptedPassword();
    if (isNotEmpty(username) && isNotEmpty(password)) {
      authBuilder.authType(OciHelmAuthType.USER_PASSWORD);
      SecretRefData passwordRef = MigratorUtility.getSecretRef(migratedEntities, password);
      authBuilder.credentials(
          OciHelmUsernamePasswordDTO.builder().username(clusterConfig.getUsername()).passwordRef(passwordRef).build());
    }

    return OciHelmConnectorDTO.builder().helmRepoUrl(clusterConfig.getChartRepoUrl()).auth(authBuilder.build()).build();
  }
}
