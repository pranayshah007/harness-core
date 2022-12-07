/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.helper;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
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
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
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
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity);
    verify(pmsPipelineTemplateHelper, times(1)).resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);
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
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

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
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

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
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

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
    PipelineStageOutcome outcome =
        pipelineStageHelper.resolveOutputVariables(expressionMap, NodeExecution.builder().ambiance(ambiance).build());
    assertThat(outcome.size()).isEqualTo(2);
    assertThat(outcome.get(var1)).isEqualTo("pipelineName");
    assertThat(outcome.get(var2)).isEqualTo("constant");
  }
}
