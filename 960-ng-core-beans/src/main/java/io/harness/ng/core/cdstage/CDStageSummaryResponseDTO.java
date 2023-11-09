/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.cdstage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cd.CDStageSummaryConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("CDStageSummaryResponseDTO")
@Schema(name = "CDStageSummaryResponseDTO", description = "Summary of CD stage")
public class CDStageSummaryResponseDTO {
  @JsonProperty(CDStageSummaryConstants.SERVICE)
  @Schema(description = CDStageSummaryConstants.SERVICE)
  @Nullable
  String service;

  @JsonProperty(CDStageSummaryConstants.ENVIRONMENT)
  @Schema(description = CDStageSummaryConstants.ENVIRONMENT)
  @Nullable
  String environment;

  @JsonProperty(CDStageSummaryConstants.INFRA_DEFINITION)
  @Schema(description = CDStageSummaryConstants.INFRA_DEFINITION)
  @Nullable
  String infra;
  // Maintain union of fields from subtypes of io.harness.cdng.creator.plan.stage.DeploymentStageDetailsInfo
}
