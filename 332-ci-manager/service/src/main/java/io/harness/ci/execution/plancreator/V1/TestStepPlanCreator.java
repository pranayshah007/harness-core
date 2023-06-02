/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.V1.TestStepNode;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.TestStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.ci.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;

public class TestStepPlanCreator extends CIPMSStepPlanCreatorV2<TestStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.TEST.getDisplayName());
  }

  @Override
  public Class<TestStepNode> getFieldClass() {
    return TestStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TestStepNode stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(TestStepNode stepElement) {
    TestStepInfo testStepInfo = stepElement.getTestStepInfo();
    return RunTestStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .runTestsStepInfo(
            RunTestsStepInfo.builder()
                .image(testStepInfo.getImage())
                .envVariables(testStepInfo.getEnvs())
                .resources(testStepInfo.getResources())
                .retry(testStepInfo.getRetry())
                .buildTool(testStepInfo.getUses() == null
                        ? ParameterField.ofNull()
                        : ParameterField.createValueField(testStepInfo.getUses().toTIBuildTool()))
                .shell(CIPlanCreatorUtils.getShell(testStepInfo.getShell()))
                .imagePullPolicy(CIPlanCreatorUtils.getImagePullPolicy(testStepInfo.getPull()))
                .language(testStepInfo.getLanguage())
                .args(SerializerUtils.getStringFieldFromJsonNodeMap(testStepInfo.getWith(), "args"))
                .preCommand(SerializerUtils.getStringFieldFromJsonNodeMap(testStepInfo.getWith(), "pre_command"))
                .postCommand(SerializerUtils.getStringFieldFromJsonNodeMap(testStepInfo.getWith(), "post_command"))
                .runOnlySelectedTests(
                    SerializerUtils.getBooleanFieldFromJsonNodeMap(testStepInfo.getWith(), "run_selected_tests"))
                .packages(SerializerUtils.getListAsStringFromJsonNodeMap(testStepInfo.getWith(), "packages"))
                .testAnnotations(SerializerUtils.getListAsStringFromJsonNodeMap(testStepInfo.getWith(), "annotations"))
                .namespaces(SerializerUtils.getListAsStringFromJsonNodeMap(testStepInfo.getWith(), "namespaces"))
                .testGlobs(SerializerUtils.getListAsStringFromJsonNodeMap(testStepInfo.getWith(), "globs"))
                .runAsUser(testStepInfo.getUser())
                .privileged(testStepInfo.getPrivileged())
                .enableTestSplitting(testStepInfo.getSplitting().getEnabled())
                .testSplitStrategy(testStepInfo.getSplitting().getStrategy() == null
                        ? ParameterField.ofNull()
                        : ParameterField.createValueField(
                            testStepInfo.getSplitting().getStrategy().toTISplitStrategy()))
                .reports(ParameterField.createValueField(
                    testStepInfo.getReports()
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
                    ParameterField.createValueField(testStepInfo.getOutputs()
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
