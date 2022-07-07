/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.NGSpecStep;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class SpecNodePlanCreatorTest extends OrchestrationStepsTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject SpecNodePlanCreator specNodePlanCreator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(specNodePlanCreator.getFieldClass()).isEqualTo(YamlField.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = specNodePlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YAMLFieldNameConstants.SPEC)).isTrue();
    assertThat(supportedTypes.get(YAMLFieldNameConstants.SPEC).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.SPEC).contains(PlanCreatorUtils.ANY_TYPE)).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + generateUuid() + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);

    String childNodeUuid = generateUuid();

    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YAMLFieldNameConstants.CHILD_NODE_OF_SPEC, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(childNodeUuid)));
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(yamlField)
                                  .dependency(Dependency.newBuilder().putAllMetadata(metadataDependency).build())
                                  .build();

    PlanCreationResponse planForSpecField = specNodePlanCreator.createPlanForField(ctx, yamlField);
    assertThat(planForSpecField).isNotNull();
    assertThat(planForSpecField.getPlanNode()).isNotNull();
    PlanNode planNode = planForSpecField.getPlanNode();
    assertThat(planNode.getStepType()).isEqualTo(NGSpecStep.STEP_TYPE);
    assertThat(planNode.getName()).isEqualTo(YAMLFieldNameConstants.SPEC);
    assertThat(planNode.getIdentifier()).isEqualTo(YAMLFieldNameConstants.SPEC);
  }
}
