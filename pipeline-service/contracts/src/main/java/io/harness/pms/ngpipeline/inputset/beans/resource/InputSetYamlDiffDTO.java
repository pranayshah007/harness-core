/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.pipeline.PipelineResourceConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetYamlDiff")
@Schema(name = "InputSetYamlDiff", description = "This contains the YAML diff required to fix an Input Set")
public class InputSetYamlDiffDTO {
  String oldYAML;
  String newYAML;
  @Schema(description = "Tells whether the Input Set provides any values after removing invalid fields")
  boolean isInputSetEmpty;
  @Schema(description = "Tells whether any Input Set can provide any new values") boolean noUpdatePossible;
  @Setter @NonFinal boolean yamlDiffPresent;
  @Schema(description = "List of references in an OverlayInputSet that exist but are invalid")
  List<String> invalidReferences;
  @Setter @NonFinal @Schema(description = PipelineResourceConstants.GIT_DETAILS_MESSAGE) EntityGitDetails gitDetails;
}
