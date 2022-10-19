package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;

import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(CDP)
public interface TerraformPlanExecutionDetailsRepository
    extends PagingAndSortingRepository<TerraformPlanExecutionDetails, String>, TFPlanExecutionDetailsRepositoryCustom {}
