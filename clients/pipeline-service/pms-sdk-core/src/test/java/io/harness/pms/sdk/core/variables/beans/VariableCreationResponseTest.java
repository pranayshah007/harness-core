/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables.beans;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.VariablesCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VariableCreationResponseTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testUpdateYamlInDependencies() {
    VariableCreationResponse variableCreationResponse = VariableCreationResponse.builder().build();
    variableCreationResponse.updateYamlInDependencies("updatedYaml");
    assertThat(variableCreationResponse.getDependencies().getYaml()).isEqualTo("updatedYaml");
    variableCreationResponse =
        VariableCreationResponse.builder().dependencies(Dependencies.newBuilder().build()).build();
    variableCreationResponse.updateYamlInDependencies("updatedYaml2");
    assertThat(variableCreationResponse.getDependencies().getYaml()).isEqualTo("updatedYaml2");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddResolvedDependency() {
    VariableCreationResponse variableCreationResponse = VariableCreationResponse.builder().build();
    variableCreationResponse.addResolvedDependency("yaml", "nodeId", "yamlPath");
    assertThat(variableCreationResponse.getResolvedDependencies().getDependenciesMap().size()).isEqualTo(1);
    assertThat(variableCreationResponse.getResolvedDependencies().getDependenciesMap().get("nodeId"))
        .isEqualTo("yamlPath");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddDependency() {
    VariableCreationResponse variableCreationResponse = VariableCreationResponse.builder().build();
    variableCreationResponse.addDependency("yaml", "nodeId", "yamlPath");
    assertThat(variableCreationResponse.getDependencies().getDependenciesMap().size()).isEqualTo(1);
    assertThat(variableCreationResponse.getDependencies().getDependenciesMap().get("nodeId")).isEqualTo("yamlPath");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddYamlPropertiesEmpty() {
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    yamlPropertiesMap.put("test", YamlProperties.newBuilder().build());
    VariableCreationResponse variableCreationResponse = VariableCreationResponse.builder().build();
    variableCreationResponse.addYamlProperties(yamlPropertiesMap);
    assertThat(variableCreationResponse.getYamlProperties()).isEqualTo(yamlPropertiesMap);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddYamlPropertiesNonEmpty() {
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    yamlPropertiesMap.put("test", YamlProperties.newBuilder().build());
    VariableCreationResponse variableCreationResponse =
        VariableCreationResponse.builder().yamlProperties(ImmutableMap.of()).build();
    variableCreationResponse.addYamlProperties(yamlPropertiesMap);
    assertThat(variableCreationResponse.getYamlProperties()).isEqualTo(yamlPropertiesMap);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testToBlobResponse() {
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    yamlPropertiesMap.put("test", YamlProperties.newBuilder().build());
    Map<String, YamlOutputProperties> yamlOutputPropertiesMap = new HashMap<>();
    yamlOutputPropertiesMap.put("test", YamlOutputProperties.newBuilder().build());
    Dependencies dependencies = Dependencies.newBuilder().setYaml("yaml").putDependencies("a", "b").build();
    VariableCreationResponse variableCreationResponse =
        VariableCreationResponse.builder()
            .dependencies(dependencies)
            .resolvedDependencies(dependencies)
            .yamlProperties(yamlPropertiesMap)
            .yamlOutputProperties(yamlOutputPropertiesMap)
            .yamlExtraProperty("a", YamlExtraProperties.newBuilder().build())
            .yamlUpdates(YamlUpdates.newBuilder().build())
            .build();
    VariablesCreationBlobResponse response =
        VariablesCreationBlobResponse.newBuilder()
            .setDeps(dependencies)
            .setResolvedDeps(dependencies)
            .putYamlProperties("test", YamlProperties.newBuilder().build())
            .putYamlOutputProperties("test", YamlOutputProperties.newBuilder().build())
            .putYamlExtraProperties("a", YamlExtraProperties.newBuilder().build())
            .setYamlUpdates(YamlUpdates.newBuilder().build())
            .build();
    assertThat(variableCreationResponse.toBlobResponse()).isEqualTo(response);
  }
}
