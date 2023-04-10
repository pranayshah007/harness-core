/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MultiEnvironmentExpansionHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private ConnectorService connectorService;

  private AutoCloseable mocks;
  @InjectMocks private MultiEnvironmentExpansionHandler expansionHandler;

  private final ExpansionRequestMetadata expansionRequestMetadata =
      ExpansionRequestMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build();

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void expand() throws IOException {
    mockInfraWithRuntimeInputs();

    String environmentYaml = IOUtils.resourceToString(
        "governance/environments.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    JsonNode jsonNode = JsonUtils.asObject(environmentYaml, new TypeReference<ObjectNode>() {});

    ExpansionResponse expand = expansionHandler.expand(jsonNode, expansionRequestMetadata, "stage/spec/environments");

    assertThat(expand.getKey()).isEqualTo("environments");
    assertThat(expand.getPlacement()).isEqualTo(ExpansionPlacementStrategy.REPLACE);

    MultiEnvExpandedValue value = (MultiEnvExpandedValue) expand.getValue();

    String expectedJson = IOUtils.resourceToString(
        "governance/expected/multienvironment.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
    assertThat(value.getKey()).isEqualTo("environments");
    assertThat(value.toJson()).isEqualTo(expectedJson);
  }

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    doReturn(
        List.of(ConnectorResponseDTO.builder()
                    .connector(ConnectorInfoDTO.builder()
                                   .identifier("k8s_connector")
                                   .name("k8s_connector_name")
                                   .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                                   .connectorConfig(
                                       KubernetesClusterConfigDTO.builder().delegateSelectors(Set.of("del-1")).build())
                                   .build())
                    .build()))
        .when(connectorService)
        .listbyFQN(anyString(), anyList());

    doReturn(Optional.of(Environment.builder()
                             .identifier("my_environment")
                             .name("dev_environment")
                             .description("description")
                             .projectIdentifier(PROJECT_ID)
                             .orgIdentifier(ORG_ID)
                             .accountId(ACCOUNT_ID)
                             .tag(NGTag.builder().key("prod").value("false").build())
                             .type(EnvironmentType.PreProduction)
                             .build()))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), eq("my_environment"), anyBoolean());
  }

  private void mockInfraWithRuntimeInputs() {
    InfrastructureConfig config0 =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .identifier("my_infra_with_ns_runtime")
                    .type(InfrastructureType.KUBERNETES_DIRECT)
                    .spec(K8SDirectInfrastructure.builder()
                              .connectorRef(ParameterField.createValueField("account.k8s_connector"))
                              .namespace(
                                  ParameterField.<String>builder().expression(true).expressionValue("<+input>").build())
                              .releaseName(
                                  ParameterField.<String>builder().expression(true).expressionValue("<+input>").build())
                              .build())
                    .build())
            .build();
    InfrastructureConfig config1 =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(InfrastructureDefinitionConfig.builder()
                                                .identifier("my_infra")
                                                .type(InfrastructureType.ECS)
                                                .spec(EcsInfrastructure.builder()
                                                          .cluster(ParameterField.createValueField("us-east-1-cluster"))
                                                          .region(ParameterField.createValueField("us-east-1"))
                                                          .build())
                                                .build())
            .build();
    doReturn(List.of(InfrastructureEntity.builder()
                         .identifier("my_infra_with_ns_runtime")
                         .type(InfrastructureType.KUBERNETES_DIRECT)
                         .yaml(YamlUtils.write(config0))
                         .build(),
                 InfrastructureEntity.builder()
                     .identifier("my_infra")
                     .type(InfrastructureType.ECS)
                     .yaml(YamlUtils.write(config1))
                     .build()))
        .when(infrastructureEntityService)
        .getAllInfrastructureFromIdentifierList(anyString(), anyString(), anyString(), eq("my_environment"), anyList());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }
}
