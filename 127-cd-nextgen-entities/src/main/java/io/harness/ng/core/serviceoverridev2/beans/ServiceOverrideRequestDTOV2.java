/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Value
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "ServiceOverrideRequestV2", description = "This is the Service Override Request entity defined in Harness")
public class ServiceOverrideRequestDTOV2 {
  // identifier field is harness-internal, we are not allowing user to provide this
  // so should be set internally with default methods
  String identifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.ENV_REF_PARAM_MESSAGE) @NotNull String environmentRef;
  @Schema(description = NGCommonEntityConstants.SERVICE_REF_PARAM_MESSAGE) String serviceRef;

  @Schema(description = NGCommonEntityConstants.INFRA_IDENTIFIER) String infraIdentifier;
  @Schema(description = NGCommonEntityConstants.CLUSTER_IDENTIFIER) String clusterIdentifier;

  @Schema(description = "Type of the override which is based on source of overrides")
  @NotNull
  ServiceOverridesType type;

  @Schema(description = "Spec for overrides, containing overriding fields like manifests, variables, config files")
  @NotNull
  ServiceOverridesSpec spec;
}
