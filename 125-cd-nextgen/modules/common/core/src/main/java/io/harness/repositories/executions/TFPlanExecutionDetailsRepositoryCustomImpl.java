/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails.TFPlanExecutionDetailsKeys;
import io.harness.exception.ExceptionUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TFPlanExecutionDetailsRepositoryCustomImpl implements TFPlanExecutionDetailsRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean deleteAllTerraformPlanExecutionDetails(TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    try {
      Criteria criteria = Criteria.where(TFPlanExecutionDetailsKeys.accountIdentifier)
                              .is(tfPlanExecutionDetailsKey.getScope().getAccountIdentifier())
                              .and(TFPlanExecutionDetailsKeys.orgIdentifier)
                              .is(tfPlanExecutionDetailsKey.getScope().getOrgIdentifier())
                              .and(TFPlanExecutionDetailsKeys.projectIdentifier)
                              .is(tfPlanExecutionDetailsKey.getScope().getProjectIdentifier())
                              .and(TFPlanExecutionDetailsKeys.pipelineExecutionId)
                              .is(tfPlanExecutionDetailsKey.getPipelineExecutionId());
      mongoTemplate.remove(new Query(criteria), TerraformPlanExecutionDetails.class);
      return true;
    } catch (Exception e) {
      log.error(
          format(
              "Error while deleting TerraformPlanExecutionDetails for Pipeline [%s] in Project [%s], in Org [%s] for Account [%s] : %s",
              tfPlanExecutionDetailsKey.getPipelineExecutionId(),
              tfPlanExecutionDetailsKey.getScope().getProjectIdentifier(),
              tfPlanExecutionDetailsKey.getScope().getOrgIdentifier(),
              tfPlanExecutionDetailsKey.getScope().getAccountIdentifier(), ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  @Override
  public List<TerraformPlanExecutionDetails> listAllPipelineTFPlanExecutionDetails(
      final TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    Criteria criteria = createScopeCriteria(tfPlanExecutionDetailsKey.getScope());

    criteria.and(TerraformPlanExecutionDetails.TFPlanExecutionDetailsKeys.pipelineExecutionId)
        .is(tfPlanExecutionDetailsKey.getPipelineExecutionId());
    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.find(query, TerraformPlanExecutionDetails.class);
  }
  public Criteria createScopeCriteria(Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(StageExecutionInfoKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(StageExecutionInfoKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(StageExecutionInfoKeys.projectIdentifier).is(scope.getProjectIdentifier());
    return criteria;
  }
}
