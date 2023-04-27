/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.connector.services.ConnectorService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.persistence.HIterator;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.Builder;
import lombok.Value;
import org.jooq.tools.reflect.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnitParamsRunner.class)
public class DeploymentStageVariableCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ServiceEntityService serviceEntityService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  InfrastructureMapper infrastructureMapper = new InfrastructureMapper();
  @InjectMocks private DeploymentStageVariableCreator deploymentStageVariableCreator;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "default";
  private final String PROJ_IDENTIFIER = "svcenvrefactor";
  private final String ENV_IDENTIFIER = "environmentVariableTest";

  AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    Reflect.on(deploymentStageVariableCreator).set("infrastructureMapper", infrastructureMapper);
    Reflect.on(infrastructureMapper).set("connectorService", Mockito.mock(ConnectorService.class));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(deploymentStageVariableCreator.getFieldClass()).isEqualTo(DeploymentStageNode.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForParentNodes() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineWithServiceInfraVariableCreatorJson.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stageField = fullYamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(0)
                               .getField("stage");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = deploymentStageVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stageField).build(),
        YamlUtils.read(stageField.getNode().toString(), DeploymentStageNode.class));
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsAll(Arrays.asList("pipeline.stages.dep.spec.infrastructure.infrastructureDefinition.spec.releaseName",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.manifests.testsvc.spec.chartVersion",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.manifests.testsvc.spec.skipResourceVersioning",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.manifests.testsvc.spec.chartName",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.manifests.testsvc.spec.store.spec.connectorRef",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.postgres.spec.tagRegex",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.postgres.spec.imagePath",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.postgres.spec.tag",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.postgres.spec.connectorRef",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.connectorRef",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.imagePath",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tagRegex",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.variables.service_var2",
            "pipeline.stages.dep.spec.serviceConfig.serviceDefinition.spec.variables.service_var1",
            "pipeline.stages.dep.spec.infrastructure.allowSimultaneousDeployments",
            "pipeline.stages.dep.spec.infrastructure.infrastructureDefinition.spec.namespace",
            "pipeline.stages.dep.spec.infrastructure.infrastructureDefinition.spec.connectorRef",
            "pipeline.stages.dep.description", "pipeline.stages.dep.delegateSelectors",
            "pipeline.stages.dep.spec.serviceConfig.serviceRef", "pipeline.stages.dep.name",
            "pipeline.stages.dep.spec.infrastructure.environmentRef",
            "pipeline.stages.dep.spec.infrastructure.infrastructureKey"));

    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodesV2 =
        deploymentStageVariableCreator.createVariablesForChildrenNodesV2(
            VariableCreationContext.builder().currentField(stageField).build(),
            YamlUtils.read(stageField.getNode().toString(), DeploymentStageNode.class));

    String uuidForProvisionerInsideInfrastructureStep = "uvlCGOSEScCPv5Y4_-LbVg";
    String uuidForProvisionerStep = "mbSWldzKSzyN-n2VzcV_jg";
    String uuidForProvisionerRollbackStep = "R9VL1m9eQcuoeS2PH_or0g";

    assertThat(variablesForChildrenNodesV2.containsKey(uuidForProvisionerInsideInfrastructureStep)).isTrue();

    Map<String, String> provisionerMap = variablesForChildrenNodesV2.get(uuidForProvisionerInsideInfrastructureStep)
                                             .getDependencies()
                                             .getDependenciesMap();

    assertThat(provisionerMap.get(uuidForProvisionerStep)).containsSubsequence("provisioner/rollbackSteps");
    assertThat(provisionerMap.get(uuidForProvisionerRollbackStep)).containsSubsequence("provisioner/steps");

    String uuidForExecutionNode = "jN1lz-uVSIW8vFKXWumINA";
    assertThat(variablesForChildrenNodesV2.containsKey(uuidForExecutionNode)).isTrue();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForServiceEnvByRef_RuntimeInputServiceEnv() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineWithV2ServiceEnv_Runtime.yaml");
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineJson = YamlUtils.injectUuid(pipelineYaml);

    final URL serviceYamlFile = classLoader.getResource("serviceV2.yaml");
    String serviceYaml = Resources.toString(serviceYamlFile, Charsets.UTF_8);
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .name("variableTestSvc")
                                      .identifier("variableTestSvc")
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .description("sample service")
                                      .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                                      .yaml(serviceYaml)
                                      .build();

    final URL envYamlFile = classLoader.getResource("environmentV2.yaml");
    String environmentYaml = Resources.toString(envYamlFile, Charsets.UTF_8);

    Environment environmentEntity = Environment.builder()
                                        .name(ENV_IDENTIFIER)
                                        .yaml(environmentYaml)
                                        .identifier(ENV_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .type(EnvironmentType.Production)
                                        .build();

    final URL infraYamlFile = classLoader.getResource("k8sDirectInfrastructure.yaml");
    String infraYaml = Resources.toString(infraYamlFile, Charsets.UTF_8);

    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .identifier("infra")
                                                    .name("infra")
                                                    .accountId(ACCOUNT_ID)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJ_IDENTIFIER)
                                                    .envIdentifier(ENV_IDENTIFIER)
                                                    .type(InfrastructureType.KUBERNETES_DIRECT)
                                                    .yaml(infraYaml)
                                                    .build();

    // mocks
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(serviceEntity));
    when(environmentService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(environmentEntity));
    when(infrastructureEntityService.get(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(infrastructureEntity));
    when(serviceOverrideService.get(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stageField = fullYamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(0)
                               .getField("stage");

    VariableCreationContext creationContext = VariableCreationContext.builder().currentField(stageField).build();
    creationContext.put(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_ID);
    creationContext.put(NGCommonEntityConstants.ORG_KEY, ORG_IDENTIFIER);
    creationContext.put(NGCommonEntityConstants.PROJECT_KEY, PROJ_IDENTIFIER);

    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodesV2 =
        deploymentStageVariableCreator.createVariablesForChildrenNodesV2(
            creationContext, YamlUtils.read(stageField.getNode().toString(), DeploymentStageNode.class));

    // linked hashmap so ordered entries: env, infra, service expressions
    List<VariableCreationResponse> variableCreationResponseList = new ArrayList<>(variablesForChildrenNodesV2.values());
    List<String> keys = new ArrayList<>(variablesForChildrenNodesV2.keySet());

    // env
    List<YamlProperties> envOutputProperties =
        variableCreationResponseList.get(0).getYamlExtraProperties().get(keys.get(0)).getOutputPropertiesList();
    List<String> envLocalNamePropertiesList =
        envOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());
    assertThat(envLocalNamePropertiesList)
        .containsExactly("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
            "env.variables", "env.envGroupRef", "env.envGroupName");

    List<String> envFQNPropertiesList =
        envOutputProperties.stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(envFQNPropertiesList)
        .containsExactlyInAnyOrder("pipeline.stages.deployStage.spec.env.name",
            "pipeline.stages.deployStage.spec.env.identifier", "pipeline.stages.deployStage.spec.env.description",
            "pipeline.stages.deployStage.spec.env.type", "pipeline.stages.deployStage.spec.env.tags",
            "pipeline.stages.deployStage.spec.env.environmentRef", "pipeline.stages.deployStage.spec.env.variables",
            "pipeline.stages.deployStage.spec.env.envGroupRef", "pipeline.stages.deployStage.spec.env.envGroupName");

    List<YamlProperties> serviceOutputProperties =
        variableCreationResponseList.get(1).getYamlExtraProperties().get(keys.get(1)).getOutputPropertiesList();
    List<String> serviceLocalNamePropertiesList =
        serviceOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());
    assertThat(serviceLocalNamePropertiesList)
        .containsExactly("service.identifier", "service.name", "service.description", "service.type", "service.tags",
            "service.gitOpsEnabled");

    List<String> serviceFQNPropertiesList =
        serviceOutputProperties.stream().map(YamlProperties::getFqn).collect(Collectors.toList());
    assertThat(serviceFQNPropertiesList)
        .containsExactlyInAnyOrder("pipeline.stages.deployStage.spec.service.identifier",
            "pipeline.stages.deployStage.spec.service.name", "pipeline.stages.deployStage.spec.service.description",
            "pipeline.stages.deployStage.spec.service.type", "pipeline.stages.deployStage.spec.service.tags",
            "pipeline.stages.deployStage.spec.service.gitOpsEnabled");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getTestData")
  public void testVariables(TestData data) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(data.getPipelineYamlFile());
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineJson = YamlUtils.injectUuid(pipelineYaml);

    final URL serviceYamlFile = classLoader.getResource(data.getServiceYamlFile());
    String serviceYaml = Resources.toString(serviceYamlFile, Charsets.UTF_8);
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .name("variableTestSvc")
                                      .identifier("variableTestSvc")
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .description("sample service")
                                      .tags(Arrays.asList(NGTag.builder().key("k1").value("v1").build()))
                                      .yaml(serviceYaml)
                                      .build();

    final URL envYamlFile = classLoader.getResource(data.getEnvYamlFile());
    String environmentYaml = Resources.toString(envYamlFile, Charsets.UTF_8);

    Environment environmentEntity = Environment.builder()
                                        .name(ENV_IDENTIFIER)
                                        .yaml(environmentYaml)
                                        .identifier(ENV_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .type(EnvironmentType.Production)
                                        .build();

    final URL infraYamlFile = classLoader.getResource(data.getInfraYamlFile());
    String infraYaml = Resources.toString(infraYamlFile, Charsets.UTF_8);

    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .identifier("infra")
                                                    .name("infra")
                                                    .accountId(ACCOUNT_ID)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJ_IDENTIFIER)
                                                    .envIdentifier(ENV_IDENTIFIER)
                                                    .type(InfrastructureType.KUBERNETES_DIRECT)
                                                    .yaml(infraYaml)
                                                    .build();

    // mocks
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(serviceEntity));
    when(environmentService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(environmentEntity));
    HIterator iteratorMock = Mockito.mock(HIterator.class);
    when(infrastructureEntityService.listIterator(any(), any(), any(), any(), any())).thenReturn(iteratorMock);
    when(iteratorMock.iterator()).thenReturn(List.of(infrastructureEntity).iterator());
    when(infrastructureEntityService.get(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(infrastructureEntity));
    when(serviceOverrideService.get(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(NGServiceOverridesEntity.builder().build()));

    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stageField = fullYamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(0)
                               .getField("stage");

    VariableCreationContext creationContext = VariableCreationContext.builder().currentField(stageField).build();
    creationContext.put(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_ID);
    creationContext.put(NGCommonEntityConstants.ORG_KEY, ORG_IDENTIFIER);
    creationContext.put(NGCommonEntityConstants.PROJECT_KEY, PROJ_IDENTIFIER);

    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodesV2 =
        deploymentStageVariableCreator.createVariablesForChildrenNodesV2(
            creationContext, YamlUtils.read(stageField.getNode().toString(), DeploymentStageNode.class));

    // linked hashmap so ordered entries: env, infra, service expressions
    List<VariableCreationResponse> variableCreationResponseList = new ArrayList<>(variablesForChildrenNodesV2.values());

    List<String> keys = new ArrayList<>(variablesForChildrenNodesV2.keySet());

    assertExpressions(variableCreationResponseList, data.getEnvFqnIndex(), data.getExpectedEnvFqn(), keys);
    assertExpressions(variableCreationResponseList, data.getInfraFqnIndex(), data.getExpectedInfraFqn(), keys);
    assertExpressions(variableCreationResponseList, data.getSvcFqnIndex(), data.getExpectedSvcFqn(), keys);

    assertDependencies(
        variableCreationResponseList, data.getExecutionDependencyIndex(), data.getExecutionDependencyValue());
    assertDependencies(
        variableCreationResponseList, data.getProvisionerDependencyIndex(), data.getProvisionerDependencyValue());
  }

  private void assertDependencies(
      List<VariableCreationResponse> variableCreationResponseList, int index, String expectedValue) {
    if (index > -1) {
      assertThat(
          variableCreationResponseList.get(index).getDependencies().getDependenciesMap().containsValue(expectedValue))
          .isTrue();
    }
  }

  private void assertExpressions(List<VariableCreationResponse> variableCreationResponses, int index,
      List<String> expectedExpressions, List<String> keys) {
    if (index > -1) {
      List<YamlProperties> serviceOutputProperties =
          variableCreationResponses.get(index).getYamlExtraProperties().get(keys.get(index)).getOutputPropertiesList();
      List<String> serviceFqnPropertiesList =
          serviceOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());
      assertThat(serviceFqnPropertiesList).isEqualTo(expectedExpressions);
    }
  }

  private Object[][] getTestData() {
    TestData data1 =
        TestData.builder()
            .pipelineYamlFile("pipelineWithV2ServiceEnv_ServiceInfraRuntime.yaml")
            .serviceYamlFile("serviceV2.yaml")
            .envYamlFile("environmentV2.yaml")
            .infraYamlFile("k8sDirectInfrastructure.yaml")
            .expectedSvcFqn(List.of("service.identifier", "service.name", "service.description", "service.type",
                "service.tags", "service.gitOpsEnabled", "serviceVariables.envVar1", "serviceVariables.svar1"))
            .expectedEnvFqn(
                List.of("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
                    "env.variables.envVar1", "env.variables.svar1", "env.envGroupRef", "env.envGroupName"))
            .provisionerDependencyIndex(-1)
            .expectedInfraFqn(List.of())
            .envFqnIndex(0)
            .svcFqnIndex(1)
            .infraFqnIndex(-1)
            .executionDependencyIndex(2)
            .executionDependencyValue("pipeline/stages/[0]/stage/spec/execution")
            .build();

    TestData data2 =
        TestData.builder()
            .pipelineYamlFile("pipelineWithV2ServiceEnv.yaml")
            .serviceYamlFile("ServiceWithArtifactSources.yaml")
            .envYamlFile("environmentV2.yaml")
            .infraYamlFile("k8sDirectInfrastructure.yaml")
            .expectedSvcFqn(List.of("service.identifier", "service.name", "service.description", "service.type",
                "service.tags", "service.gitOpsEnabled", "artifacts.primary.connectorRef",
                "artifacts.primary.imagePath", "artifacts.primary.tag", "artifacts.primary.tagRegex",
                "artifacts.primary.identifier", "artifacts.primary.type", "artifacts.primary.primaryArtifact",
                "artifacts.primary.image", "artifacts.primary.imagePullSecret", "artifacts.primary.label",
                "artifacts.primary.displayName", "artifacts.primary.digest", "artifacts.primary.metadata",
                "artifacts.primary.dockerConfigJsonSecret", "artifacts.primary.registryHostname",
                "artifacts.primary.region", "artifacts.primary.repositoryName", "artifacts.primary.artifactPath",
                "artifacts.primary.repositoryFormat", "artifacts.primary.artifactDirectory",
                "artifacts.primary.artifactPathFilter", "artifacts.primary.subscription", "artifacts.primary.registry",
                "artifacts.primary.repository", "artifacts.primary.project", "artifacts.primary.package",
                "artifacts.primary.version", "artifacts.primary.versionRegex", "artifacts.primary.repositoryType",
                "serviceVariables.envVar1", "serviceVariables.svar1"))
            .expectedEnvFqn(
                List.of("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
                    "env.variables.envVar1", "env.variables.svar1", "env.envGroupRef", "env.envGroupName"))
            .expectedInfraFqn(List.of("infra.connectorRef", "infra.namespace", "infra.releaseName",
                "infra.infrastructureKey", "infra.connector"))
            .envFqnIndex(0)
            .infraFqnIndex(1)
            .svcFqnIndex(2)
            .provisionerDependencyIndex(-1)
            .executionDependencyIndex(3)
            .executionDependencyValue("pipeline/stages/[0]/stage/spec/execution")
            .build();

    TestData data3 =
        TestData.builder()
            .pipelineYamlFile("pipelineWithV2ServiceEnv.yaml")
            .serviceYamlFile("ServiceWithArtifactSourcesButFixedPrimaryRef.yaml")
            .envYamlFile("environmentV2.yaml")
            .infraYamlFile("k8sDirectInfrastructure.yaml")
            .expectedSvcFqn(
                List.of("service.identifier", "service.name", "service.description", "service.type", "service.tags",
                    "service.gitOpsEnabled", "artifacts.primary.connectorRef", "artifacts.primary.repositoryName",
                    "artifacts.primary.artifactPath", "artifacts.primary.repositoryFormat", "artifacts.primary.tag",
                    "artifacts.primary.tagRegex", "artifacts.primary.identifier", "artifacts.primary.type",
                    "artifacts.primary.primaryArtifact", "artifacts.primary.image", "artifacts.primary.imagePullSecret",
                    "artifacts.primary.dockerConfigJsonSecret", "artifacts.primary.registryHostname",
                    "artifacts.primary.digest", "artifacts.primary.metadata", "artifacts.primary.label",
                    "serviceVariables.envVar1", "serviceVariables.svar1"))
            .expectedEnvFqn(
                List.of("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
                    "env.variables.envVar1", "env.variables.svar1", "env.envGroupRef", "env.envGroupName"))
            .expectedInfraFqn(List.of("infra.connectorRef", "infra.namespace", "infra.releaseName",
                "infra.infrastructureKey", "infra.connector"))
            .provisionerDependencyIndex(-1)
            .envFqnIndex(0)
            .infraFqnIndex(1)
            .svcFqnIndex(2)
            .executionDependencyIndex(3)
            .executionDependencyValue("pipeline/stages/[0]/stage/spec/execution")
            .build();

    TestData data4 =
        TestData.builder()
            .pipelineYamlFile("pipelineWithV2ServiceEnvironments.yaml")
            .serviceYamlFile("serviceV2.yaml")
            .envYamlFile("environmentV2.yaml")
            .infraYamlFile("k8sDirectInfrastructure.yaml")
            .expectedSvcFqn(List.of("service.identifier", "service.name", "service.description", "service.type",
                "service.tags", "service.gitOpsEnabled", "manifests.nginx.identifier", "manifests.nginx.type",
                "manifests.nginx.store.connectorRef", "manifests.nginx.store.gitFetchType",
                "manifests.nginx.store.branch", "manifests.nginx.store.commitId", "manifests.nginx.store.paths",
                "manifests.nginx.store.folderPath", "manifests.nginx.store.repoName",
                "manifests.nginx.skipResourceVersioning", "manifests.nginx.enableDeclarativeRollback",
                "manifests.nginx.valuesPaths", "artifacts.sidecars.gcr_test.connectorRef",
                "artifacts.sidecars.gcr_test.imagePath", "artifacts.sidecars.gcr_test.tag",
                "artifacts.sidecars.gcr_test.tagRegex", "artifacts.sidecars.gcr_test.registryHostname",
                "artifacts.sidecars.gcr_test.identifier", "artifacts.sidecars.gcr_test.type",
                "artifacts.sidecars.gcr_test.primaryArtifact", "artifacts.sidecars.gcr_test.image",
                "artifacts.sidecars.gcr_test.imagePullSecret", "artifacts.sidecars.gcr_test.digest",
                "artifacts.sidecars.gcr_test.metadata", "artifacts.sidecars.gcr_test.label",
                "artifacts.sidecars.gcr_test.dockerConfigJsonSecret", "artifacts.primary.connectorRef",
                "artifacts.primary.imagePath", "artifacts.primary.tag", "artifacts.primary.tagRegex",
                "artifacts.primary.identifier", "artifacts.primary.type", "artifacts.primary.primaryArtifact",
                "artifacts.primary.image", "artifacts.primary.imagePullSecret", "artifacts.primary.label",
                "artifacts.primary.displayName", "artifacts.primary.digest", "artifacts.primary.metadata",
                "artifacts.primary.dockerConfigJsonSecret", "serviceVariables.svar2", "serviceVariables.envVar1",
                "serviceVariables.svar1"))
            .expectedEnvFqn(
                List.of("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
                    "env.variables.envVar1", "env.variables.svar1", "env.envGroupRef", "env.envGroupName"))
            .expectedInfraFqn(List.of("infra.connectorRef", "infra.namespace", "infra.releaseName",
                "infra.infrastructureKey", "infra.connector"))
            .provisionerDependencyIndex(-1)
            .envFqnIndex(0)
            .infraFqnIndex(1)
            .svcFqnIndex(2)
            .executionDependencyIndex(3)
            .executionDependencyValue("pipeline/stages/[0]/stage/spec/execution")
            .build();

    TestData data5 =
        TestData.builder()
            .pipelineYamlFile("pipelineWithV2ServiceEnvInfrastructureDefinitionProvisioner.yaml")
            .serviceYamlFile("serviceV2.yaml")
            .envYamlFile("environmentV2.yaml")
            .infraYamlFile("k8sDirectInfrastructure.yaml")
            .expectedSvcFqn(List.of("service.identifier", "service.name", "service.description", "service.type",
                "service.tags", "service.gitOpsEnabled", "manifests.nginx.identifier", "manifests.nginx.type",
                "manifests.nginx.store.connectorRef", "manifests.nginx.store.gitFetchType",
                "manifests.nginx.store.branch", "manifests.nginx.store.commitId", "manifests.nginx.store.paths",
                "manifests.nginx.store.folderPath", "manifests.nginx.store.repoName",
                "manifests.nginx.skipResourceVersioning", "manifests.nginx.enableDeclarativeRollback",
                "manifests.nginx.valuesPaths", "artifacts.sidecars.gcr_test.connectorRef",
                "artifacts.sidecars.gcr_test.imagePath", "artifacts.sidecars.gcr_test.tag",
                "artifacts.sidecars.gcr_test.tagRegex", "artifacts.sidecars.gcr_test.registryHostname",
                "artifacts.sidecars.gcr_test.identifier", "artifacts.sidecars.gcr_test.type",
                "artifacts.sidecars.gcr_test.primaryArtifact", "artifacts.sidecars.gcr_test.image",
                "artifacts.sidecars.gcr_test.imagePullSecret", "artifacts.sidecars.gcr_test.digest",
                "artifacts.sidecars.gcr_test.metadata", "artifacts.sidecars.gcr_test.label",
                "artifacts.sidecars.gcr_test.dockerConfigJsonSecret", "artifacts.primary.connectorRef",
                "artifacts.primary.imagePath", "artifacts.primary.tag", "artifacts.primary.tagRegex",
                "artifacts.primary.identifier", "artifacts.primary.type", "artifacts.primary.primaryArtifact",
                "artifacts.primary.image", "artifacts.primary.imagePullSecret", "artifacts.primary.label",
                "artifacts.primary.displayName", "artifacts.primary.digest", "artifacts.primary.metadata",
                "artifacts.primary.dockerConfigJsonSecret", "serviceVariables.svar2", "serviceVariables.envVar1",
                "serviceVariables.svar1"))
            .expectedEnvFqn(
                List.of("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
                    "env.variables.envVar1", "env.variables.svar1", "env.envGroupRef", "env.envGroupName"))
            .expectedInfraFqn(List.of("infra.connectorRef", "infra.namespace", "infra.releaseName",
                "infra.infrastructureKey", "infra.connector"))
            .provisionerDependencyIndex(0)
            .envFqnIndex(1)
            .infraFqnIndex(2)
            .svcFqnIndex(3)
            .provisionerDependencyValue("pipeline/stages/[0]/stage/spec/environment/provisioner/steps/[0]/step")
            .executionDependencyIndex(4)
            .executionDependencyValue("pipeline/stages/[0]/stage/spec/execution")
            .build();

    TestData data6 =
        TestData.builder()
            .pipelineYamlFile("pipelineWithServicesEnvironments.yaml")
            .serviceYamlFile("serviceV2.yaml")
            .envYamlFile("environmentV2.yaml")
            .infraYamlFile("k8sDirectInfrastructure.yaml")
            .expectedSvcFqn(List.of("service.identifier", "service.name", "service.description", "service.type",
                "service.tags", "service.gitOpsEnabled", "manifests.nginx.identifier", "manifests.nginx.type",
                "manifests.nginx.store.connectorRef", "manifests.nginx.store.gitFetchType",
                "manifests.nginx.store.branch", "manifests.nginx.store.commitId", "manifests.nginx.store.paths",
                "manifests.nginx.store.folderPath", "manifests.nginx.store.repoName",
                "manifests.nginx.skipResourceVersioning", "manifests.nginx.enableDeclarativeRollback",
                "manifests.nginx.valuesPaths", "artifacts.sidecars.gcr_test.connectorRef",
                "artifacts.sidecars.gcr_test.imagePath", "artifacts.sidecars.gcr_test.tag",
                "artifacts.sidecars.gcr_test.tagRegex", "artifacts.sidecars.gcr_test.registryHostname",
                "artifacts.sidecars.gcr_test.identifier", "artifacts.sidecars.gcr_test.type",
                "artifacts.sidecars.gcr_test.primaryArtifact", "artifacts.sidecars.gcr_test.image",
                "artifacts.sidecars.gcr_test.imagePullSecret", "artifacts.sidecars.gcr_test.digest",
                "artifacts.sidecars.gcr_test.metadata", "artifacts.sidecars.gcr_test.label",
                "artifacts.sidecars.gcr_test.dockerConfigJsonSecret", "artifacts.primary.connectorRef",
                "artifacts.primary.imagePath", "artifacts.primary.tag", "artifacts.primary.tagRegex",
                "artifacts.primary.identifier", "artifacts.primary.type", "artifacts.primary.primaryArtifact",
                "artifacts.primary.image", "artifacts.primary.imagePullSecret", "artifacts.primary.label",
                "artifacts.primary.displayName", "artifacts.primary.digest", "artifacts.primary.metadata",
                "artifacts.primary.dockerConfigJsonSecret", "serviceVariables.svar2", "serviceVariables.envVar1",
                "serviceVariables.svar1"))
            .expectedEnvFqn(
                List.of("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
                    "env.variables.envVar1", "env.variables.svar1", "env.envGroupRef", "env.envGroupName"))
            .expectedInfraFqn(List.of("infra.connectorRef", "infra.namespace", "infra.releaseName",
                "infra.infrastructureKey", "infra.connector"))
            .provisionerDependencyIndex(-1)
            .envFqnIndex(0)
            .infraFqnIndex(1)
            .svcFqnIndex(2)
            .executionDependencyIndex(3)
            .executionDependencyValue("pipeline/stages/[0]/stage/spec/execution")
            .build();
    return new Object[][] {{data1}, {data2}, {data3}, {data4}, {data5}, {data6}};
  }

  @Value
  @Builder
  private static class TestData {
    String pipelineYamlFile;
    String serviceYamlFile;
    String envYamlFile;
    String infraYamlFile;

    int svcFqnIndex;
    int envFqnIndex;
    int infraFqnIndex;
    int executionDependencyIndex;
    int provisionerDependencyIndex;

    List<String> expectedSvcFqn;
    List<String> expectedEnvFqn;
    List<String> expectedInfraFqn;

    String executionDependencyValue;
    String provisionerDependencyValue;
  }
}
