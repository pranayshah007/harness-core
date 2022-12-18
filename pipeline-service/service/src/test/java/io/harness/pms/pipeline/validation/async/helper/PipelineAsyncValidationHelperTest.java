/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.helper;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.rule.Owner;

import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public class PipelineAsyncValidationHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildFQN() {
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("id")
                                .build();
    assertThat(PipelineAsyncValidationHelper.buildFQN(entity, null)).isEqualTo("acc/org/proj/id");
    assertThat(PipelineAsyncValidationHelper.buildFQN(entity, "")).isEqualTo("acc/org/proj/id");
    assertThat(PipelineAsyncValidationHelper.buildFQN(entity, "br")).isEqualTo("acc/org/proj/id/br");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetCriteriaForUpdate() {
    Criteria c = PipelineAsyncValidationHelper.getCriteriaForUpdate("uuid123");
    Document criteriaObject = c.getCriteriaObject();
    assertThat(criteriaObject.size()).isEqualTo(1);
    assertThat(criteriaObject.containsKey("uuid")).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUpdateOperations() {
    Update updateOperations = PipelineAsyncValidationHelper.getUpdateOperations(
        ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    assertTrue(updateOperations.modifies("status"));
    assertTrue(updateOperations.modifies("result"));
  }
}
