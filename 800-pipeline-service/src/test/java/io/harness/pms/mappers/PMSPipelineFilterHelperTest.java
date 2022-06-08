/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.PMSPipelineFilterHelper;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PMSPipelineFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetUpdateOperations() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().build();
    List<String> fieldsToBeUpdated = new ArrayList<>();
    fieldsToBeUpdated.add(PipelineEntityKeys.yaml);
    fieldsToBeUpdated.add(PipelineEntityKeys.lastUpdatedAt);
    fieldsToBeUpdated.add(PipelineEntityKeys.deleted);
    fieldsToBeUpdated.add(PipelineEntityKeys.name);
    fieldsToBeUpdated.add(PipelineEntityKeys.description);
    fieldsToBeUpdated.add(PipelineEntityKeys.tags);
    fieldsToBeUpdated.add(PipelineEntityKeys.filters);
    fieldsToBeUpdated.add(PipelineEntityKeys.stageCount);
    fieldsToBeUpdated.add(PipelineEntityKeys.stageNames);
    fieldsToBeUpdated.add(PipelineEntityKeys.allowStageExecutions);

    for (String field : fieldsToBeUpdated) {
      assertThat(true).isEqualTo(PMSPipelineFilterHelper.getUpdateOperations(pipelineEntity, 0L).modifies(field));
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateFieldsInDBEntry() {
    PipelineEntity fromDB = PipelineEntity.builder()
                                .uuid("uuid")
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("pr")
                                .identifier("id")
                                .yaml("oldYaml")
                                .createdAt(1L)
                                .lastUpdatedAt(2L)
                                .deleted(false)
                                .name("oldName")
                                .description("oldDec")
                                .tags(Collections.emptyList())
                                .version(22L)
                                .stageCount(1)
                                .stageNames(Collections.singletonList("s1"))
                                .allowStageExecutions(true)
                                .build();
    PipelineEntity fieldsToUpdate = PipelineEntity.builder()
                                        .accountId("acc")
                                        .orgIdentifier("org")
                                        .projectIdentifier("pr")
                                        .identifier("id")
                                        .yaml("not the oldYaml")
                                        .deleted(false)
                                        .name("New Name")
                                        .description("New Dec")
                                        .tags(Collections.singletonList(NGTag.builder().build()))
                                        .stageCount(2)
                                        .stageNames(Arrays.asList("s11", "s12"))
                                        .allowStageExecutions(false)
                                        .build();
    long timeOfUpdate = 10L;
    PipelineEntity pipelineEntity = PMSPipelineFilterHelper.updateFieldsInDBEntry(fromDB, fieldsToUpdate, timeOfUpdate);
    assertThat(pipelineEntity.getYaml()).isEqualTo("not the oldYaml");
    assertThat(pipelineEntity.getName()).isEqualTo("New Name");
    assertThat(pipelineEntity.getDescription()).isEqualTo("New Dec");
    assertThat(pipelineEntity.getTags()).isEqualTo(Collections.singletonList(NGTag.builder().build()));
    assertThat(pipelineEntity.getStageCount()).isEqualTo(2);
    assertThat(pipelineEntity.getStageNames()).containsExactly("s11", "s12");
    assertThat(pipelineEntity.getAllowStageExecutions()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUpdateOperationsForOnboardingToInline() {
    Update updateOperationsForOnboardingToInline = PMSPipelineFilterHelper.getUpdateOperationsForOnboardingToInline();
    Document updateObject = updateOperationsForOnboardingToInline.getUpdateObject();
    assertThat(updateObject.size()).isEqualTo(1);
    Document setObject = (Document) updateObject.get("$set");
    assertThat(setObject.size()).isEqualTo(1);
    assertThat(setObject.containsKey("storeType")).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetCriteriaForAllPipelinesInProject() {
    String acc = "acc";
    String org = "org";
    String proj = "proj";
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForAllPipelinesInProject(acc, org, proj);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.size()).isEqualTo(3);
    assertThat(criteriaObject.containsKey(PipelineEntityKeys.accountId)).isTrue();
    assertThat(criteriaObject.containsKey(PipelineEntityKeys.orgIdentifier)).isTrue();
    assertThat(criteriaObject.containsKey(PipelineEntityKeys.projectIdentifier)).isTrue();
  }
}
