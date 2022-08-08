/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.configfile;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
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
public class IndividualConfigFilePlanCreatorTest extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Inject @InjectMocks IndividualConfigFilePlanCreator individualConfigFilePlanCreator;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(individualConfigFilePlanCreator.getFieldClass()).isEqualTo(ConfigFile.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = individualConfigFilePlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.CONFIG_FILE)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.CONFIG_FILE).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.CONFIG_FILE).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    String identifier = "configFileIdentifier";
    ConfigFileStepParameters configFileStepParameters =
        ConfigFileStepParameters.builder().identifier(identifier).build();

    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    metadataDependency.put(PlanCreatorConstants.CONFIG_FILE_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(configFileStepParameters)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    PlanCreationResponse planCreationResponse = individualConfigFilePlanCreator.createPlanForField(ctx, null);
    PlanNode planNode = planCreationResponse.getPlanNode();
    assertThat(planNode.getUuid()).isEqualTo(uuid);
    assertThat(planNode.getIdentifier()).isEqualTo(identifier);
    assertThat(planNode.getStepParameters()).isEqualTo(configFileStepParameters);
  }
}
