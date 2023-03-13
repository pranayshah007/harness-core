/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.executiondetails;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;

import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public interface TerraformCloudPlanExecutionDetailsService {
  TerraformCloudPlanExecutionDetails save(TerraformCloudPlanExecutionDetails terraformCloudPlanExecutionDetails);

  boolean deleteAllTerraformCloudPlanExecutionDetails(Scope scope, String pipelineExecutionId);

  List<TerraformCloudPlanExecutionDetails> listAllPipelineTFCloudPlanExecutionDetails(
      Scope scope, String pipelineExecutionId);

  TerraformCloudPlanExecutionDetails updateTerraformCloudPlanExecutionDetails(
      Scope scope, String pipelineExecutionId, String runId, Map<String, Object> updates);
}
