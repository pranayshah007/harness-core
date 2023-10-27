/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.dto;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.template.CacheResponseMetadataDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "InfrastructureResponseDTO", description = "This is the InfrastructureResponseDTO entity defined in Harness")
public class InfrastructureResponseDTO {
  String accountId;
  String identifier;
  String orgIdentifier;
  String projectIdentifier;
  String environmentRef;
  String name;
  String description;
  Map<String, String> tags;
  InfrastructureType type;
  ServiceDefinitionType deploymentType;
  String yaml;
  @Schema(hidden = true) EntityGitDetails entityGitDetails;
  @Schema(hidden = true) String connectorRef;
  @Schema(hidden = true) StoreType storeType;
  @Schema(hidden = true) String fallbackBranch;
  @Schema(hidden = true) CacheResponseMetadataDTO cacheResponseMetadataDTO;
}
