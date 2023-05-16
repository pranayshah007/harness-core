/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.ANKIT_TIWARI;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetFunctorTest extends CategoryTest {
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;

  @InjectMocks private InputSetFunctor inputSetFunctor;

  String inputYaml = "pipeline:\n"
      + "  identifier: trialselective\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: Test1\n"
      + "        type: Custom\n"
      + "        spec:\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  identifier: Wait_1\n"
      + "                  type: Wait\n"
      + "                  spec:\n"
      + "                    duration: 1m\n"
      + "    - parallel:\n"
      + "        - stage:\n"
      + "            identifier: test2\n"
      + "            type: Custom\n"
      + "            spec:\n"
      + "              execution:\n"
      + "                steps:\n"
      + "                  - step:\n"
      + "                      identifier: Wait_1\n"
      + "                      type: Wait\n"
      + "                      spec:\n"
      + "                        duration: 1m\n";

  Ambiance ambiance = Ambiance.newBuilder()
                          .putSetupAbstractions("accountId", "accountId")
                          .putSetupAbstractions("projectIdentifier", "projectId")
                          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                          .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void testBind() {
    on(inputSetFunctor).set("ambiance", ambiance);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .inputSetYaml(inputYaml)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.WEBHOOK)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("system").build())
                                      .build())
            .build();

    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(any(), any());

    Map<String, Object> inputSetMap = (Map<String, Object>) inputSetFunctor.bind();

    Map<String, Object> pipeline = (Map<String, Object>) inputSetMap.get("pipeline");

    assertEquals(pipeline.get("identifier"), "trialselective");
  }
}
