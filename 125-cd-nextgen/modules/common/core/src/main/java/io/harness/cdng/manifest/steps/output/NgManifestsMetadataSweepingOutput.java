/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps.output;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@JsonTypeName("NgManifestsMetadataSweepingOutput")
@TypeAlias("ngManifestSweepingOutput")
@RecasterAlias("io.harness.cdng.manifest.steps.NgManifestsMetadataSweepingOutput")
public class NgManifestsMetadataSweepingOutput implements ExecutionSweepingOutput {
  @NotNull Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap;
  @NotNull ServiceDefinitionType serviceDefinitionType;
  @NotNull String serviceIdentifier;
  String environmentIdentifier;

  // added for override v2
  Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride;
  List<ManifestConfigWrapper> svcManifests;

  // manifest configurations
  ManifestConfigurations manifestConfigurations;
}
