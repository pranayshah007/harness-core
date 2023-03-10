/*

 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndDestroySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanOnlySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudRefreshSpecParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndDestroyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanOnlyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRefreshTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudTaskParams;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerraformCloudParamsMapper {
  @Inject private TerraformCloudStepHelper helper;

  public TerraformCloudTaskParams mapRunSpecToTaskParams(
      TerraformCloudRunStepParameters stepParameters, Ambiance ambiance) {
    TerraformCloudRunSpecParameters runSpec = stepParameters.getSpec();
    TerraformCloudRunType runType = runSpec.getType();
    switch (runType) {
      case REFRESH_STATE:
        TerraformCloudRefreshSpecParameters refreshSpec = (TerraformCloudRefreshSpecParameters) runSpec;
        return TerraformCloudRefreshTaskParams.builder()
            .workspace(ParameterFieldHelper.getParameterFieldValue(refreshSpec.getWorkspace()))
            .discardPendingRuns(ParameterFieldHelper.getBooleanParameterFieldValue(refreshSpec.getDiscardPendingRuns()))
            .variables(getVariablesMap(refreshSpec.getVariables()))
            .message(ParameterFieldHelper.getParameterFieldValue(stepParameters.getMessage()))
            .build();
      case PLAN_ONLY:
        TerraformCloudPlanOnlySpecParameters planOnlySpecParameters = (TerraformCloudPlanOnlySpecParameters) runSpec;
        return TerraformCloudPlanOnlyTaskParams.builder()
            .workspace(ParameterFieldHelper.getParameterFieldValue(planOnlySpecParameters.getWorkspace()))
            .discardPendingRuns(
                ParameterFieldHelper.getBooleanParameterFieldValue(planOnlySpecParameters.getDiscardPendingRuns()))
            .variables(getVariablesMap(planOnlySpecParameters.getVariables()))
            .exportJsonTfPlan(
                ParameterFieldHelper.getBooleanParameterFieldValue(planOnlySpecParameters.getExportTerraformPlanJson()))
            .planType(getPlanType(planOnlySpecParameters.getPlanType()))
            .targets(ParameterFieldHelper.getParameterFieldValue(planOnlySpecParameters.getTargets()))
            .terraformVersion(ParameterFieldHelper.getParameterFieldValue(planOnlySpecParameters.getTerraformVersion()))
            .message(ParameterFieldHelper.getParameterFieldValue(stepParameters.getMessage()))
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .entityId(helper.generateFullIdentifier(helper.getProvisionIdentifier(runSpec), ambiance))
            .build();
      case PLAN_AND_APPLY:
        TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
            (TerraformCloudPlanAndApplySpecParameters) runSpec;
        return TerraformCloudPlanAndApplyTaskParams.builder()
            .workspace(ParameterFieldHelper.getParameterFieldValue(planAndApplySpecParameters.getWorkspace()))
            .discardPendingRuns(
                ParameterFieldHelper.getBooleanParameterFieldValue(planAndApplySpecParameters.getDiscardPendingRuns()))
            .variables(getVariablesMap(planAndApplySpecParameters.getVariables()))
            .targets(ParameterFieldHelper.getParameterFieldValue(planAndApplySpecParameters.getTargets()))
            .policyOverride(
                ParameterFieldHelper.getBooleanParameterFieldValue(planAndApplySpecParameters.getOverridePolicies()))
            .message(ParameterFieldHelper.getParameterFieldValue(stepParameters.getMessage()))
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .entityId(helper.generateFullIdentifier(helper.getProvisionIdentifier(runSpec), ambiance))
            .build();
      case PLAN_AND_DESTROY:
        TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
            (TerraformCloudPlanAndDestroySpecParameters) runSpec;
        return TerraformCloudPlanAndDestroyTaskParams.builder()
            .workspace(ParameterFieldHelper.getParameterFieldValue(planAndDestroySpecParameters.getWorkspace()))
            .discardPendingRuns(ParameterFieldHelper.getBooleanParameterFieldValue(
                planAndDestroySpecParameters.getDiscardPendingRuns()))
            .variables(getVariablesMap(planAndDestroySpecParameters.getVariables()))
            .targets(ParameterFieldHelper.getParameterFieldValue(planAndDestroySpecParameters.getTargets()))
            .policyOverride(
                ParameterFieldHelper.getBooleanParameterFieldValue(planAndDestroySpecParameters.getOverridePolicies()))
            .message(ParameterFieldHelper.getParameterFieldValue(stepParameters.getMessage()))
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .entityId(helper.generateFullIdentifier(helper.getProvisionIdentifier(runSpec), ambiance))
            .build();
      case PLAN:
        TerraformCloudPlanSpecParameters planSpecParameters = (TerraformCloudPlanSpecParameters) runSpec;
        return TerraformCloudPlanTaskParams.builder()
            .workspace(ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getWorkspace()))
            .discardPendingRuns(
                ParameterFieldHelper.getBooleanParameterFieldValue(planSpecParameters.getDiscardPendingRuns()))
            .variables(getVariablesMap(planSpecParameters.getVariables()))
            .exportJsonTfPlan(
                ParameterFieldHelper.getBooleanParameterFieldValue(planSpecParameters.getExportTerraformPlanJson()))
            .planType(getPlanType(planSpecParameters.getPlanType()))
            .targets(ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getTargets()))
            .message(ParameterFieldHelper.getParameterFieldValue(stepParameters.getMessage()))
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .entityId(helper.generateFullIdentifier(helper.getProvisionIdentifier(runSpec), ambiance))
            .build();
      case APPLY:
        TerraformCloudApplySpecParameters applySpecParameters = (TerraformCloudApplySpecParameters) runSpec;
        return TerraformCloudApplyTaskParams.builder()
            .runId(helper.getPlanRunId(
                ParameterFieldHelper.getParameterFieldValue(applySpecParameters.getProvisionerIdentifier()), ambiance))
            .message(ParameterFieldHelper.getParameterFieldValue(stepParameters.getMessage()))
            .build();
      default:
        throw new InvalidRequestException(format("Unsupported run type: [%s]", runType));
    }
  }

  private Map<String, String> getVariablesMap(Map<String, Object> inputVariables) {
    if (isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.keySet().forEach(
        key -> res.put(key, ((ParameterField<?>) inputVariables.get(key)).getValue().toString()));
    return res;
  }

  private io.harness.delegate.task.terraformcloud.PlanType getPlanType(PlanType planType) {
    if (planType != null) {
      return planType == PlanType.APPLY ? io.harness.delegate.task.terraformcloud.PlanType.APPLY
                                        : io.harness.delegate.task.terraformcloud.PlanType.DESTROY;
    }
    return null;
  }
}
