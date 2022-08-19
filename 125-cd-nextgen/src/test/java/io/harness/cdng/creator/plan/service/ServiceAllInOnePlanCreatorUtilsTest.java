package io.harness.cdng.creator.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ServiceAllInOnePlanCreatorUtilsTest {
  @Mock private KryoSerializer kryoSerializer;
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    Mockito.doReturn("some_string".getBytes(StandardCharsets.UTF_8))
        .when(kryoSerializer)
        .asBytes(ArgumentMatchers.any());
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
  public void addServiceNodeWithExpression() {
    ServicePlanCreatorV2Config config =
        ServicePlanCreatorV2Config.builder()
            .identifier(ParameterField.createValueField("my-service"))
            .serviceDefinition(
                ServiceDefinition.builder()
                    .type(ServiceDefinitionType.KUBERNETES)
                    .serviceSpec(KubernetesServiceSpec.builder()
                                     .manifests(Collections.emptyList())
                                     .artifacts(ArtifactListConfig.builder()
                                                    .primary(PrimaryArtifact.builder()
                                                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                                                 .spec(DockerHubArtifactConfig.builder().build())
                                                                 .build())
                                                    .build())
                                     .build())
                    .build())
            .build();
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap =
        ServiceAllInOnePlanCreatorUtils.addServiceNodeWithExpression(
            kryoSerializer, config, "envNodeId", "serviceSpecNodeId");

    List<PlanNode> collect =
        planCreationResponseMap.values().stream().map(PlanCreationResponse::getPlanNode).collect(Collectors.toList());
    assertThat(collect.stream().map(PlanNode::getIdentifier).collect(Collectors.toList()))
        .containsExactly("service", "artifacts", "serviceDefinition", "spec");
    assertThat(planCreationResponseMap).hasSize(4);
  }
}