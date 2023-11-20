/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifactBundle;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.pcf.artifact.TasArtifactRequestDetails;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@Builder
@OwnedBy(CDP)
public class PackageArtifactConfig {
  private ConnectorConfigDTO connectorConfig;
  private ArtifactSourceType sourceType;
  // Ideally this class should be generic and outside pcf.artifact so that it can be used for all the modules but to
  // avoid any issue are using it at it is. Please treat TasArtifactRequestDetails as generic class
  @Expression(ALLOW_SECRETS) private TasArtifactRequestDetails artifactDetails;
  private List<EncryptedDataDetail> encryptedDataDetails;

  public ArtifactType getArtifactType() {
    return ArtifactType.PACKAGE;
  }
}
