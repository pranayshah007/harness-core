/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetSchemaConstants;
import io.harness.pms.pipeline.CacheResponseMetadataDTO;
import io.harness.pms.pipeline.PipelineResourceConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
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
@ApiModel("InputSetResponse")
@Schema(name = "InputSetResponse", description = "This contains Input Set details.")
public class InputSetResponseDTOPMS {
  @Schema(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId;
  @Schema(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_ID_MESSAGE) String identifier;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_YAML_MESSAGE) String inputSetYaml;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_NAME_MESSAGE) String name;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_DESCRIPTION_MESSAGE) String description;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_TAGS_MESSAGE) Map<String, String> tags;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_OUTDATED_MESSAGE) boolean isOutdated;

  @Schema(description = InputSetSchemaConstants.INPUT_SET_ERROR_MESSAGE)
  @ApiModelProperty(name = "isErrorResponse")
  boolean isErrorResponse;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_ERROR_WRAPPER_MESSAGE)
  InputSetErrorWrapperDTOPMS inputSetErrorWrapper;

  @Schema(description = InputSetSchemaConstants.INPUT_SET_VERSION_MESSAGE) @JsonIgnore Long version;

  @Schema(description = PipelineResourceConstants.GIT_DETAILS_MESSAGE) EntityGitDetails gitDetails;
  @Schema(description = PipelineResourceConstants.GIT_VALIDITY_MESSAGE) EntityValidityDetails entityValidityDetails;
  @Schema(description = GitSyncApiConstants.STORE_TYPE_RESPONSE_PARAM_MESSAGE, hidden = true) StoreType storeType;
  @Schema(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE, hidden = true) String connectorRef;
  @Schema(description = GitSyncApiConstants.GIT_CACHING_METADATA, hidden = true) CacheResponseMetadataDTO cacheResponse;
}
