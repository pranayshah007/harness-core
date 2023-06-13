/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.helper;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.ProceedWithDefaultValuesFailureActionConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.marksuccess.MarkAsSuccessFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.rollback.PipelineRollbackFailureActionConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PipelineStageHelperV1 pipelineStageHelperV1;
  @InjectMocks private PipelineStageHelper pipelineStageHelper;
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateNestedChainedPipeline() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity);
    verify(pmsPipelineTemplateHelper, times(1)).resolveTemplateRefsInPipeline(pipelineEntity, "true");
    verify(pipelineStageHelperV1, times(0)).containsPipelineStage(yaml);

    pipelineEntity = PipelineEntity.builder().harnessVersion(PipelineVersion.V1).build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity);
    verify(pipelineStageHelperV1, times(1)).containsPipelineStage(yaml);

    PipelineEntity pipelineEntity1 = PipelineEntity.builder().harnessVersion("V2").build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity1, "true");
    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity1))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateNestedChainInSeries() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n"
        + "        - stage:\n"
        + "              name: test2\n"
        + "              type: Pipeline\n"
        + "              identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateNestedChainInParallel() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n"
        + "        - parallel:\n"
        + "             - stage:\n"
        + "                 name: test\n"
        + "                 type: Pipeline\n"
        + "                 identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testNegativeNestedChainInParallel() {
    String yaml = "pipeline:\n"
        + "    name: test nested pipeline chain\n"
        + "    identifier: pipeline_chain\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: test\n"
        + "              type: Deployment\n"
        + "              identifier: test\n"
        + "        - parallel:\n"
        + "             - stage:\n"
        + "                 name: test\n"
        + "                 type: Deployment\n"
        + "                 identifier: test\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, "true");

    assertThatCode(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testResolveExpression() {
    Map<String, ParameterField<String>> expressionMap = new HashMap<>();

    String var1 = "var1";
    String var2 = "var2";
    Ambiance ambiance = Ambiance.newBuilder().setPlanId("planId").build();
    expressionMap.put(var1, ParameterField.createExpressionField(true, "<+pipeline.name>", null, false));
    expressionMap.put(var2, ParameterField.createValueField("constant"));

    Map<String, String> resolvedMap = new HashMap<>();
    resolvedMap.put(var1, expressionMap.get(var1).getExpressionValue());
    resolvedMap.put(var2, expressionMap.get(var2).getValue());

    Map<String, String> resolvedExpressionMap = new HashMap<>();
    resolvedExpressionMap.put(var1, "pipelineName");
    resolvedExpressionMap.put(var2, expressionMap.get(var2).getValue());

    doReturn(resolvedExpressionMap)
        .when(pmsEngineExpressionService)
        .resolve(ambiance, resolvedMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    PipelineStageOutcome outcome = pipelineStageHelper.resolveOutputVariables(expressionMap, ambiance);
    assertThat(outcome.size()).isEqualTo(2);
    assertThat(outcome.get(var1)).isEqualTo("pipelineName");
    assertThat(outcome.get(var2)).isEqualTo("constant");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateFailureStrategy() {
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(null)).doesNotThrowAnyException();
    ParameterField<List<FailureStrategyConfig>> failureStrategyConfigs =
        getFailureStrategy(RetryFailureActionConfig.builder().build());

    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(failureStrategyConfigs))
        .isInstanceOf(InvalidRequestException.class);

    ParameterField<List<FailureStrategyConfig>> miFailureStrategy =
        getFailureStrategy(ManualInterventionFailureActionConfig.builder().build());
    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(miFailureStrategy))
        .isInstanceOf(InvalidRequestException.class);

    ParameterField<List<FailureStrategyConfig>> pipelineRollbackFailureStrategy =
        getFailureStrategy(PipelineRollbackFailureActionConfig.builder().build());
    assertThatThrownBy(() -> pipelineStageHelper.validateFailureStrategy(pipelineRollbackFailureStrategy))
        .isInstanceOf(InvalidRequestException.class);

    ParameterField<List<FailureStrategyConfig>> defaultFailureStrategy =
        getFailureStrategy(ProceedWithDefaultValuesFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(defaultFailureStrategy))
        .doesNotThrowAnyException();

    ParameterField<List<FailureStrategyConfig>> ignoreFailureStrategy =
        getFailureStrategy(IgnoreFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(ignoreFailureStrategy)).doesNotThrowAnyException();

    ParameterField<List<FailureStrategyConfig>> markAsSuccessFailureStrategy =
        getFailureStrategy(MarkAsSuccessFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(markAsSuccessFailureStrategy))
        .doesNotThrowAnyException();

    ParameterField<List<FailureStrategyConfig>> abortFailureStrategy =
        getFailureStrategy(AbortFailureActionConfig.builder().build());
    assertThatCode(() -> pipelineStageHelper.validateFailureStrategy(abortFailureStrategy)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInputSetYaml() throws IOException {
    YamlField inputSetField = YamlUtils.readTreeWithDefaultObjectMapper("a:\n  b: c");
    String inputSetYaml = pipelineStageHelper.getInputSetYaml(inputSetField, PipelineVersion.V0);
    assertThat(inputSetYaml).isEqualTo("pipeline:\n  a:\n    b: c\n");
    verify(pipelineStageHelperV1, times(0)).getInputSet(inputSetField);

    pipelineStageHelper.getInputSetYaml(inputSetField, PipelineVersion.V1);
    verify(pipelineStageHelperV1, times(1)).getInputSet(inputSetField);

    assertThatThrownBy(() -> pipelineStageHelper.getInputSetYaml(inputSetField, "V2"))
        .isInstanceOf(InvalidRequestException.class);
  }
  @NotNull
  private ParameterField<List<FailureStrategyConfig>> getFailureStrategy(
      FailureStrategyActionConfig failureStrategyActionConfig) {
    return ParameterField.createValueField(
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder().action(failureStrategyActionConfig).build())
                                      .build()));
  }
}
