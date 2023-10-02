/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasGoogleContainerRegistrySettingsProvider extends AbstractTasRegistrySettingsProvider {
  private static final String GCR_USERNAME = "_json_key";
  @Inject DecryptionHelper decryptionHelper;

  @Override
  public TasArtifactCreds getContainerSettings(
      TasContainerArtifactConfig artifactConfig, DecryptionHelper decryptionHelper) {
    GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) artifactConfig.getConnectorConfig();

    if (GcpCredentialType.MANUAL_CREDENTIALS != gcpConnectorDTO.getCredential().getGcpCredentialType()) {
      throw new InvalidRequestException(format("Invalid credentials type, %s are not supported",
          gcpConnectorDTO.getCredential().getGcpCredentialType().toString()));
    }
    decryptEntity(decryptionHelper, gcpConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

    String registryUrl = artifactConfig.getRegistryHostname();
    GcpManualDetailsDTO gcpManualDetailsDTO = (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
    String decryptedSecret = new String(gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue());

    validateSettings(artifactConfig, registryUrl, GCR_USERNAME, decryptedSecret);
    return populateDockerSettings(registryUrl, GCR_USERNAME, decryptedSecret);
  }
}
