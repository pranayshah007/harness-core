/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.serviceHook;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.configfile.IndividualConfigFilePlanCreator;
import io.harness.cdng.creator.plan.servicehook.IndividualServiceHookPlanCreator;
import io.harness.cdng.hooks.steps.ServiceHookStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDP)
public class IndividualServiceHookPlanCreatorTest extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Inject @InjectMocks IndividualServiceHookPlanCreator individualServiceHookPlanCreator;

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(individualServiceHookPlanCreator.getFieldClass()).isEqualTo(ConfigFile.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = individualServiceHookPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.PRE_HOOK)).isEqualTo(true);
    assertThat(supportedTypes.containsKey(YamlTypes.POST_HOOK)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.PRE_HOOK).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.POST_HOOK).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.PRE_HOOK).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.POST_HOOK).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    String identifier = "serviceHookIdentifier";
    ServiceHookStepParameters serviceHookStepParameters =
        ServiceHookStepParameters.builder().identifier(identifier).build();

    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    metadataDependency.put(PlanCreatorConstants.SERVICE_HOOK_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceHookStepParameters)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    PlanCreationResponse planCreationResponse = individualServiceHookPlanCreator.createPlanForField(ctx, null);
    PlanNode planNode = planCreationResponse.getPlanNode();
    assertThat(planNode.getUuid()).isEqualTo(uuid);
    assertThat(planNode.getIdentifier()).isEqualTo(identifier);
    assertThat(planNode.getStepParameters()).isEqualTo(serviceHookStepParameters);
  }
}
