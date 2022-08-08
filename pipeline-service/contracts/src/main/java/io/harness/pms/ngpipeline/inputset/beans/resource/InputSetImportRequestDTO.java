/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(value = PIPELINE)
public class InputSetImportRequestDTO {
  @Schema(description = "Name of the Input Set to be imported. This will override the Name in the YAML on Git",
      required = true)
  String inputSetName;
  @Schema(description =
              "Description of the Input Set to be imported. This will override the Description in the YAML on Git")
  String inputSetDescription;
}
