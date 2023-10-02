/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.dtos;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "This contains details regarding Artifactory artifact")
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryDockerBuildDetailsDTO implements ArtifactoryBuildDetailsDTO {
  @Schema(description = "This is the artifact tag value") String tag;
  @Schema(description = "This is the URL to the artifact") String buildUrl;
  @Schema(description = "This is map of the metadata details for the artifact (like artifact pull URL...")
  Map<String, String> metadata;
  @Schema(description = "This is map of artifact labels") Map<String, String> labels;
  @Schema(description = "This is the artifact image path") String imagePath;
}
