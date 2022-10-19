package io.harness.cdng.provision.terraform.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(CDP)
public interface TerraformPlanExectionDetailsService {
  TerraformPlanExecutionDetails save(TerraformPlanExecutionDetails terraformPlanExecutionDetails);

  boolean deleteAllTerraformPlanExecutionDetails(TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey);

  List<TerraformPlanExecutionDetails> listAllPipelineTFPlanExecutionDetails(
      TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey);
}
