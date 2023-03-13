/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.executiondetails;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.repositories.executions.TerraformCloudPlanExecutionDetailsRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class TerraformCloudPlanExecutionDetailsServiceImpl implements TerraformCloudPlanExecutionDetailsService {
  private TerraformCloudPlanExecutionDetailsRepository terraformCloudPlanExecutionDetailsRepository;

  @Override
  public TerraformCloudPlanExecutionDetails save(
      @Valid @NotNull TerraformCloudPlanExecutionDetails terraformCloudPlanExecutionDetails) {
    return terraformCloudPlanExecutionDetailsRepository.save(terraformCloudPlanExecutionDetails);
  }

  @Override
  public boolean deleteAllTerraformCloudPlanExecutionDetails(Scope scope, String pipelineExecutionId) {
    return terraformCloudPlanExecutionDetailsRepository.deleteAllTerraformCloudPlanExecutionDetails(
        scope, pipelineExecutionId);
  }

  @Override
  public List<TerraformCloudPlanExecutionDetails> listAllPipelineTFCloudPlanExecutionDetails(
      Scope scope, String pipelineExecutionId) {
    if (isEmpty(pipelineExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }
    return terraformCloudPlanExecutionDetailsRepository.listAllPipelineTerraformCloudPlanExecutionDetails(
        scope, pipelineExecutionId);
  }

  @Override
  public TerraformCloudPlanExecutionDetails updateTerraformCloudPlanExecutionDetails(
      Scope scope, String pipelineExecutionId, String runId, Map<String, Object> updates) {
    return terraformCloudPlanExecutionDetailsRepository.updateTerraformCloudPlanExecutionDetails(
        scope, pipelineExecutionId, runId, updates);
  }
}
