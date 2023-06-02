/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.functor;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraformcloud.executiondetails.TerraformCloudPlanExecutionDetailsService;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import com.google.inject.Inject;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudPlanJsonFunctor implements SdkFunctor {
  public static final String TERRAFORM_CLOUD_PLAN_JSON = "terraformCloudPlanJson";

  @Inject private TerraformCloudPlanExecutionDetailsService terraformCloudPlanExecutionDetailsService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length == 0 || isEmpty(args[0])) {
      throw new IllegalArgumentException(
          "Inappropriate usage of 'terraformCloudPlanJson' functor. Missing terraform plan json output argument", USER);
    }

    String tfPlanJsonOutputName = args[0];
    Optional<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetails =
        getExecutionDetailsByProvisionerId(ambiance, tfPlanJsonOutputName);
    if (terraformCloudPlanExecutionDetails.isPresent()) {
      return String.format(TerraformPlanExpressionInterface.TERRAFORM_CLOUD_PLAN_DELEGATE_EXPRESSION,
          terraformCloudPlanExecutionDetails.get().getTfPlanJsonFieldId(), ambiance.getExpressionFunctorToken(),
          "jsonFilePath");
    } else {
      throw new InvalidRequestException(
          "Missing output: " + tfPlanJsonOutputName + ". Terraform cloud plan wasn't exported.");
    }
  }

  public static String getExpression(String baseFqn, String outputName) {
    return String.format("%s%s.\"%s.%s\"%s", EXPR_START, TERRAFORM_CLOUD_PLAN_JSON, baseFqn, outputName, EXPR_END);
  }

  public static String getExpression(String provisionerId) {
    return String.format("%s%s.\"%s\"%s", EXPR_START, TERRAFORM_CLOUD_PLAN_JSON, provisionerId, EXPR_END);
  }

  private Optional<TerraformCloudPlanExecutionDetails> getExecutionDetailsByProvisionerId(
      Ambiance ambiance, String provisionerId) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);

    return terraformCloudPlanExecutionDetailsService
        .listAllPipelineTFCloudPlanExecutionDetails(
            Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build(),
            planExecutionId)
        .stream()
        .filter(executionDetail -> executionDetail.getProvisionerId().equals(provisionerId))
        .findFirst();
  }
}
