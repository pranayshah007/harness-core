/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.pipelinestage.PipelineStageNode;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageFilterCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock PMSPipelineServiceImpl pmsPipelineService;
  @InjectMocks PipelineStageFilterCreator pipelineStageFilterCreator;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldValidatePipelineStageFilterCreator() {
    Set<String> stageTypes = pipelineStageFilterCreator.getSupportedStageTypes();
    assertThat(stageTypes).isNotEmpty();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldValidatePipelineStageGetFilter() {
    PipelineStageNode customStageNode = new PipelineStageNode();
    PipelineFilter filter =
        pipelineStageFilterCreator.getFilter(FilterCreationContext.builder().build(), customStageNode);
    assertThat(filter).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldValidatePipelineStageFieldClass() {
    assertThat(pipelineStageFilterCreator.getFieldClass()).isEqualTo(PipelineStageNode.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldHandleNode() throws IOException {
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId("acc").setOrgId("org").setProjectId("project").build())
            .currentField(pipelineStageYamlField)
            .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true);

    GitEntityInfo gitRequestParamsInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitRequestParamsInfo.getParentEntityAccountIdentifier()).isNull();
    assertThat(gitRequestParamsInfo.getParentEntityOrgIdentifier()).isNull();
    assertThat(gitRequestParamsInfo.getParentEntityProjectIdentifier()).isNull();

    FilterCreationResponse filterCreationResponse = pipelineStageFilterCreator.handleNode(
        filterCreationContext, YamlUtils.read(yamlField, PipelineStageNode.class));

    gitRequestParamsInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitRequestParamsInfo.getParentEntityAccountIdentifier()).isEqualTo("acc");
    assertThat(gitRequestParamsInfo.getParentEntityOrgIdentifier()).isEqualTo("org");
    assertThat(gitRequestParamsInfo.getParentEntityProjectIdentifier()).isEqualTo("project");

    assertThat(filterCreationResponse.getReferredEntities().size()).isEqualTo(1);
    EntityDetailProtoDTO entityDetailProtoDTO = filterCreationResponse.getReferredEntities().get(0);
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(EntityTypeProtoEnum.PIPELINES);
    assertThat(entityDetailProtoDTO.getIdentifierRef())
        .isEqualTo(IdentifierRefProtoDTO.newBuilder()
                       .setAccountIdentifier(StringValue.of("acc"))
                       .setOrgIdentifier(StringValue.of("org"))
                       .setProjectIdentifier(StringValue.of("project"))
                       .setIdentifier(StringValue.of("childPipeline"))
                       .build());

    // case2: pipeline stage config as null
    String yamlFieldWithoutSpec = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  inputs: \n"
        + "     dummy: dummy\n"
        + "  inputSetReferences: \n"
        + "     - ref1\n"
        + "  project: \"project\"\n";

    pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlFieldWithoutSpec);
    FilterCreationContext filterCreationContextCase2 =
        FilterCreationContext.builder()
            .setupMetadata(SetupMetadata.newBuilder().setAccountId("acc").setOrgId("org").setProjectId("org").build())
            .currentField(pipelineStageYamlField)
            .build();

    assertThatThrownBy(()
                           -> pipelineStageFilterCreator.handleNode(filterCreationContextCase2,
                               YamlUtils.read(yamlFieldWithoutSpec, PipelineStageNode.class)))
        .isInstanceOf(InvalidRequestException.class);
  }
}
