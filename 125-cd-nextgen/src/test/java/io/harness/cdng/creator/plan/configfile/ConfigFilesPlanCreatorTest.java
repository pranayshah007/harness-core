/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.ConfigFiles;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.configfile.steps.ConfigFilesStep;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.SshServiceSpec;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
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
import java.util.ArrayList;
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

    YamlField configFilesYamlNodes = readYaml("cdng/plan/configfiles/configFiles.yml");

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

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldCreateWithProperOrderV1() throws IOException {
    ServiceConfig serviceConfig =
        ServiceConfig.builder()
            .serviceDefinition(ServiceDefinition.builder()
                                   .serviceSpec(SshServiceSpec.builder()
                                                    .configFiles(Arrays.asList(configFile("cf1"), configFile("cf2"),
                                                        configFile("cf3"), configFile("cf4")))
                                                    .build())
                                   .build())
            .stageOverrides(StageOverridesConfig.builder()
                                .configFiles(Arrays.asList(
                                    configFile("cf1"), configFile("cf2"), configFile("cf3"), configFile("cf4")))
                                .build())
            .build();

    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.SERVICE_CONFIG, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceConfig)));

    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    YamlField configFilesYamlNodes = readYaml("cdng/plan/configfiles/configFiles.yml");

    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(configFilesYamlNodes).dependency(dependency).build();

    List<String> expectedListrOfIdentifier = Arrays.asList("cf4", "cf3", "cf2", "cf1");
    List<String> actualListOfIdentifier = new ArrayList<>();
    LinkedHashMap<String, PlanCreationResponse> response = configFilesPlanCreator.createPlanForChildrenNodes(ctx, null);
    assertThat(response.size()).isEqualTo(4);
    for (Map.Entry<String, PlanCreationResponse> entry : response.entrySet()) {
      actualListOfIdentifier.add(fetchConfigFileIdentifier(entry.getKey(), entry.getValue()));
    }
    assertThat(expectedListrOfIdentifier).isEqualTo(actualListOfIdentifier);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldCreateWithProperOrderV2() throws IOException {
    NGServiceV2InfoConfig serviceConfig =
        NGServiceV2InfoConfig.builder()
            .serviceDefinition(ServiceDefinition.builder()
                                   .serviceSpec(SshServiceSpec.builder()
                                                    .configFiles(Arrays.asList(configFile("cf1"), configFile("cf2"),
                                                        configFile("cf3"), configFile("cf4")))
                                                    .build())
                                   .build())
            .build();

    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.SERVICE_ENTITY, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceConfig)));

    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    YamlField configFilesYamlNodes = readYaml("cdng/plan/configfiles/configFiles.yml");

    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(configFilesYamlNodes).dependency(dependency).build();

    List<String> expectedListrOfIdentifier = Arrays.asList("cf4", "cf3", "cf2", "cf1");
    List<String> actualListOfIdentifier = new ArrayList<>();
    LinkedHashMap<String, PlanCreationResponse> response = configFilesPlanCreator.createPlanForChildrenNodes(ctx, null);
    assertThat(response.size()).isEqualTo(4);
    for (Map.Entry<String, PlanCreationResponse> entry : response.entrySet()) {
      actualListOfIdentifier.add(fetchConfigFileIdentifier(entry.getKey(), entry.getValue()));
    }
    assertThat(expectedListrOfIdentifier).isEqualTo(actualListOfIdentifier);
  }

  private ConfigFileWrapper configFile(String identifier) {
    return ConfigFileWrapper.builder()
        .configFile(ConfigFile.builder()
                        .identifier(identifier)
                        .spec(ConfigFileAttributes.builder()
                                  .store(ParameterField.createValueField(
                                      StoreConfigWrapper.builder()
                                          .type(StoreConfigType.HARNESS)
                                          .spec(HarnessStore.builder()
                                                    .files(ParameterField.createValueField(Arrays.asList("test")))
                                                    .build())
                                          .build()))
                                  .build())
                        .build())
        .build();
  }

  private YamlField readYaml(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }

  private String fetchConfigFileIdentifier(String uuid, PlanCreationResponse planCreationResponse) {
    Map<String, ByteString> metadataMap =
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(uuid).getMetadataMap();
    assertThat(metadataMap.size()).isEqualTo(2);
    assertThat(metadataMap.containsKey(YamlTypes.UUID)).isEqualTo(true);
    assertThat(metadataMap.containsKey(PlanCreatorConstants.CONFIG_FILE_STEP_PARAMETER)).isEqualTo(true);

    ConfigFileStepParameters stepParameters = (ConfigFileStepParameters) kryoSerializer.asInflatedObject(
        metadataMap.get(PlanCreatorConstants.CONFIG_FILE_STEP_PARAMETER).toByteArray());
    return stepParameters.getIdentifier();
  }
}
