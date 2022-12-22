/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.helper;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageHelperV1Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @InjectMocks private PipelineStageHelperV1 pipelineStageHelper;
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainedPipeline() {
    String yaml = "stages:\n"
        + "  - type: Custom\n"
        + "    spec:\n"
        + "      steps:\n"
        + "        - type: Http\n"
        + "          spec:\n"
        + "            method: GET\n"
        + "            url: http://www.google.com\n"
        + "          timeout: 10s\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity);
    verify(pmsPipelineTemplateHelper, times(1)).resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainInSeries() {
    String yaml = "stages:\n"
        + "  - type: Custom\n"
        + "    spec:\n"
        + "      steps:\n"
        + "        - type: Http\n"
        + "          spec:\n"
        + "            method: GET\n"
        + "            url: http://www.google.com\n"
        + "          timeout: 10s\n"
        + "  - type: Pipeline\n"
        + "    spec:\n"
        + "      org: orgId\n";
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainInParallel() {
    String yaml = "stages:\n"
        + "  - type: parallel\n"
        + "    spec:\n"
        + "      stages:\n"
        + "        - type: Pipeline\n"
        + "          spec:\n"
        + "            org: orgId\n"
        + "            projectId: projectId\n"
        + "            pipeline: pipelineId\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateNestedChainInGroup() {
    String yaml = "stages:\n"
        + "  - type: group\n"
        + "    spec:\n"
        + "      stages:\n"
        + "        - type: Pipeline\n"
        + "          spec:\n"
        + "            org: orgId\n"
        + "            projectId: projectId\n"
        + "            pipeline: pipelineId\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

    assertThatThrownBy(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNegativeNestedChainInParallel() {
    String yaml = "stages:\n"
        + "  - type: Custom\n"
        + "    spec:\n"
        + "      steps:\n"
        + "        - type: Http\n"
        + "          spec:\n"
        + "            method: GET\n"
        + "            url: http://www.google.com\n"
        + "          timeout: 10s\n"
        + "  - type: Deployment\n"
        + "    spec:\n"
        + "      steps:\n"
        + "        - type: Http\n"
        + "          spec:\n"
        + "            method: GET\n"
        + "            url: http://www.google.com\n"
        + "          timeout: 10s\n";

    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, BOOLEAN_FALSE_VALUE);

    assertThatCode(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInputSet() throws IOException {
    String inputs = "{\"foo\":\"bar\"}";
    YamlField inputsYamlField = YamlUtils.readTreeWithDefaultObjectMapper(inputs);
    String inputsJson = pipelineStageHelper.getInputSet(inputsYamlField);
    assertThat(inputsJson).isEqualTo("{\"inputs\":{\"foo\":\"bar\"}}");

    inputsJson = pipelineStageHelper.getInputSet(null);
    assertThat(inputsJson).isEqualTo("{}");
  }
}
