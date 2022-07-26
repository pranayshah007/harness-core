/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertTrue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionSummaryKeysTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testKeys() {
    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(PipelineExecutionSummaryEntity.class);
    List<String> constants = new ArrayList<>();
    constants.add(PipelineExecutionSummaryKeys.accountId);
    constants.add(PipelineExecutionSummaryKeys.planExecutionId);
    constants.add(PipelineExecutionSummaryKeys.pipelineIdentifier);
    constants.add(PipelineExecutionSummaryKeys.orgIdentifier);
    constants.add(PipelineExecutionSummaryKeys.projectIdentifier);
    constants.add(PipelineExecutionSummaryKeys.startTs);
    constants.add(PipelineExecutionSummaryKeys.status);
    constants.add(PipelineExecutionSummaryKeys.endTs);
    constants.add(PipelineExecutionSummaryKeys.runSequence);
    constants.add(PipelineExecutionSummaryKeys.moduleInfo);
    constants.add(PipelineExecutionSummaryKeys.uuid);
    constants.add(PipelineExecutionSummaryKeys.name);
    constants.add(PipelineExecutionSummaryKeys.executionTriggerInfo);
    List<String> fieldNames = new ArrayList<>();
    for (Field field : fields) {
      fieldNames.add(field.getName());
    }
    for (String key : constants) {
      assertTrue(fieldNames.contains(key));
    }
  }
}
