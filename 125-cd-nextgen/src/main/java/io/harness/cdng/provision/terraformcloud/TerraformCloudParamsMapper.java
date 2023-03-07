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
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams.TerraformCloudTaskParamsBuilder;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerraformCloudParamsMapper {
  @Inject private TerraformCloudStepHelper helper;

  public TerraformCloudTaskParams mapRunSpecToTaskParams(TerraformCloudRunSpecParameters runSpec, Ambiance ambiance) {
    TerraformCloudRunType runType = runSpec.getType();
    TerraformCloudTaskParamsBuilder builder;
    switch (runType) {
      case REFRESH_STATE:
        TerraformCloudRefreshSpecParameters refreshSpec = (TerraformCloudRefreshSpecParameters) runSpec;
        builder = buildTerraformCloudTaskParams(refreshSpec.getOrganization(), refreshSpec.getWorkspace(),
            refreshSpec.getDiscardPendingRuns(), refreshSpec.getVariables())
                      .terraformCloudTaskType(TerraformCloudTaskType.RUN_REFRESH_STATE);
        return builder.build();
      case PLAN_ONLY:
        TerraformCloudPlanOnlySpecParameters planOnlySpecParameters = (TerraformCloudPlanOnlySpecParameters) runSpec;
        builder = buildTerraformCloudTaskParams(planOnlySpecParameters.getOrganization(),
            planOnlySpecParameters.getWorkspace(), planOnlySpecParameters.getDiscardPendingRuns(),
            planOnlySpecParameters.getVariables(), planOnlySpecParameters.getExportTerraformPlanJson(),
            planOnlySpecParameters.getPlanType(), planOnlySpecParameters.getTargets())
                      .terraformVersion(ParameterFieldHelper.getParameterFieldValue(
                          ((TerraformCloudPlanOnlySpecParameters) runSpec).getTerraformVersion()))
                      .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN_ONLY);
        break;
      case PLAN_AND_APPLY:
        TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
            (TerraformCloudPlanAndApplySpecParameters) runSpec;
        builder = buildTerraformCloudTaskParams(planAndApplySpecParameters.getOrganization(),
            planAndApplySpecParameters.getWorkspace(), planAndApplySpecParameters.getDiscardPendingRuns(),
            planAndApplySpecParameters.getVariables(), null, null, planAndApplySpecParameters.getTargets())
                      .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN_AND_APPLY);
        builder.policyOverride(
            ParameterFieldHelper.getBooleanParameterFieldValue(planAndApplySpecParameters.getOverridePolicies()));
        break;
      case PLAN_AND_DESTROY:
        TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
            (TerraformCloudPlanAndDestroySpecParameters) runSpec;
        builder = buildTerraformCloudTaskParams(planAndDestroySpecParameters.getOrganization(),
            planAndDestroySpecParameters.getWorkspace(), planAndDestroySpecParameters.getDiscardPendingRuns(),
            planAndDestroySpecParameters.getVariables(), null, null, planAndDestroySpecParameters.getTargets())
                      .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN_AND_DESTROY);
        builder.policyOverride(
            ParameterFieldHelper.getBooleanParameterFieldValue(planAndDestroySpecParameters.getOverridePolicies()));
        break;
      case PLAN:
        TerraformCloudPlanSpecParameters planSpecParameters = (TerraformCloudPlanSpecParameters) runSpec;
        builder = buildTerraformCloudTaskParams(planSpecParameters.getOrganization(), planSpecParameters.getWorkspace(),
            planSpecParameters.getDiscardPendingRuns(), planSpecParameters.getVariables(),
            planSpecParameters.getExportTerraformPlanJson(), planSpecParameters.getPlanType(),
            planSpecParameters.getTargets())
                      .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN);
        break;
      case APPLY:
        TerraformCloudApplySpecParameters applySpecParameters = (TerraformCloudApplySpecParameters) runSpec;
        builder = TerraformCloudTaskParams.builder()
                      .runId(helper.getPlanRunId(
                          ParameterFieldHelper.getParameterFieldValue(applySpecParameters.getProvisionerIdentifier()),
                          ambiance))
                      .terraformCloudTaskType(TerraformCloudTaskType.RUN_APPLY);
        break;
      default:
        throw new InvalidRequestException(format("Unsupported run type: [%s]", runType));
    }
    return builder.build();
  }

  private TerraformCloudTaskParamsBuilder buildTerraformCloudTaskParams(ParameterField<String> organization,
      ParameterField<String> workspace, ParameterField<Boolean> discardPendingRuns, Map<String, Object> variables,
      ParameterField<Boolean> exportTerraformPlanJson, PlanType planType, ParameterField<List<String>> targets) {
    return buildTerraformCloudTaskParams(organization, workspace, discardPendingRuns, variables)
        .exportJsonTfPlan(ParameterFieldHelper.getBooleanParameterFieldValue(exportTerraformPlanJson))
        .targets(ParameterFieldHelper.getParameterFieldValue(targets))
        .planType(planType != null ? getPlanType(planType) : null);
  }

  private TerraformCloudTaskParamsBuilder buildTerraformCloudTaskParams(ParameterField<String> organization,
      ParameterField<String> workspaceId, ParameterField<Boolean> discardPendingRuns, Map<String, Object> variables) {
    return TerraformCloudTaskParams.builder()
        .organization(ParameterFieldHelper.getParameterFieldValue(organization))
        .workspace(ParameterFieldHelper.getParameterFieldValue(workspaceId))
        .discardPendingRuns(ParameterFieldHelper.getBooleanParameterFieldValue(discardPendingRuns))
        .variables(getVariablesMap(variables));
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

  private io.harness.delegate.beans.terraformcloud.PlanType getPlanType(PlanType planType) {
    return planType == PlanType.APPLY ? io.harness.delegate.beans.terraformcloud.PlanType.APPLY
                                      : io.harness.delegate.beans.terraformcloud.PlanType.DESTROY;
  }
}
