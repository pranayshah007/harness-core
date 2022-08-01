/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineYamlDtoMapperTest extends CategoryTest {
  String correctYaml = "pipeline:\n"
      + "  identifier: n1\n"
      + "  orgIdentifier: n2\n"
      + "  projectIdentifier: n3\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1";
  String wrongYaml = "pipeline:"
      + "  identifier:: n1"
      + "  stages:\n"
      + "    - stage:\n";
  String correctYamlWithAllowStages = "pipeline:\n"
      + "  identifier: n1\n"
      + "  orgIdentifier: n2\n"
      + "  projectIdentifier: n3\n"
      + "  allowStageExecutions: true\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1";
  String correctYamlWithDisallowStages = "pipeline:\n"
      + "  identifier: n1\n"
      + "  orgIdentifier: n2\n"
      + "  projectIdentifier: n3\n"
      + "  allowStageExecutions: false\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1";
  String correctYamlWithPipelineLevelDelegateSelectors = "pipeline:\n"
      + "  identifier: n1\n"
      + "  orgIdentifier: n2\n"
      + "  projectIdentifier: n3\n"
      + "  allowStageExecutions: false\n"
      + "  delegateSelectors:\n"
      + "    - sel_pipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        delegateSelectors:\n"
      + "          - sel_stage";

  String correctYamlWithPipelineLevelDelegateSelectorsEmptyList = "pipeline:\n"
      + "  identifier: n1\n"
      + "  orgIdentifier: n2\n"
      + "  projectIdentifier: n3\n"
      + "  allowStageExecutions: false\n"
      + "  delegateSelectors:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1";
  String correctYamlWithPipelineLevelDelegateSelectorsMoreThanOne = "pipeline:\n"
      + "  identifier: n1\n"
      + "  orgIdentifier: n2\n"
      + "  projectIdentifier: n3\n"
      + "  allowStageExecutions: false\n"
      + "  delegateSelectors:\n"
      + "    - sel_pipeline\n"
      + "    - sel2_pipeline\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1";

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDto() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(correctYaml).build();
    PipelineConfig pipelineConfig = PipelineYamlDtoMapper.toDto(pipelineEntity);
    PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.getIdentifier()).isEqualTo("n1");
    assertThat(pipelineInfoConfig.getOrgIdentifier()).isEqualTo("n2");
    assertThat(pipelineInfoConfig.getProjectIdentifier()).isEqualTo("n3");
    assertThat(pipelineInfoConfig.getStages()).hasSize(1);
    assertThat(pipelineInfoConfig.isAllowStageExecutions()).isFalse();

    assertThatThrownBy(() -> PipelineYamlDtoMapper.toDto(PipelineEntity.builder().yaml(wrongYaml).build()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForAllowStageExecutionsFlag() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(correctYamlWithAllowStages).build();
    PipelineConfig pipelineConfig = PipelineYamlDtoMapper.toDto(pipelineEntity);
    PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.isAllowStageExecutions()).isTrue();

    pipelineEntity = PipelineEntity.builder().yaml(correctYamlWithDisallowStages).build();
    pipelineConfig = PipelineYamlDtoMapper.toDto(pipelineEntity);
    pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.isAllowStageExecutions()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForYaml() {
    PipelineConfig pipelineConfig = PipelineYamlDtoMapper.toDto(correctYaml);
    PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.getIdentifier()).isEqualTo("n1");
    assertThat(pipelineInfoConfig.getOrgIdentifier()).isEqualTo("n2");
    assertThat(pipelineInfoConfig.getProjectIdentifier()).isEqualTo("n3");
    assertThat(pipelineInfoConfig.getStages()).hasSize(1);

    assertThatThrownBy(() -> PipelineYamlDtoMapper.toDto(wrongYaml)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testToDtoForDelegateSelectorsAtPipeline() {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(correctYamlWithPipelineLevelDelegateSelectors).build();
    PipelineConfig pipelineConfig = PipelineYamlDtoMapper.toDto(pipelineEntity);
    PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.getDelegateSelectors()).isNotNull();
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue()).hasSize(1);
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue().get(0));
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel_pipeline");
    assertThat(pipelineInfoConfig.getStages()).hasSize(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testToDtoForDelegateSelectorsAtPipelineAndStageWithEmptyList() {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(correctYamlWithPipelineLevelDelegateSelectorsEmptyList).build();
    PipelineConfig pipelineConfig = PipelineYamlDtoMapper.toDto(pipelineEntity);
    PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.getDelegateSelectors()).isNotNull();
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testToDtoForMoreThanOneDelegateSelectorsAtPipeline() {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(correctYamlWithPipelineLevelDelegateSelectorsMoreThanOne).build();
    PipelineConfig pipelineConfig = PipelineYamlDtoMapper.toDto(pipelineEntity);
    PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    assertThat(pipelineInfoConfig.getDelegateSelectors()).isNotNull();
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue()).hasSize(2);
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue().get(0).getDelegateSelectors())
        .isEqualTo("sel_pipeline");
    assertThat(pipelineInfoConfig.getDelegateSelectors().getValue().get(1).getDelegateSelectors())
        .isEqualTo("sel2_pipeline");
  }
}
