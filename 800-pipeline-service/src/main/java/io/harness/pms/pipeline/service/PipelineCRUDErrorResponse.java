/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PipelineCRUDErrorResponse {
  public String errorMessageForPipelineNotFound(String orgId, String projectId, String pipelineId) {
    return format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist or has been deleted.", pipelineId,
        projectId, orgId);
  }

  public String errorMessageForEmptyYamlOnGit(String orgId, String projectId, String pipelineId, String branch) {
    return format("Empty YAML found on Git in branch [%s] for Pipeline [%s] under Project[%s], Organization [%s].",
        branch, pipelineId, projectId, orgId);
  }

  public String errorMessageForNotAYAMLFile(String branch, String filepath) {
    return format("File found on Git in branch [%s] for filepath [%s] is not a YAML.", branch, filepath);
  }

  public String errorMessageForNotAPipelineYAML(String branch, String filepath) {
    return format("File found on Git in branch [%s] for filepath [%s] is not a Pipeline YAML.", branch, filepath);
  }

  public String errorMessageForInvalidField(String field, String identifierInYAML, String expectedIdentifier) {
    return format(
        "%s in YAML [%s] does not match the expected %s [%s].", field, identifierInYAML, field, expectedIdentifier);
  }
}
