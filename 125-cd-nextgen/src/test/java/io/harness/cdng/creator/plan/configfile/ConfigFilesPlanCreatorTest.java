/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFiles;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.configfile.steps.ConfigFilesStep;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
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
public class ConfigFilesPlanCreatorTest extends CDNGTestBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject @InjectMocks ConfigFilesPlanCreator configFilesPlanCreator;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(configFilesPlanCreator.getFieldClass()).isEqualTo(ConfigFiles.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = configFilesPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.CONFIG_FILES)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.CONFIG_FILES).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    List<String> childrenNodeIds = Arrays.asList("childNodeIdentifier1", "childNodeIdentifier1");
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    PlanNode planForParentNode = configFilesPlanCreator.createPlanForParentNode(ctx, null, childrenNodeIds);
    assertThat(planForParentNode.getUuid()).isEqualTo(uuid);
    assertThat(planForParentNode.getStepType()).isEqualTo(ConfigFilesStep.STEP_TYPE);
    assertThat(planForParentNode.getIdentifier()).isEqualTo(YamlTypes.CONFIG_FILES);
    assertThat(planForParentNode.getName()).isEqualTo(PlanCreatorConstants.CONFIG_FILES_NODE_NAME);
    assertThat(planForParentNode.getStepParameters())
        .isEqualTo(ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddDependenciesForConfigFiles() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/configfiles/configFiles.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField configFilesYamlNodes = YamlUtils.readTree(yaml);

    List<YamlNode> yamlNodes = Optional.of(configFilesYamlNodes.getNode().asArray()).orElse(Collections.emptyList());

    String configFileIdentifier = yamlNodes.get(0).getField(YamlTypes.CONFIG_FILE).getNode().getIdentifier();
    ConfigFileStepParameters configFileStepParameters = ConfigFileStepParameters.builder().build();
    configFilesPlanCreator.addDependenciesForIndividualConfigFile(
        configFileIdentifier, configFileStepParameters, configFilesYamlNodes, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);

    configFileIdentifier = yamlNodes.get(1).getField(YamlTypes.CONFIG_FILE).getNode().getIdentifier();
    configFilesPlanCreator.addDependenciesForIndividualConfigFile(
        configFileIdentifier, configFileStepParameters, configFilesYamlNodes, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(2);

    // should return the first from the list
    configFilesPlanCreator.addDependenciesForIndividualConfigFile(
        "notExistingIdentifier", configFileStepParameters, configFilesYamlNodes, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(3);
  }
}
