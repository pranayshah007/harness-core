package io.harness.repositories.planExecutionJson;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionExpansion;
import io.harness.mongo.helper.AnalyticsMongoTemplateHolder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@Singleton
public class PlanExecutionExpansionRepositoryCustomImpl implements PlanExecutionExpansionRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final MongoTemplate analyticsMongoTemplate;

  @Inject
  public PlanExecutionExpansionRepositoryCustomImpl(
      MongoTemplate mongoTemplate, AnalyticsMongoTemplateHolder analyticsMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.analyticsMongoTemplate = analyticsMongoTemplateHolder.getAnalyticsMongoTemplate();
  }

  @Override
  public void update(Query query, Update update) {
    mongoTemplate.findAndModify(query, update, PlanExecutionExpansion.class);
  }

  @Override
  public PlanExecutionExpansion find(Query query) {
    return mongoTemplate.findOne(query, PlanExecutionExpansion.class);
  }
}
