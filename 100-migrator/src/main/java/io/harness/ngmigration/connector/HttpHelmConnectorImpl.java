/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO.builder;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO.HttpHelmAuthenticationDTOBuilder;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDP)
public class HttpHelmConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((HttpHelmRepoConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.HTTP_HELM_REPO;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    HttpHelmRepoConfig config = (HttpHelmRepoConfig) settingAttribute.getValue();

    HttpHelmAuthenticationDTOBuilder authBuilder = HttpHelmAuthenticationDTO.builder();
    authBuilder.authType(HttpHelmAuthType.ANONYMOUS);
    String username = config.getUsername();
    String secretId = config.getEncryptedPassword();
    if (isNotEmpty(username) && isNotEmpty(secretId)) {
      authBuilder.authType(HttpHelmAuthType.USER_PASSWORD);
      authBuilder.credentials(HttpHelmUsernamePasswordDTO.builder()
                                  .username(username)
                                  .passwordRef(MigratorUtility.getSecretRef(migratedEntities, secretId))
                                  .build());
    }
    HttpHelmAuthenticationDTO authDTO = authBuilder.build();

    return builder().helmRepoUrl(config.getChartRepoUrl()).auth(authDTO).build();
  }
}
