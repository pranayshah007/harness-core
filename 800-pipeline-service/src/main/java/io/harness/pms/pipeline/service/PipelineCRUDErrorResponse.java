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
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.governance.PolicySetMetadata;

import java.util.List;
import java.util.stream.Collectors;
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

  public void checkForGovernanceErrorAndThrow(GovernanceMetadata governanceMetadata) {
    if (governanceMetadata.getDeny()) {
      List<String> denyingPolicySetIds = governanceMetadata.getDetailsList()
                                             .stream()
                                             .filter(PolicySetMetadata::getDeny)
                                             .map(PolicySetMetadata::getIdentifier)
                                             .collect(Collectors.toList());
      // todo: see if this can be changed to PolicyEvaluationFailureException, probably yes
      throw new InvalidRequestException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingPolicySetIds);
    }
  }

  public String errorMessageForPipelinesNotDeleted(
      String accountID, String orgId, String projectId, String exceptionMessage) {
    return format("Error while deleting Pipelines in Project [%s], in Org [%s] for Account [%s] : %s", projectId, orgId,
        accountID, exceptionMessage);
  }
}
