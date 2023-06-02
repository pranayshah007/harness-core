/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.functor;

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
import io.harness.cdng.provision.terraform.output.TerraformPlanJsonOutput;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import com.google.inject.Inject;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanJsonFunctor implements SdkFunctor {
  public static final String TERRAFORM_PLAN_JSON = "terraformPlanJson";

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length == 0 || isEmpty(args[0])) {
      throw new IllegalArgumentException(
          "Inappropriate usage of 'terraformPlanJson' functor. Missing terraform plan json output argument", USER);
    }

    String tfPlanJsonOutputName = args[0];
    OptionalSweepingOutput output = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(tfPlanJsonOutputName));
    if (!output.isFound() || output.getOutput() == null) {
      // logic to get JSON plan from saved execution details
      Optional<TerraformPlanExecutionDetails> tfPlanExecutionDetail =
          getExecutionDetailsByProvisionerId(ambiance, tfPlanJsonOutputName);
      if (tfPlanExecutionDetail.isPresent()) {
        return String.format(TerraformPlanExpressionInterface.DELEGATE_EXPRESSION,
            tfPlanExecutionDetail.get().getTfPlanJsonFieldId(), ambiance.getExpressionFunctorToken(), "jsonFilePath");
      } else {
        throw new InvalidRequestException(
            "Missing output: " + tfPlanJsonOutputName + ". Terraform plan wasn't exported.");
      }
    }
    TerraformPlanJsonOutput terraformPlanJsonOutput = (TerraformPlanJsonOutput) output.getOutput();

    return String.format(TerraformPlanExpressionInterface.DELEGATE_EXPRESSION,
        terraformPlanJsonOutput.getTfPlanFileId(), ambiance.getExpressionFunctorToken(), "jsonFilePath");
  }

  public static String getExpression(String baseFqn, String outputName) {
    return String.format("%s%s.\"%s.%s\"%s", EXPR_START, TERRAFORM_PLAN_JSON, baseFqn, outputName, EXPR_END);
  }

  public static String getExpression(String provisionerId) {
    return String.format("%s%s.\"%s\"%s", EXPR_START, TERRAFORM_PLAN_JSON, provisionerId, EXPR_END);
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
