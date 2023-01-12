package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadata;

import com.google.inject.Inject;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class PlanExecutionMetadataRepositoryCustomImpl implements PlanExecutionMetadataRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PlanExecutionMetadata findByPlanExecutionIdUsingProjections(
      String planExecutionId, Set<String> fieldsToInclude) {
    Criteria criteria =
        Criteria.where(PlanExecutionMetadata.PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return mongoTemplate.findOne(query, PlanExecutionMetadata.class);
  }
}