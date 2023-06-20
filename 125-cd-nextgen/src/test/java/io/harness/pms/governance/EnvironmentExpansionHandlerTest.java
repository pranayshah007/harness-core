/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnvironmentExpansionHandlerTest extends CategoryTest {
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private ConnectorService connectorService;
  @Mock private KryoSerializer kryoSerializer;
  private final EnvironmentExpansionUtils utils = new EnvironmentExpansionUtils();
  @InjectMocks private EnvironmentExpansionHandler expansionHandler = new EnvironmentExpansionHandler();
  private AutoCloseable mocks;

  private final ExpansionRequestMetadata expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                                                        .setAccountId("accountId")
                                                                        .setOrgId("orgId")
                                                                        .setProjectId("projectId")
                                                                        .build();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    Reflect.on(expansionHandler).set("utils", utils);
    Reflect.on(utils).set("connectorService", connectorService);
    Reflect.on(utils).set("kryoSerializer", kryoSerializer);

    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .connector(ConnectorInfoDTO.builder()
                                    .name("k8s_connector_name")
                                    .connectorConfig(
                                        KubernetesClusterConfigDTO.builder().delegateSelectors(Set.of("del-1")).build())
                                    .build())
                     .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), eq("k8s_connector"));

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .name("k8s_connector_name")
                                            .connectorConfig(
                                                AwsConnectorDTO.builder().delegateSelectors(Set.of("del-1")).build())
                                            .build())
                             .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), eq("aws-connector"));

    doReturn(Optional.of(Environment.builder().id("my_environment").name("dev_environment").build()))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), eq("my_environment"), anyBoolean());

    InfrastructureConfig config1 = InfrastructureConfig.builder()
                                       .infrastructureDefinitionConfig(
                                           InfrastructureDefinitionConfig.builder()
                                               .identifier("my_infra")
                                               .type(InfrastructureType.ECS)
                                               .spec(EcsInfrastructure.builder()
                                                         .cluster(ParameterField.createValueField("us-east-1-cluster"))
                                                         .connectorRef(ParameterField.createValueField("aws-connector"))
                                                         .region(ParameterField.createValueField("us-east-1"))
                                                         .build())
                                               .build())
                                       .build();
    doReturn(Optional.of(InfrastructureEntity.builder()
                             .identifier("my_infra")
                             .type(InfrastructureType.ECS)
                             .yaml(YamlUtils.writeYamlString(config1))
                             .build()))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), eq("my_environment"), eq("my_infra"));

    InfrastructureConfig config2 =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .identifier("my_infra_with_ns_runtime")
                    .type(InfrastructureType.KUBERNETES_DIRECT)
                    .spec(K8SDirectInfrastructure.builder()
                              .connectorRef(ParameterField.createValueField("k8s_connector"))
                              .namespace(
                                  ParameterField.<String>builder().expression(true).expressionValue("<+input>").build())
                              .releaseName(
                                  ParameterField.<String>builder().expression(true).expressionValue("<+input>").build())
                              .build())
                    .build())
            .build();
    doReturn(Optional.of(InfrastructureEntity.builder()
                             .identifier("my_infra_with_ns_runtime")
                             .type(InfrastructureType.KUBERNETES_DIRECT)
                             .yaml(YamlUtils.writeYamlString(config2))
                             .build()))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), eq("my_environment"), eq("my_infra_with_ns_runtime"));

    InfrastructureConfig configWithoutConnectorRef =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .identifier("infra_without_connectorRef")
                    .type(InfrastructureType.KUBERNETES_DIRECT)
                    .spec(K8SDirectInfrastructure.builder()
                              .connectorRef(ParameterField.createValueField(null))
                              .namespace(
                                  ParameterField.<String>builder().expression(true).expressionValue("default").build())
                              .releaseName(ParameterField.<String>builder()
                                               .expression(true)
                                               .expressionValue("release_name")
                                               .build())
                              .build())
                    .build())
            .build();
    doReturn(Optional.of(InfrastructureEntity.builder()
                             .identifier("infra_without_connectorRef")
                             .type(InfrastructureType.KUBERNETES_DIRECT)
                             .yaml(YamlUtils.writeYamlString(configWithoutConnectorRef))
                             .build()))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), eq("my_environment"), eq("infra_without_connectorRef"));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expand() throws IOException {
    String environmentYaml = IOUtils.resourceToString(
        "governance/environment.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.getKey()).isEqualTo("infrastructure");
    assertThat(expand.getPlacement()).isEqualTo(ExpansionPlacementStrategy.PARALLEL);

    InfrastructureExpandedValue value = (InfrastructureExpandedValue) expand.getValue();

    String expectedJson = IOUtils.resourceToString(
        "governance/expected/expandExpected.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    assertThat(value.getKey()).isEqualTo("infrastructure");
    assertThat(value.toJson()).isEqualTo(expectedJson);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandInfraInputs_0() throws IOException {
    String environmentYaml = IOUtils.resourceToString(
        "governance/environmentWithInfraInputs.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.getKey()).isEqualTo("infrastructure");
    assertThat(expand.getPlacement()).isEqualTo(ExpansionPlacementStrategy.PARALLEL);

    InfrastructureExpandedValue value = (InfrastructureExpandedValue) expand.getValue();

    String expectedJson = IOUtils.resourceToString(
        "governance/expected/expectedInputsMerged.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());

    assertThat(value.getKey()).isEqualTo("infrastructure");
    assertThat(value.toJson()).isEqualTo(expectedJson);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandInfraInputs_1() throws IOException {
    String environmentYaml = IOUtils.resourceToString("governance/envWithInfrastructureDefinitionInputs.json",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.getKey()).isEqualTo("infrastructure");
    assertThat(expand.getPlacement()).isEqualTo(ExpansionPlacementStrategy.PARALLEL);

    InfrastructureExpandedValue value = (InfrastructureExpandedValue) expand.getValue();

    String expectedJson = IOUtils.resourceToString(
        "governance/expected/expectedInputsMerged.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());

    assertThat(value.getKey()).isEqualTo("infrastructure");
    assertThat(value.toJson()).isEqualTo(expectedJson);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandEnvExpression() throws IOException {
    String environmentYaml = IOUtils.resourceToString(
        "governance/environmentExpression.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.isSuccess()).isFalse();
    assertThat(expand.getErrorMessage()).contains("environmentRef is an expression");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandInfraNotFound() throws IOException {
    String environmentYaml = IOUtils.resourceToString(
        "governance/environment.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    mockInfraNotFound();
    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.isSuccess()).isFalse();
    assertThat(expand.getErrorMessage())
        .contains("Infrastructure Definition my_infra does not exist in environment my_environment");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandUnexpectedEnvYaml() {
    JsonNode jsonNode =
        JsonUtils.asObject("{\"key\": \"some yaml I dont understand\"}", new TypeReference<ObjectNode>() {});

    mockInfraNotFound();
    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.isSuccess()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandWithInfrastructureDefinition() throws IOException {
    String environmentYaml = IOUtils.resourceToString(
        "governance/envWithInfrastructureDefinition.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.getKey()).isEqualTo("infrastructure");
    assertThat(expand.getPlacement()).isEqualTo(ExpansionPlacementStrategy.PARALLEL);

    InfrastructureExpandedValue value = (InfrastructureExpandedValue) expand.getValue();

    String expectedJson = IOUtils.resourceToString("governance/expected/expandWithInfratructureDefinition.json",
        StandardCharsets.UTF_8, this.getClass().getClassLoader());
    assertThat(value.getKey()).isEqualTo("infrastructure");
    assertThat(value.toJson()).isEqualTo(expectedJson);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expandInfraWithoutConnector() throws IOException {
    String environmentYaml = IOUtils.resourceToString(
        "governance/infraWithoutConnectorRef.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environment");

    assertThat(expand.getKey()).isEqualTo("infrastructure");
    assertThat(expand.getPlacement()).isEqualTo(ExpansionPlacementStrategy.PARALLEL);

    InfrastructureExpandedValue value = (InfrastructureExpandedValue) expand.getValue();

    String expectedJson = IOUtils.resourceToString(
        "governance/expected/infraWithoutConnector.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    assertThat(value.getKey()).isEqualTo("infrastructure");
    assertThat(value.toJson()).isEqualTo(expectedJson);
  }

  private void mockInfraNotFound() {
    doReturn(Optional.empty())
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());
  }
}
