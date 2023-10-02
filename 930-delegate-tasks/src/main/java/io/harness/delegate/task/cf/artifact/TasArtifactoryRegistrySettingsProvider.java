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
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;

import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasArtifactoryRegistrySettingsProvider extends AbstractTasRegistrySettingsProvider {
  @Override
  public TasArtifactCreds getContainerSettings(
      TasContainerArtifactConfig artifactConfig, DecryptionHelper decryptionHelper) {
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) artifactConfig.getConnectorConfig();
    decryptEntity(
        decryptionHelper, artifactoryConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());
    String dockerRegistryUrl = !isBlank(artifactConfig.getRegistryHostname())
        ? artifactConfig.getRegistryHostname()
        : artifactoryConnectorDTO.getArtifactoryServerUrl();
    if (ArtifactoryAuthType.USER_PASSWORD == artifactoryConnectorDTO.getAuth().getAuthType()) {
      ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
          (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnectorDTO.getAuth().getCredentials();
      String decryptedSecret = new String(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue());
      String username = artifactoryUsernamePasswordAuthDTO.getUsername();
      validateSettings(artifactConfig, dockerRegistryUrl, username, decryptedSecret);
      return populateDockerSettings(dockerRegistryUrl, username, decryptedSecret);
    }

    validateSettings(artifactConfig, dockerRegistryUrl);
    return populateDockerSettings(dockerRegistryUrl, null, null);
  }
}
