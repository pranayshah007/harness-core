/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.ami;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
@Schema(name = "AMIRequestBody", description = "This has details of the AMI Tags and Filters")
public class AMIRequestBody {
  @Schema(description = "Runtime input YAML") String runtimeInputYaml;

  @Schema(description = "Aws tags applicable to the AMI artifact") List<AMITag> tags;

  @Schema(description = "Filters for AMI artifact") List<AMIFilter> filters;
}
