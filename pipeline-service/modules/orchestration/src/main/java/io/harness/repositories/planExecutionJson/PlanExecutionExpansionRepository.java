package io.harness.repositories.planExecutionJson;

import io.harness.annotation.HarnessRepo;
import io.harness.execution.PlanExecutionExpansion;

import org.springframework.data.repository.CrudRepository;

@HarnessRepo
public interface PlanExecutionExpansionRepository
    extends CrudRepository<PlanExecutionExpansion, String>, PlanExecutionExpansionRepositoryCustom {}
