/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;

import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureArtifactoryRegistrySettingsProvider extends AbstractAzureRegistrySettingsProvider {
  @Override
  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) artifactConfig.getConnectorConfig();

    String dockerRegistryUrl = !isBlank(artifactConfig.getRegistryHostname())
        ? artifactConfig.getRegistryHostname()
        : artifactoryConnectorDTO.getArtifactoryServerUrl();
    if (ArtifactoryAuthType.USER_PASSWORD == artifactoryConnectorDTO.getAuth().getAuthType()) {
      ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
          (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnectorDTO.getAuth().getCredentials();
      String decryptedSecret = new String(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue());
      String username = artifactoryUsernamePasswordAuthDTO.getUsername();
      validateSettings(artifactConfig, dockerRegistryUrl, username, decryptedSecret);
      return populateDockerSettingMap(dockerRegistryUrl, username, decryptedSecret);
    }

    validateSettings(artifactConfig, dockerRegistryUrl);
    return populateDockerSettingMap(dockerRegistryUrl);
  }
}
