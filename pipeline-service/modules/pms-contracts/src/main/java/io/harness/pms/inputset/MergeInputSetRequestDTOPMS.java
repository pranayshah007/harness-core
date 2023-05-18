/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("MergeInputSetRequest")
@Schema(name = "MergeInputSetRequest", description = "Contains list of Input Set references and Stage Ids")
public class MergeInputSetRequestDTOPMS {
  @Schema(description = "List of Input Set References to be merged") List<String> inputSetReferences;
  @Schema(
      description =
          "This is a boolean value that indicates if the response must contain the YAML for the merged Pipeline. The default value is False.")
  boolean withMergedPipelineYaml;
  @Schema(description = "List of Stage Ids. Input Sets corresponding to these Ids will be merged.")
  List<String> stageIdentifiers;
  @Schema(description =
              "Runtime Input Yaml needed to be merged into the result of the merged Yaml of the inputSetReferences")
  String lastYamlToMerge;

  @Schema(description = "To be set true if we want to set user flow in thread context", hidden = true)
  boolean getOnlyFileContent;
}
