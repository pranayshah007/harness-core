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
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import com.google.inject.Inject;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudPolicyChecksJsonFunctor implements SdkFunctor {
  public static final String TFC_POLICY_CHECKS_JSON = "policyChecksJson";

  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length == 0 || isEmpty(args[0])) {
      throw new IllegalArgumentException(
          "Inappropriate usage of 'policyChecksJson' functor. Missing policy checks json output argument", USER);
    }

    String tfcPolicyChecksOutputName = args[0];
    Optional<TerraformPlanExecutionDetails> tfPlanExecutionDetail =
        getExecutionDetailsByProvisionerId(ambiance, tfcPolicyChecksOutputName);
    if (tfPlanExecutionDetail.isPresent()) {
      return String.format(TerraformPlanExpressionInterface.POLICY_CHECKS_DELEGATE_EXPRESSION,
          tfPlanExecutionDetail.get().getTfcPolicyChecksFileId(), ambiance.getExpressionFunctorToken(),
          "policyChecksJsonFilePath");
    } else {
      throw new InvalidRequestException(
          "Missing output: " + tfcPolicyChecksOutputName + ". Terraform Cloud Policy Checks wasn't exported.");
    }
  }

  public static String getExpression(String provisionerId) {
    return String.format("%s%s.\"%s\"%s", EXPR_START, TFC_POLICY_CHECKS_JSON, provisionerId, EXPR_END);
  }

  private Optional<TerraformPlanExecutionDetails> getExecutionDetailsByProvisionerId(
      Ambiance ambiance, String provisionerId) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);

    return terraformPlanExectionDetailsService
        .listAllPipelineTFPlanExecutionDetails(TFPlanExecutionDetailsKey.builder()
                                                   .scope(Scope.builder()
                                                              .accountIdentifier(accountId)
                                                              .orgIdentifier(orgId)
                                                              .projectIdentifier(projectId)
                                                              .build())
                                                   .pipelineExecutionId(planExecutionId)
                                                   .build())
        .stream()
        .filter(executionDetail -> executionDetail.getProvisionerId().equals(provisionerId))
        .findFirst();
  }
}
