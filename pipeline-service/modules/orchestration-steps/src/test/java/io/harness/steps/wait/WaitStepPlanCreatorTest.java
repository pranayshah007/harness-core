/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepPlanCreatorTest extends OrchestrationStepsTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private WaitStepPlanCreator waitStepPlanCreator;

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> set = waitStepPlanCreator.getSupportedStepTypes();
    assertEquals(set.size(), 1);
    assertTrue(set.contains("Wait"));
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertEquals(waitStepPlanCreator.getFieldClass(), WaitStepNode.class);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);

    String childNodeUuid = generateUuid();

    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YAMLFieldNameConstants.CHILD_NODE_OF_SPEC, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(childNodeUuid)));
    PlanCreationContext ctx =
        PlanCreationContext.builder()
            .currentField(yamlField)
            .dependency(Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
            .build();
    WaitStepNode waitStepNode = new WaitStepNode();
    WaitStepInfo waitStepInfo =
        WaitStepInfo.infoBuilder()
            .duration(ParameterField.createValueField(Timeout.builder().timeoutString("10m").build()))
            .uuid(uuid)
            .build();
    waitStepNode.setUuid(uuid);
    waitStepNode.setWaitStepInfo(waitStepInfo);
    PlanCreationResponse planForSpecField = waitStepPlanCreator.createPlanForField(ctx, waitStepNode);
    assertNotNull(planForSpecField);
    assertNotNull(planForSpecField.getPlanNode());
    PlanNode planNode = planForSpecField.getPlanNode();
    assertEquals(planNode.getStepType(), StepSpecTypeConstants.WAIT_STEP_TYPE);
    assertNotNull(planNode.getStepParameters());
    StepElementParameters stepElementParameters = (StepElementParameters) planNode.getStepParameters();
    WaitStepParameters waitStepParameters = (WaitStepParameters) stepElementParameters.getSpec();
    assertEquals(waitStepParameters.duration.getValue(), "10m");
    assertEquals(planNode.getFacilitatorObtainments().size(), 1);
    assertEquals(
        planNode.getFacilitatorObtainments().get(0).getType().getType(), OrchestrationFacilitatorType.WAIT_STEP);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetStepParameters() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);

    String childNodeUuid = generateUuid();

    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YAMLFieldNameConstants.CHILD_NODE_OF_SPEC, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(childNodeUuid)));
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(yamlField)
                                  .dependency(Dependency.newBuilder().putAllMetadata(metadataDependency).build())
                                  .build();
    WaitStepNode waitStepNode = new WaitStepNode();
    WaitStepInfo waitStepInfo =
        WaitStepInfo.infoBuilder()
            .duration(ParameterField.createValueField(Timeout.builder().timeoutString("10m").build()))
            .uuid(uuid)
            .build();
    waitStepNode.setUuid(uuid);
    waitStepNode.setWaitStepInfo(waitStepInfo);
    StepElementParameters stepElementParameters =
        (StepElementParameters) waitStepPlanCreator.getStepParameters(ctx, waitStepNode);
    WaitStepParameters waitStepParameters = (WaitStepParameters) stepElementParameters.getSpec();
    assertEquals(waitStepParameters.duration.getValue(), "10m");
  }
}
