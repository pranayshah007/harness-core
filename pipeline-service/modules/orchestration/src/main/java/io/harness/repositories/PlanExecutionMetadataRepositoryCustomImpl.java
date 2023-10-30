/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.EntityNotFoundException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import java.util.Set;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMetadataRepositoryCustomImpl implements PlanExecutionMetadataRepositoryCustom {
  private final MongoTemplate secondaryMongoTemplate;
  private final MongoTemplate mongoTemplate;

  @Inject
  public PlanExecutionMetadataRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  public PlanExecutionMetadata getWithFieldsIncludedFromSecondary(String planExecutionId, Set<String> fieldsToInclude) {
    Query query = new Query(Criteria.where(PlanExecutionMetadataKeys.planExecutionId).is(planExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    PlanExecutionMetadata planExecutionMetadata = secondaryMongoTemplate.findOne(query, PlanExecutionMetadata.class);
    if (planExecutionMetadata == null) {
      throw new EntityNotFoundException("Plan Execution Metadata not found for planExecutionId: " + planExecutionId
          + " . Please note that this data is not available for executions older than 30 days.");
    }
    return planExecutionMetadata;
  }

  @Override
  public PlanExecutionMetadata updatePlanExecution(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return Failsafe.with(DEFAULT_RETRY_POLICY)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PlanExecutionMetadata.class));
  }

  @Override
  public void multiUpdatePlanExecution(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      mongoTemplate.updateMulti(query, update, PlanExecutionMetadata.class);
      return true;
    });
  }
}
