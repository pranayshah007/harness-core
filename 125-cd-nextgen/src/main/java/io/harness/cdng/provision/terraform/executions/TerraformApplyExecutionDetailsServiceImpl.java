/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.repositories.executions.TerraformApplyExecutionDetailsRepository;

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
public class TerraformApplyExecutionDetailsServiceImpl implements TerraformApplyExecutionDetailsService {
  private TerraformApplyExecutionDetailsRepository terraformApplyExecutionDetailsRepository;

  @Override
  public TerraformApplyExecutionDetails save(
      @Valid @NotNull TerraformApplyExecutionDetails terraformApplyExecutionDetails) {
    return terraformApplyExecutionDetailsRepository.save(terraformApplyExecutionDetails);
  }

  @Override
  public boolean deleteAllTerraformApplyExecutionDetails(TFApplyExecutionDetailsKey tfApplyExecutionDetailsKey) {
    return terraformApplyExecutionDetailsRepository.deleteAllTerraformApplyExecutionDetails(tfApplyExecutionDetailsKey);
  }

  @Override
  public List<TerraformApplyExecutionDetails> listAllPipelineTFApplyExecutionDetails(
      @NotNull @Valid TFApplyExecutionDetailsKey tfApplyExecutionDetailsKey) {
    if (isEmpty(tfApplyExecutionDetailsKey.getPipelineExecutionId())) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }
    return terraformApplyExecutionDetailsRepository.listAllPipelineTFApplyExecutionDetails(tfApplyExecutionDetailsKey);
  }
}
