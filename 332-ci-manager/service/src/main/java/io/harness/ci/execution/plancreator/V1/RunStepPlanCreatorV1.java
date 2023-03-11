/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.V1.ScriptStepNode;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.V1.ScriptStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.ci.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;

public class RunStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<ScriptStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.SCRIPT.getDisplayName());
  }

  @Override
  public Class<ScriptStepNode> getFieldClass() {
    return ScriptStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ScriptStepNode stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(ScriptStepNode stepElement) {
    ScriptStepInfo scriptStepInfo = stepElement.getScriptStepInfo();
    return RunStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .runStepInfo(RunStepInfo.builder()
                         .command(scriptStepInfo.getRun())
                         .image(scriptStepInfo.getImage())
                         .envVariables(scriptStepInfo.getEnvs())
                         .resources(scriptStepInfo.getResources())
                         .retry(scriptStepInfo.getRetry())
                         .shell(CIPlanCreatorUtils.getShell(scriptStepInfo.getShell()))
                         .imagePullPolicy(CIPlanCreatorUtils.getImagePullPolicy(scriptStepInfo.getPull()))
                         .runAsUser(scriptStepInfo.getUser())
                         .privileged(scriptStepInfo.getPrivileged())
                         .reports(ParameterField.createValueField(
                             scriptStepInfo.getReports()
                                 .getValue()
                                 .stream()
                                 .map(r
                                     -> UnitTestReport.builder()
                                            .type(r.getType().toUnitTestReportType())
                                            .spec(JUnitTestReport.builder().paths(r.getPath()).build())
                                            .build())
                                 .collect(Collectors.toList())
                                 .stream()
                                 .findFirst()
                                 .orElse(null)))
                         .outputVariables(
                             ParameterField.createValueField(scriptStepInfo.getOutputs()
                                                                 .getValue()
                                                                 .stream()
                                                                 .map(o -> OutputNGVariable.builder().name(o).build())
                                                                 .collect(Collectors.toList())))
                         .build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
