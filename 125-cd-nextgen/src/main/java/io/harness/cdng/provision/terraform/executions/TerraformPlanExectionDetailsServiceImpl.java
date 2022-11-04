/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.repositories.executions.TerraformPlanExecutionDetailsRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class TerraformPlanExectionDetailsServiceImpl implements TerraformPlanExectionDetailsService {
  private TerraformPlanExecutionDetailsRepository terraformPlanExecutionDetailsRepository;

  @Override
  public TerraformPlanExecutionDetails save(
      @Valid @NotNull TerraformPlanExecutionDetails terraformPlanExecutionDetails) {
    return terraformPlanExecutionDetailsRepository.save(terraformPlanExecutionDetails);
  }

  @Override
  public boolean deleteAllTerraformPlanExecutionDetails(TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    return terraformPlanExecutionDetailsRepository.deleteAllTerraformPlanExecutionDetails(tfPlanExecutionDetailsKey);
  }

  @Override
  public List<TerraformPlanExecutionDetails> listAllPipelineTFPlanExecutionDetails(
      @NotNull @Valid TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    if (isEmpty(tfPlanExecutionDetailsKey.getPipelineExecutionId())) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }
    return terraformPlanExecutionDetailsRepository.listAllPipelineTFPlanExecutionDetails(tfPlanExecutionDetailsKey);
  }
}
