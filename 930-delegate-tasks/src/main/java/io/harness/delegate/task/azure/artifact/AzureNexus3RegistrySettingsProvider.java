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
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;

import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureNexus3RegistrySettingsProvider extends AbstractAzureRegistrySettingsProvider {
  @Override
  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) artifactConfig.getConnectorConfig();
    String registryUrl = !isBlank(artifactConfig.getRegistryHostname()) ? artifactConfig.getRegistryHostname()
                                                                        : nexusConnectorDTO.getNexusServerUrl();

    if (NexusAuthType.USER_PASSWORD == nexusConnectorDTO.getAuth().getAuthType()) {
      NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO =
          (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();

      String decryptedSecret = new String(nexusUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue());
      String username = nexusUsernamePasswordAuthDTO.getUsername();

      validateSettings(artifactConfig, registryUrl, username, decryptedSecret);
      return populateDockerSettingMap(registryUrl, username, decryptedSecret);
    }

    validateSettings(artifactConfig, registryUrl);
    return populateDockerSettingMap(registryUrl);
  }
}
