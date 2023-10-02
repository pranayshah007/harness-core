/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@Builder
@OwnedBy(CDP)
public class TasContainerArtifactConfig implements TasArtifactConfig {
  private ConnectorConfigDTO connectorConfig;
  private TasArtifactRegistryType registryType;
  private String image;
  private String tag;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private String registryHostname;
  private String region;

  @Override
  public TasArtifactType getArtifactType() {
    return TasArtifactType.CONTAINER;
  }
}
