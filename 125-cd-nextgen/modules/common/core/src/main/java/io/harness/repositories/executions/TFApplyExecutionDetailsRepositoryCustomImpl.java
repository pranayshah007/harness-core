/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.provision.terraform.executions.TFApplyExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformApplyExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformApplyExecutionDetails.TFApplyExecutionDetailsKeys;
import io.harness.exception.ExceptionUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TFApplyExecutionDetailsRepositoryCustomImpl implements TFApplyExecutionDetailsRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean deleteAllTerraformApplyExecutionDetails(TFApplyExecutionDetailsKey tfApplyExecutionDetailsKey) {
    try {
      Criteria criteria = Criteria.where(TFApplyExecutionDetailsKeys.accountIdentifier)
                              .is(tfApplyExecutionDetailsKey.getScope().getAccountIdentifier())
                              .and(TFApplyExecutionDetailsKeys.orgIdentifier)
                              .is(tfApplyExecutionDetailsKey.getScope().getOrgIdentifier())
                              .and(TFApplyExecutionDetailsKeys.projectIdentifier)
                              .is(tfApplyExecutionDetailsKey.getScope().getProjectIdentifier())
                              .and(TFApplyExecutionDetailsKeys.pipelineExecutionId)
                              .is(tfApplyExecutionDetailsKey.getPipelineExecutionId());
      mongoTemplate.remove(new Query(criteria), TerraformApplyExecutionDetails.class);
      return true;
    } catch (Exception e) {
      log.error(
          format(
              "Error while deleting TerraformApplyExecutionDetails for Pipeline [%s] in Project [%s], in Org [%s] for Account [%s] : %s",
              tfApplyExecutionDetailsKey.getPipelineExecutionId(),
              tfApplyExecutionDetailsKey.getScope().getProjectIdentifier(),
              tfApplyExecutionDetailsKey.getScope().getOrgIdentifier(),
              tfApplyExecutionDetailsKey.getScope().getAccountIdentifier(), ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  @Override
  public List<TerraformApplyExecutionDetails> listAllPipelineTFApplyExecutionDetails(
      final TFApplyExecutionDetailsKey tfApplyExecutionDetailsKey) {
    Scope scope = tfApplyExecutionDetailsKey.getScope();

    Criteria criteria = new Criteria();
    criteria.and(StageExecutionInfoKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(StageExecutionInfoKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(StageExecutionInfoKeys.projectIdentifier).is(scope.getProjectIdentifier());
    criteria.and(TFApplyExecutionDetailsKeys.pipelineExecutionId)
        .is(tfApplyExecutionDetailsKey.getPipelineExecutionId());

    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.find(query, TerraformApplyExecutionDetails.class);
  }
}