/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
public enum ArtifactType {
  ECR("ECR"),
  ARTIFACTORY("ARTIFACTORY"),
  S3("S3");

  private String artifactType;

  ArtifactType(String artifactType) {
    this.artifactType = artifactType;
  }

  public String getArtifactType() {
    return this.artifactType;
  }

  public static ArtifactType fromString(String type) {
    for (ArtifactType inputType : ArtifactType.values()) {
      if (inputType.artifactType.equalsIgnoreCase(type)) {
        return inputType;
      }
    }
    return null;
  }
}
