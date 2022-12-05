/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.functor;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.output.TerraformHumanReadablePlanOutput;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDP)
public class TerraformHumanReadablePlanFunctor implements SdkFunctor {
  public static final String TERRAFORM_HUMAN_READABLE_PLAN = "terraformPlanHumanReadable";

  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length == 0 || isEmpty(args[0])) {
      throw new IllegalArgumentException(
          "Inappropriate usage of 'terraformPlanHumanReadable' functor. Missing terraform human readable output argument",
          USER);
    }

    String tfHumanReadablePlanName = args[0];
    OptionalSweepingOutput output = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(tfHumanReadablePlanName));
    if (!output.isFound() || output.getOutput() == null) {
      throw new InvalidRequestException(
          "Missing output: " + tfHumanReadablePlanName + ". Terraform plan wasn't exported.");
    }

    TerraformHumanReadablePlanOutput terraformHumanReadablePlanOutput =
        (TerraformHumanReadablePlanOutput) output.getOutput();

    return String.format(TerraformPlanExpressionInterface.HUMAN_READABLE_DELEGATE_EXPRESSION,
        terraformHumanReadablePlanOutput.getTfPlanFileId(), ambiance.getExpressionFunctorToken(),
        "humanReadableFilePath");
  }

  public static String getExpression(String baseFqn, String outputName) {
    return String.format("%s%s.\"%s.%s\"%s", EXPR_START, TERRAFORM_HUMAN_READABLE_PLAN, baseFqn, outputName, EXPR_END);
  }
}
