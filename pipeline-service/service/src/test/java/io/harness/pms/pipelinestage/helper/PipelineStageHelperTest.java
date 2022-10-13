/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.helper;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

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
import io.harness.rule.Owner;

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
        .resolveTemplateRefsInPipeline(pipelineEntity);
    pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity);
    verify(pmsPipelineTemplateHelper, times(1)).resolveTemplateRefsInPipeline(pipelineEntity);
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
        .resolveTemplateRefsInPipeline(pipelineEntity);

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
        .resolveTemplateRefsInPipeline(pipelineEntity);

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
        .resolveTemplateRefsInPipeline(pipelineEntity);

    assertThatCode(() -> pipelineStageHelper.validateNestedChainedPipeline(pipelineEntity)).doesNotThrowAnyException();
  }
}
