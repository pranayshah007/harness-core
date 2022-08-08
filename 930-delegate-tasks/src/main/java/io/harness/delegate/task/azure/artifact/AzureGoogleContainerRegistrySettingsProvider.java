/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureGoogleContainerRegistrySettingsProvider extends AbstractAzureRegistrySettingsProvider {
  private static final String GCR_USERNAME = "_json_key";

  @Override
  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) artifactConfig.getConnectorConfig();

    if (GcpCredentialType.MANUAL_CREDENTIALS != gcpConnectorDTO.getCredential().getGcpCredentialType()) {
      throw new InvalidRequestException(format("Invalid credentials type, %s are not supported",
          gcpConnectorDTO.getCredential().getGcpCredentialType().toString()));
    }

    String registryUrl = artifactConfig.getRegistryHostname();
    GcpManualDetailsDTO gcpManualDetailsDTO = (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
    String decryptedSecret = new String(gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue());

    validateSettings(artifactConfig, registryUrl, GCR_USERNAME, decryptedSecret);
    return populateDockerSettingMap(registryUrl, GCR_USERNAME, decryptedSecret);
  }
}
