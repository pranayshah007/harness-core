package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class PlanExecutionRepositoryCustomImpl implements PlanExecutionRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PlanExecution getWithProjectionsWithoutUuid(String planExecutionId, List<String> fieldNames) {
    Criteria criteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(criteria);
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    query.fields().exclude(PlanExecutionKeys.uuid);
    return mongoTemplate.findOne(query, PlanExecution.class);
  }
}
