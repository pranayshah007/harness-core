/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.environment.EnvironmentPlanCreatorHelper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class DeploymentStagePMSPlanCreatorV2Test extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Inject DeploymentStagePMSPlanCreatorV2 deploymentStagePMSPlanCreator;

  private YamlField getYamlFieldFromPath(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }

  private String getYamlFromPath(String path) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    return new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddCDExecutionDependencies() throws IOException {
    YamlField executionField = getYamlFieldFromPath("cdng/plan/service.yml");

    String executionNodeId = executionField.getNode().getUuid();
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    deploymentStagePMSPlanCreator.addCDExecutionDependencies(planCreationResponseMap, executionField);
    assertThat(planCreationResponseMap.containsKey(executionNodeId)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchEnvironmentPlanCreatorConfigYaml() throws IOException {
    YamlField environmentYamlV2 = getYamlFieldFromPath("cdng/plan/environment/environmentYamlV2WithInfra.yml");

    String envPlanCreatorConfigYaml =
        getYamlFromPath("cdng/plan/environment/environmentPlanCreatorConfigWithInfra.yml");
    EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
        YamlUtils.read(envPlanCreatorConfigYaml, EnvironmentPlanCreatorConfig.class);
    YamlField updatedEnvironmentYamlField = EnvironmentPlanCreatorHelper.fetchEnvironmentPlanCreatorConfigYaml(
        environmentPlanCreatorConfig, environmentYamlV2);
    assertThat(updatedEnvironmentYamlField).isNotNull();
    assertThat(updatedEnvironmentYamlField.getNode().getFieldName()).isEqualTo(YamlTypes.ENVIRONMENT_YAML);
    assertThat(updatedEnvironmentYamlField.getNode().getField("environmentRef").getNode().asText())
        .isEqualTo(environmentPlanCreatorConfig.getEnvironmentRef().getValue());
    List<YamlNode> infrastructureDefinitions =
        updatedEnvironmentYamlField.getNode().getField("infrastructureDefinitions").getNode().asArray();
    assertThat(infrastructureDefinitions.size()).isEqualTo(2);
    assertThat(infrastructureDefinitions.get(0).getField("ref").getNode().asText())
        .isEqualTo(environmentPlanCreatorConfig.getInfrastructureDefinitions().get(0).getRef());
    assertThat(infrastructureDefinitions.get(1).getField("ref").getNode().asText())
        .isEqualTo(environmentPlanCreatorConfig.getInfrastructureDefinitions().get(1).getRef());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddEnvironmentV2Dependency() throws IOException {
    YamlField environmentYamlV2 = getYamlFieldFromPath("cdng/plan/environment/environmentYamlV2WithInfra.yml");

    String envPlanCreatorConfigYaml =
        getYamlFromPath("cdng/plan/environment/environmentPlanCreatorConfigWithInfra.yml");
    EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
        YamlUtils.read(envPlanCreatorConfigYaml, EnvironmentPlanCreatorConfig.class);
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    EnvironmentPlanCreatorHelper.addEnvironmentV2Dependency(planCreationResponseMap, environmentPlanCreatorConfig,
        environmentYamlV2, false, "environmentUuid", "infraSectionUuid", "serviceSpecNodeUuid", kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    String key = planCreationResponseMap.keySet().iterator().next();
    assertThat(planCreationResponseMap.get(key).getYamlUpdates().getFqnToYamlCount()).isEqualTo(1);
    assertThat(planCreationResponseMap.get(key).getDependencies().getDependenciesMap().size()).isEqualTo(1);

    Map<String, ByteString> dependencyMetadata =
        planCreationResponseMap.get(key).getDependencies().getDependencyMetadataMap().get(key).getMetadataMap();

    assertThat(dependencyMetadata.size()).isEqualTo(4);
    assertThat(dependencyMetadata.containsKey(YamlTypes.UUID)).isTrue();
    assertThat(dependencyMetadata.get(YamlTypes.UUID))
        .isEqualTo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(key)));

    assertThat(dependencyMetadata.containsKey(YamlTypes.NEXT_UUID)).isTrue();
    assertThat(dependencyMetadata.get(YamlTypes.NEXT_UUID))
        .isEqualTo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes("serviceSpecNodeUuid")));

    assertThat(dependencyMetadata.containsKey(YamlTypes.INFRA_SECTION_UUID)).isTrue();
    assertThat(dependencyMetadata.get(YamlTypes.INFRA_SECTION_UUID))
        .isEqualTo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes("infraSectionUuid")));
  }
}
