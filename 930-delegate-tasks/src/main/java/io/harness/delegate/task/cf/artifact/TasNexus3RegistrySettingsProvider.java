/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasNexus3RegistrySettingsProvider extends AbstractTasRegistrySettingsProvider {
  @Inject DecryptionHelper decryptionHelper;
  @Override
  public TasArtifactCreds getContainerSettings(
      TasContainerArtifactConfig artifactConfig, DecryptionHelper decryptionHelper) {
    NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, nexusConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

    String registryUrl = !isBlank(artifactConfig.getRegistryHostname()) ? artifactConfig.getRegistryHostname()
                                                                        : nexusConnectorDTO.getNexusServerUrl();

    if (NexusAuthType.USER_PASSWORD == nexusConnectorDTO.getAuth().getAuthType()) {
      NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO =
          (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();

      String decryptedSecret = new String(nexusUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue());
      String username = nexusUsernamePasswordAuthDTO.getUsername();

      validateSettings(artifactConfig, registryUrl, username, decryptedSecret);
      return populateDockerSettings(registryUrl, username, decryptedSecret);
    }

    validateSettings(artifactConfig, registryUrl);
    return populateDockerSettings(registryUrl, null, null);
  }
}
