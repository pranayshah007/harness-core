/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.serviceHook;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.servicehook.ServiceHooksPlanCreator;
import io.harness.cdng.hooks.ServiceHooks;
import io.harness.cdng.hooks.steps.ServiceHookStepParameters;
import io.harness.cdng.hooks.steps.ServiceHooksStep;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(CDP)
public class ServiceHooksPlanCreatorTest extends CDNGTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject @InjectMocks ServiceHooksPlanCreator serviceHooksPlanCreator;

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(serviceHooksPlanCreator.getFieldClass()).isEqualTo(ServiceHooks.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = serviceHooksPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.SERVICE_HOOKS)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_HOOKS).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    List<String> childrenNodeIds = Arrays.asList("childNodeIdentifier1", "childNodeIdentifier1");
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    PlanNode planForParentNode = serviceHooksPlanCreator.createPlanForParentNode(ctx, null, childrenNodeIds);
    assertThat(planForParentNode.getUuid()).isEqualTo(uuid);
    assertThat(planForParentNode.getStepType()).isEqualTo(ServiceHooksStep.STEP_TYPE);
    assertThat(planForParentNode.getIdentifier()).isEqualTo(YamlTypes.SERVICE_HOOKS);
    assertThat(planForParentNode.getName()).isEqualTo(PlanCreatorConstants.SERVICE_HOOKS_NODE_NAME);
    assertThat(planForParentNode.getStepParameters())
        .isEqualTo(ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testAddDependenciesForServiceHooks() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    YamlField serviceFilesYamlNodes = readYaml("cdng/plan/serviceHooks/serviceHooks.yml");

    List<YamlNode> yamlNodes = Optional.of(serviceFilesYamlNodes.getNode().asArray()).orElse(Collections.emptyList());

    String serviceHookIdentifier = yamlNodes.get(0).getField(YamlTypes.PRE_HOOK).getNode().getIdentifier();
    ServiceHookStepParameters serviceHookStepParameters = ServiceHookStepParameters.builder().build();
    serviceHooksPlanCreator.addDependenciesForIndividualServiceHook(
        serviceHookIdentifier, serviceHookStepParameters, serviceFilesYamlNodes, planCreationResponseMap);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);

    serviceHookIdentifier = yamlNodes.get(1).getField(YamlTypes.POST_HOOK).getNode().getIdentifier();
    serviceHooksPlanCreator.addDependenciesForIndividualServiceHook(
        serviceHookIdentifier, serviceHookStepParameters, serviceFilesYamlNodes, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(2);

    // should return the first from the list
    serviceHooksPlanCreator.addDependenciesForIndividualServiceHook(
        "notExistingIdentifier", serviceHookStepParameters, serviceFilesYamlNodes, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(3);
  }

  private YamlField readYaml(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }
}
