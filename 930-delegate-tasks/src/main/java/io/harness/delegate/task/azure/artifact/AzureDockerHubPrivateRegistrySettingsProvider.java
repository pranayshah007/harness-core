/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;

import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureDockerHubPrivateRegistrySettingsProvider extends AbstractAzureRegistrySettingsProvider {
  @Override
  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) artifactConfig.getConnectorConfig();
    DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
        (DockerUserNamePasswordDTO) dockerConnectorDTO.getAuth().getCredentials();

    String dockerRegistryUrl = dockerConnectorDTO.getDockerRegistryUrl();
    String decryptedSecret = new String(dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue());
    String username = dockerUserNamePasswordDTO.getUsername();
    validateSettings(artifactConfig, dockerRegistryUrl, username, decryptedSecret);
    return populateDockerSettingMap(dockerRegistryUrl, username, decryptedSecret);
  }
}
