/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.publicaccess.dto.PublicAccessResponse;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PMSPipelineResponse", description = "This contains pipeline yaml with the version.")
public class PMSPipelineResponseDTO {
  private String yamlPipeline;
  @Schema(description = "Pipeline YAML after resolving templates") private String resolvedTemplatesPipelineYaml;
  @JsonIgnore Long version;
  EntityGitDetails gitDetails;
  EntityValidityDetails entityValidityDetails;
  Set<String> modules;
  GovernanceMetadata governanceMetadata;
  YamlSchemaErrorWrapperDTO yamlSchemaErrorWrapper;
  ValidateTemplateInputsResponseDTO validateTemplateInputsResponse;
  CacheResponseMetadataDTO cacheResponse;
  // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
  // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
  // is not needed and will be null
  String validationUuid;
  StoreType storeType;
  PublicAccessResponse publicAccessResponse;
}
