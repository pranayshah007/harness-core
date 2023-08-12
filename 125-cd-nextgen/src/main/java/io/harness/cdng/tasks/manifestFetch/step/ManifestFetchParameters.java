/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.tasks.manifestFetch.step;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Value
@Builder
@TypeAlias("manifestFetchParameters")
@RecasterAlias("io.harness.cdng.tasks.manifestFetch.step.ManifestFetchParameters")
public class ManifestFetchParameters implements StepParameters {
  private List<ManifestAttributes> serviceSpecManifestAttributes;
  private List<ManifestAttributes> overridesManifestAttributes;
  private boolean fetchValuesOnly;
}
