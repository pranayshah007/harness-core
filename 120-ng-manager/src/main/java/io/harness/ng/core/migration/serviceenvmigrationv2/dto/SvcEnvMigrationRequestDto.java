/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
public class SvcEnvMigrationRequestDto {
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull String pipelineIdentifier;
  @NotNull @Schema(description = "infra identifier format") String infraIdentifierFormat;
  boolean isUpdatePipeline;
  Map<String, TemplateObject> templateMap;
  List<String> skipServices;
  List<String> skipInfras;
  String branch;
  Map<String, String> expressionMap;
  Map<String, RuntimeEntity> stageMap;
  @DefaultValue("false") boolean isNewBranch;
  @DefaultValue("false") boolean populateServiceInputs;
  @DefaultValue("false") boolean populateInfrastructureInputs;
}
