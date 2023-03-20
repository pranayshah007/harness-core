/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.mappers;

import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.SAHIL;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdvisorObtainmentList;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PlanNodeProtoMapperTest extends PmsSdkCoreTestBase {
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks @Inject PlanNodeProtoMapper planNodeProtoMapper;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testToPlanNodeProtoWithDecoratedFields() {
    PlanNodeBuilder planNodeBuilder = PlanNode.builder()
                                          .name("name")
                                          .stageFqn("fqn")
                                          .uuid("uuid")
                                          .stepType(StepType.newBuilder().getDefaultInstanceForType())
                                          .identifier("identifier")
                                          .skipExpressionChain(false)
                                          .skipGraphType(SkipType.SKIP_NODE)
                                          .skipUnresolvedExpressionsCheck(true)
                                          .whenCondition("when")
                                          .skipCondition("skip")
                                          .group("group");
    PlanNodeProto.Builder response = PlanNodeProto.newBuilder()
                                         .setName("name")
                                         .setStageFqn("fqn")
                                         .setUuid("uuid")
                                         .setServiceName(PMS_SDK_CORE_SERVICE_NAME)
                                         .setStepType(StepType.newBuilder().getDefaultInstanceForType())
                                         .setIdentifier("identifier")
                                         .setSkipExpressionChain(false)
                                         .setExpressionMode(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)
                                         .setSkipType(SkipType.SKIP_NODE)
                                         .setSkipUnresolvedExpressionsCheck(true)
                                         .setWhenCondition("when")
                                         .setSkipCondition("skip")
                                         .setGroup("group");
    assertThat(planNodeProtoMapper.toPlanNodeProtoWithDecoratedFields(planNodeBuilder.build()))
        .isEqualTo(response.build());

    // setting executionInputTemplate to some value. So planNodeProto should have this field.
    planNodeBuilder.executionInputTemplate("executionInputTemplate");
    assertThat(planNodeProtoMapper.toPlanNodeProtoWithDecoratedFields(planNodeBuilder.build()))
        .isNotEqualTo(response.build());
    ExpressionMode expressionMode = ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
    assertThat(
        planNodeProtoMapper.toPlanNodeProtoWithDecoratedFields(planNodeBuilder.expressionMode(expressionMode).build())
            .getExpressionMode())
        .isEqualTo(expressionMode);
    response.setExpressionMode(expressionMode);
    response.setExecutionInputTemplate("executionInputTemplate");
    assertEquals(planNodeProtoMapper.toPlanNodeProtoWithDecoratedFields(planNodeBuilder.build()), response.build());

    // adding rollback mode advisors
    PlanNode withRBAdvisor = planNodeBuilder
                                 .advisorObtainmentForExecutionMode(POST_EXECUTION_ROLLBACK,
                                     Collections.singletonList(AdviserObtainment.getDefaultInstance()))
                                 .advisorObtainmentForExecutionMode(PIPELINE_ROLLBACK,
                                     Collections.singletonList(AdviserObtainment.getDefaultInstance()))
                                 .build();
    Map<String, AdvisorObtainmentList> adviserObtainmentsMap =
        planNodeProtoMapper.toPlanNodeProtoWithDecoratedFields(withRBAdvisor)
            .getAdviserObtainmentsForExecutionModeMap();
    assertThat(adviserObtainmentsMap).hasSize(2);
    assertThat(adviserObtainmentsMap).containsKeys(POST_EXECUTION_ROLLBACK.name(), PIPELINE_ROLLBACK.name());
  }
}
