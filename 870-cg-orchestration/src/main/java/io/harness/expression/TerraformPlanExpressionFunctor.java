/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.provision.TerraformConstants.TF_APPLY_VAR_NAME;
import static io.harness.provision.TerraformConstants.TF_DESTROY_VAR_NAME;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.FunctorException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import software.wings.service.intfc.FileService;

import java.util.function.Function;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class TerraformPlanExpressionFunctor implements ExpressionFunctor, TerraformPlanExpressionInterface {
  private final transient String planSweepingOutputName;
  private final transient Function<String, TerraformPlanParam> obtainTfPlanFunction;
  private final transient FileService fileService;
  private final transient int expressionFunctorToken;
  private transient TerraformPlanParam cachedTerraformPlanParam;

  public final TerraformPlanExpressionFunctor destroy;

  @Builder
  public TerraformPlanExpressionFunctor(
      Function<String, TerraformPlanParam> obtainTfPlanFunction, FileService fileService, int expressionFunctorToken) {
    this.planSweepingOutputName = TF_APPLY_VAR_NAME;
    this.obtainTfPlanFunction = obtainTfPlanFunction;
    this.fileService = fileService;
    this.expressionFunctorToken = expressionFunctorToken;

    this.destroy = new TerraformPlanExpressionFunctor(
        TF_DESTROY_VAR_NAME, obtainTfPlanFunction, fileService, expressionFunctorToken);
  }

  protected TerraformPlanExpressionFunctor(String planSweepingOutputName,
      Function<String, TerraformPlanParam> obtainTfPlanFunction, FileService fileService, int expressionFunctorToken) {
    this.planSweepingOutputName = planSweepingOutputName;
    this.obtainTfPlanFunction = obtainTfPlanFunction;
    this.fileService = fileService;
    this.expressionFunctorToken = expressionFunctorToken;
    this.destroy = null;
  }

  @Override
  public String jsonFilePath() {
    if (cachedTerraformPlanParam == null) {
      TerraformPlanParam terraformPlanParam = findTerraformPlanParam();
      if (terraformPlanParam == null) {
        return null;
      }
      this.cachedTerraformPlanParam = terraformPlanParam;
    }

    return format(
        DELEGATE_EXPRESSION, cachedTerraformPlanParam.getTfPlanJsonFileId(), expressionFunctorToken, "jsonFilePath");
  }

  @Override
  public String humanReadableFilePath() {
    if (cachedTerraformPlanParam == null) {
      TerraformPlanParam terraformPlanParam = findTerraformPlanParam();
      if (terraformPlanParam == null) {
        return null;
      }
      this.cachedTerraformPlanParam = terraformPlanParam;
    }

    return format(HUMAN_READABLE_DELEGATE_EXPRESSION, cachedTerraformPlanParam.getTfplanHumanReadableFileId(),
        expressionFunctorToken, "humanReadableFilePath");
  }

  @Override
  public String policyChecksJsonFilePath() {
    if (cachedTerraformPlanParam == null) {
      TerraformPlanParam terraformPlanParam = findTerraformPlanParam();
      if (terraformPlanParam == null) {
        return null;
      }
      this.cachedTerraformPlanParam = terraformPlanParam;
    }
    return format(POLICY_CHECKS_DELEGATE_EXPRESSION, cachedTerraformPlanParam.getPolicyChecksFileId(),
        expressionFunctorToken, "tfcPolicyChecks");
  }

  private TerraformPlanParam findTerraformPlanParam() {
    TerraformPlanParam terraformPlanParam = obtainTfPlanFunction.apply(this.planSweepingOutputName);
    if (terraformPlanParam == null) {
      log.warn(format("Terraform plan '%s' is not available in current context. "
              + "Terraform plan is available only after terraform step with run plan only option",
          this.planSweepingOutputName));
      return null;
    }

    if (terraformPlanParam.getTfPlanJsonFileId() == null) {
      log.warn(format("Invalid usage of terraform plan functor. Missing tfPlanJsonFileId in terraform plan param '%s'",
          this.planSweepingOutputName));
      return null;
    }

    boolean fileExists;
    try {
      FileMetadata fileMetadata =
          fileService.getFileMetadata(terraformPlanParam.getTfPlanJsonFileId(), FileBucket.TERRAFORM_PLAN_JSON);
      fileExists = fileMetadata != null;
    } catch (Exception ignore) {
      // GCS implementation will throw an exception if file doesn't exist
      fileExists = false;
    }

    if (!fileExists) {
      throw new FunctorException(format(
          "File with id '%s' doesn't exist. Json representation of terraform plan isn't available after it was applied",
          terraformPlanParam.getTfPlanJsonFileId()));
    }

    return terraformPlanParam;
  }
}
