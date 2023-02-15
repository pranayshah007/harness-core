package io.harness.repositories.planExecutionJson;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.execution.PlanExecutionExpansion;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface PlanExecutionExpansionRepositoryCustom {
  void update(Query query, Update update);

  PlanExecutionExpansion find(Query query);
}
