/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifactBundle.ArtifactBundledArtifactType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Value
@Builder
public class ArtifactBundleStoreDTO {
  String manifestPath;
  String deployableUnitPath;
  ArtifactBundledArtifactType artifactBundleType;

  public ArtifactBundleStore toGitStoreConfig() {
    return ArtifactBundleStore.builder()
        .manifestPath(ParameterField.createValueField(manifestPath))
        .deployableUnitPath(ParameterField.createValueField(deployableUnitPath))
        .artifactBundleType(artifactBundleType)
        .build();
  }
}
