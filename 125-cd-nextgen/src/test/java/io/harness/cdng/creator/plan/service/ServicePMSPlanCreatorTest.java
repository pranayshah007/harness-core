package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.steps.ArtifactStep;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.infra.steps.EnvironmentStep;
import io.harness.cdng.licenserestriction.EnforcementValidator;
import io.harness.cdng.manifest.steps.ManifestStep;
import io.harness.cdng.manifest.steps.ManifestStepParameters;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.steps.ServiceConfigStep;
import io.harness.cdng.service.steps.ServiceConfigStepParameters;
import io.harness.cdng.service.steps.ServiceDefinitionStep;
import io.harness.cdng.service.steps.ServiceDefinitionStepParameters;
import io.harness.cdng.service.steps.ServiceSpecStep;
import io.harness.cdng.service.steps.ServiceSpecStepParameters;
import io.harness.cdng.service.steps.ServiceStep;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class ServicePMSPlanCreatorTest extends CDNGTestBase {
  @Mock private EnforcementValidator enforcementValidator;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @InjectMocks private ServicePMSPlanCreator servicePMSPlanCreator;

  @Before
  public void before() throws IllegalAccessException {
    FieldUtils.writeField(servicePMSPlanCreator, "enforcementValidator", enforcementValidator, true);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCreatePlanForServiceNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/service.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField field = YamlUtils.readTree(yaml);
    assertThat(field).isNotNull();

    ServiceConfig serviceConfig = YamlUtils.read(yaml, ServiceConfig.class);
    assertThat(serviceConfig).isNotNull();

    // infra field
    yamlFile = classLoader.getResourceAsStream("cdng/plan/infra.yml");
    assertThat(yamlFile).isNotNull();

    yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField infraField = YamlUtils.readTree(yaml);
    assertThat(infraField).isNotNull();

    PipelineInfrastructure infrastructure = YamlUtils.read(yaml, PipelineInfrastructure.class);
    assertThat(infrastructure).isNotNull();

    PipelineInfrastructure actualInfraConfig =
        InfrastructurePmsPlanCreator.getActualInfraConfig(infrastructure, infraField);
    PlanCreationContext ctx = getPlanCreationContext();
    PlanCreationResponse response = servicePMSPlanCreator.createPlanForServiceNode(field, serviceConfig, kryoSerializer,
        InfrastructurePmsPlanCreator.getInfraSectionStepParams(actualInfraConfig, ""), ctx);
    assertThat(response).isNotNull();
    String startingNodeId = response.getStartingNodeId();
    assertThat(startingNodeId).isNotNull();
    assertThat(startingNodeId.length()).isGreaterThan(0);

    Map<String, PlanNode> nodeMap = response.getNodes();
    assertThat(nodeMap.size()).isEqualTo(14);

    List<PlanNode> nodes = new ArrayList<>(nodeMap.values());
    List<PlanNode> artifactNodes = findNodes(nodes, ArtifactStep.STEP_TYPE);
    assertThat(artifactNodes.size()).isEqualTo(3);

    PlanNode sidecarsNode = findNodeByIdentifier(nodes, "sidecars");
    ForkStepParameters forkStepParameters = (ForkStepParameters) sidecarsNode.getStepParameters();
    assertThat(forkStepParameters.getParallelNodeIds().size()).isEqualTo(2);
    assertThat(forkStepParameters.getParallelNodeIds())
        .isSubsetOf(artifactNodes.stream().map(PlanNode::getUuid).collect(Collectors.toList()));

    PlanNode artifactsNode = findNodeByIdentifier(nodes, "artifacts");
    forkStepParameters = (ForkStepParameters) artifactsNode.getStepParameters();
    assertThat(forkStepParameters.getParallelNodeIds().size()).isEqualTo(2);
    assertThat(forkStepParameters.getParallelNodeIds()).contains(sidecarsNode.getUuid());

    List<PlanNode> manifestNodes = findNodes(nodes, ManifestStep.STEP_TYPE);
    assertThat(manifestNodes.size()).isEqualTo(3);

    PlanNode manifestsNode = findNodeByIdentifier(nodes, "manifests");
    forkStepParameters = (ForkStepParameters) manifestsNode.getStepParameters();
    assertThat(forkStepParameters.getParallelNodeIds().size()).isEqualTo(3);
    assertThat(forkStepParameters.getParallelNodeIds())
        .isSubsetOf(manifestNodes.stream().map(PlanNode::getUuid).collect(Collectors.toList()));

    PlanNode serviceSpecNode = findNode(nodes, ServiceSpecStep.STEP_TYPE);
    ServiceSpecStepParameters serviceSpecStepParameters =
        (ServiceSpecStepParameters) serviceSpecNode.getStepParameters();
    assertThat(serviceSpecStepParameters.getChildrenNodeIds())
        .containsExactly(artifactsNode.getUuid(), manifestsNode.getUuid());

    PlanNode environmentNode = findNode(nodes, EnvironmentStep.STEP_TYPE);
    PlanNode serviceDefNode = findNode(nodes, ServiceDefinitionStep.STEP_TYPE);
    ServiceDefinitionStepParameters serviceDefinitionStepParameters =
        (ServiceDefinitionStepParameters) serviceDefNode.getStepParameters();
    assertThat(serviceDefinitionStepParameters.getChildNodeId()).isEqualTo(environmentNode.getUuid());

    PlanNode serviceNode = findNode(nodes, ServiceStep.STEP_TYPE);
    PlanNode serviceConfigNode = findNode(nodes, ServiceConfigStep.STEP_TYPE);
    assertThat(serviceConfigNode.getUuid()).isEqualTo(startingNodeId);

    ServiceConfigStepParameters serviceConfigStepParameters =
        (ServiceConfigStepParameters) serviceConfigNode.getStepParameters();
    assertThat(serviceConfigStepParameters.getChildNodeId()).isEqualTo(serviceNode.getUuid());

    PlanNode primaryArtifactNode = findNodeByIdentifier(nodes, "primary");
    assertThat(primaryArtifactNode).isNotNull();

    ArtifactStepParameters artifactStepParameters = (ArtifactStepParameters) primaryArtifactNode.getStepParameters();
    assertThat(((DockerHubArtifactConfig) artifactStepParameters.getSpec()).getTag().getValue())
        .isEqualTo("stable-perl");
    assertThat(artifactStepParameters.getOverrideSets().size()).isEqualTo(1);
    assertThat(((DockerHubArtifactConfig) artifactStepParameters.getOverrideSets().get(0)).getTag().getValue())
        .isEqualTo("stable");
    assertThat(((DockerHubArtifactConfig) artifactStepParameters.getStageOverride()).getTag().getValue())
        .isEqualTo("latest");

    PlanNode sc1ArtifactNode = findNodeByIdentifier(nodes, "sc1");
    assertThat(sc1ArtifactNode).isNotNull();

    artifactStepParameters = (ArtifactStepParameters) sc1ArtifactNode.getStepParameters();
    assertThat(((DockerHubArtifactConfig) artifactStepParameters.getSpec()).getTag().getValue()).isEqualTo("35");
    assertThat(artifactStepParameters.getOverrideSets().size()).isEqualTo(0);
    assertThat(((DockerHubArtifactConfig) artifactStepParameters.getStageOverride()).getTag().getValue())
        .isEqualTo("33");

    PlanNode sc2ArtifactNode = findNodeByIdentifier(nodes, "sc2");
    assertThat(sc1ArtifactNode).isNotNull();

    artifactStepParameters = (ArtifactStepParameters) sc2ArtifactNode.getStepParameters();
    assertThat(((DockerHubArtifactConfig) artifactStepParameters.getSpec()).getTag().getValue()).isEqualTo("mainline");
    assertThat(artifactStepParameters.getOverrideSets().size()).isEqualTo(0);
    assertThat(artifactStepParameters.getStageOverride()).isNull();

    PlanNode m1Node = findNodeByIdentifier(nodes, "m1");
    assertThat(m1Node).isNotNull();

    ManifestStepParameters manifestStepParameters = (ManifestStepParameters) m1Node.getStepParameters();
    assertThat(((GithubStore) ((K8sManifest) manifestStepParameters.getSpec()).getStoreConfig()).getPaths().getValue())
        .containsExactly("random", "random2");
    assertThat(manifestStepParameters.getOverrideSets().size()).isEqualTo(1);
    assertThat(((GithubStore) ((K8sManifest) manifestStepParameters.getOverrideSets().get(0)).getStoreConfig())
                   .getPaths()
                   .getValue())
        .containsExactly("random2", "random3");
    assertThat(((GithubStore) ((K8sManifest) manifestStepParameters.getStageOverride()).getStoreConfig())
                   .getPaths()
                   .getValue())
        .containsExactly("random2", "random4");

    PlanNode m2Node = findNodeByIdentifier(nodes, "m2");
    assertThat(m2Node).isNotNull();

    manifestStepParameters = (ManifestStepParameters) m2Node.getStepParameters();
    assertThat(
        ((GithubStore) ((ValuesManifest) manifestStepParameters.getSpec()).getStoreConfig()).getPaths().getValue())
        .containsExactly("random");
    assertThat(manifestStepParameters.getOverrideSets().size()).isEqualTo(0);
    assertThat(manifestStepParameters.getStageOverride()).isNull();

    PlanNode m3Node = findNodeByIdentifier(nodes, "m3");
    assertThat(m3Node).isNotNull();

    manifestStepParameters = (ManifestStepParameters) m3Node.getStepParameters();
    assertThat(manifestStepParameters.getSpec()).isNull();
    assertThat(manifestStepParameters.getOverrideSets().size()).isEqualTo(1);
    assertThat(((GithubStore) ((K8sManifest) manifestStepParameters.getOverrideSets().get(0)).getStoreConfig())
                   .getPaths()
                   .getValue())
        .containsExactly("random4", "random3");
    assertThat(manifestStepParameters.getStageOverride()).isNull();
    verify(enforcementValidator)
        .validate("ACCOUNT_IDENTIFIER", "ORG_IDENTIFIER", "PROJECT_IDENTIFIER", "PIPELINE_IDENTIFIER", "YAML",
            "EXECUTION_UUID");
  }

  private PlanCreationContext getPlanCreationContext() {
    PlanCreationContext ctx = Mockito.mock(PlanCreationContext.class);
    doReturn(PlanCreationContextValue.newBuilder()
                 .setAccountIdentifier("ACCOUNT_IDENTIFIER")
                 .setProjectIdentifier("PROJECT_IDENTIFIER")
                 .setOrgIdentifier("ORG_IDENTIFIER")
                 .setMetadata(ExecutionMetadata.newBuilder()
                                  .setPipelineIdentifier("PIPELINE_IDENTIFIER")
                                  .setExecutionUuid("EXECUTION_UUID")
                                  .build())
                 .build())
        .when(ctx)
        .getMetadata();
    doReturn("YAML").when(ctx).getYaml();
    return ctx;
  }

  private List<PlanNode> findNodes(List<PlanNode> nodes, StepType stepType) {
    return nodes.stream().filter(n -> n.getStepType().equals(stepType)).collect(Collectors.toList());
  }

  private PlanNode findNode(List<PlanNode> nodes, StepType stepType) {
    List<PlanNode> filteredNodes =
        nodes.stream().filter(n -> n.getStepType().equals(stepType)).collect(Collectors.toList());
    assertThat(filteredNodes.size()).isEqualTo(1);
    return filteredNodes.get(0);
  }

  private PlanNode findNodeByIdentifier(List<PlanNode> nodes, String identifier) {
    List<PlanNode> filteredNodes =
        nodes.stream().filter(n -> n.getIdentifier().equals(identifier)).collect(Collectors.toList());
    assertThat(filteredNodes.size()).isEqualTo(1);
    return filteredNodes.get(0);
  }
}
