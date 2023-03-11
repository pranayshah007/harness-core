/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.provision.terraform.executions.RunDetails.RunDetailsKeys;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails.TerraformCloudPlanExecutionDetailsKeys;
import io.harness.exception.ExceptionUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TFCloudPlanExecutionDetailsRepositoryCustomImpl implements TFCloudPlanExecutionDetailsRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean deleteAllTerraformCloudPlanExecutionDetails(Scope scope, String pipelineExecutionId) {
    try {
      Criteria criteria = Criteria.where(TerraformCloudPlanExecutionDetailsKeys.accountIdentifier)
                              .is(scope.getAccountIdentifier())
                              .and(TerraformCloudPlanExecutionDetailsKeys.orgIdentifier)
                              .is(scope.getOrgIdentifier())
                              .and(TerraformCloudPlanExecutionDetailsKeys.projectIdentifier)
                              .is(scope.getProjectIdentifier())
                              .and(TerraformCloudPlanExecutionDetailsKeys.pipelineExecutionId)
                              .is(pipelineExecutionId);
      mongoTemplate.remove(new Query(criteria), TerraformCloudPlanExecutionDetails.class);
      return true;
    } catch (Exception e) {
      log.error(
          format(
              "Error while deleting TerraformCloudPlanExecutionDetails for Pipeline [%s] in Project [%s], in Org [%s] for Account [%s] : %s",
              pipelineExecutionId, scope.getProjectIdentifier(), scope.getOrgIdentifier(), scope,
              ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  @Override
  public List<TerraformCloudPlanExecutionDetails> listAllPipelineTerraformCloudPlanExecutionDetails(
      Scope scope, String pipelineExecutionId) {
    Criteria criteria = createScopeCriteria(scope);

    criteria.and(TerraformCloudPlanExecutionDetailsKeys.pipelineExecutionId).is(pipelineExecutionId);
    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.find(query, TerraformCloudPlanExecutionDetails.class);
  }

  @Override
  public TerraformCloudPlanExecutionDetails updateTerraformCloudPlanExecutionDetails(
      Scope scope, String pipelineExecutionId, String runId, Map<String, Object> updates) {
    Criteria criteria = createScopeCriteria(scope);

    criteria.and(TerraformCloudPlanExecutionDetailsKeys.pipelineExecutionId).is(pipelineExecutionId);
    criteria.and(TerraformCloudPlanExecutionDetailsKeys.runDetails + "." + RunDetailsKeys.runId).is(runId);

    Query query = new Query();
    query.addCriteria(criteria);
    Update update = new Update();
    updates.forEach(update::set);
    return mongoTemplate.findAndModify(query, update, TerraformCloudPlanExecutionDetails.class);
  }

  private Criteria createScopeCriteria(Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(TerraformCloudPlanExecutionDetailsKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(TerraformCloudPlanExecutionDetailsKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(TerraformCloudPlanExecutionDetailsKeys.projectIdentifier).is(scope.getProjectIdentifier());
    return criteria;
  }
}
