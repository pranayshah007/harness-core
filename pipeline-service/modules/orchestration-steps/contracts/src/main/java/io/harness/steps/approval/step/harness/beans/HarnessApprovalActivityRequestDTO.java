/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("HarnessApprovalActivityRequest")
@Schema(name = "HarnessApprovalActivityRequest", description = "Details of approval activity requested")
public class HarnessApprovalActivityRequestDTO {
  @Parameter(description = "Approval activity action")
  @Schema(description = "Approval activity action")
  @NotNull
  HarnessApprovalAction action;

  @Parameter(description = "Custom data to capture at the time of approval")
  @Schema(description = "Custom data to capture at the time of approval")
  List<ApproverInput> approverInputs;
  @Parameter(description = "Approval activity with the comment")
  @Schema(description = "Approval activity with the comment")
  String comments;

  @Parameter(description = "auto approval parameter") @ApiModelProperty(hidden = true) boolean autoApprove;
}
