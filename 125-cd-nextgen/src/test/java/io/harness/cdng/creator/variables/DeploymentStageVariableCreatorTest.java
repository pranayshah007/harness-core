/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DeploymentStageVariableCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ServiceEntityService serviceEntityService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @InjectMocks private DeploymentStageVariableCreator deploymentStageVariableCreator;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "default";
  private final String PROJ_IDENTIFIER = "svcenvrefactor";
  private final String SERVICE_IDENTIFIER = "variableTestSvc";
  private final String ENV_IDENTIFIER = "environmentVariableTest";

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
  public void createVariablesForServiceEnvByRef() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineWithV2ServiceEnv.yaml");
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
    List<String> envFqnPropertiesList =
        envOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());

    assertThat(envFqnPropertiesList)
        .containsExactly("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
            "env.variables.envVar1", "env.variables.svar1");

    // infra
    List<YamlProperties> infraOutputProperties =
        variableCreationResponseList.get(1).getYamlExtraProperties().get(keys.get(1)).getOutputPropertiesList();
    List<String> infraFqnPropertiesList =
        infraOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());
    assertThat(infraFqnPropertiesList)
        .containsExactly("infra.connectorRef", "infra.namespace", "infra.releaseName", "infra.infrastructureKey");

    // service
    List<YamlProperties> serviceOutputProperties =
        variableCreationResponseList.get(2).getYamlExtraProperties().get(keys.get(2)).getOutputPropertiesList();
    List<String> serviceFqnPropertiesList =
        serviceOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());

    assertThat(serviceFqnPropertiesList)
        .containsExactly("service.identifier", "service.name", "service.description", "service.type", "service.tags",
            "service.gitOpsEnabled", "manifests.nginx.identifier", "manifests.nginx.type",
            "manifests.nginx.store.connectorRef", "manifests.nginx.store.gitFetchType", "manifests.nginx.store.branch",
            "manifests.nginx.store.commitId", "manifests.nginx.store.paths", "manifests.nginx.store.folderPath",
            "manifests.nginx.store.repoName", "manifests.nginx.skipResourceVersioning", "manifests.nginx.valuesPaths",
            "artifacts.sidecars.gcr_test.connectorRef", "artifacts.sidecars.gcr_test.imagePath",
            "artifacts.sidecars.gcr_test.tag", "artifacts.sidecars.gcr_test.tagRegex",
            "artifacts.sidecars.gcr_test.registryHostname", "artifacts.sidecars.gcr_test.identifier",
            "artifacts.sidecars.gcr_test.type", "artifacts.sidecars.gcr_test.primaryArtifact",
            "artifacts.sidecars.gcr_test.image", "artifacts.sidecars.gcr_test.imagePullSecret",
            "artifacts.primary.connectorRef", "artifacts.primary.imagePath", "artifacts.primary.tag",
            "artifacts.primary.tagRegex", "artifacts.primary.identifier", "artifacts.primary.type",
            "artifacts.primary.primaryArtifact", "artifacts.primary.image", "artifacts.primary.imagePullSecret",
            "serviceVariables.svar2", "serviceVariables.envVar1", "serviceVariables.svar1");
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
    List<String> envFqnPropertiesList =
        envOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());

    assertThat(envFqnPropertiesList)
        .containsExactly("env.name", "env.identifier", "env.description", "env.type", "env.tags", "env.environmentRef",
            "env.variables");

    // no infra vars
    // service
    List<YamlProperties> serviceOutputProperties =
        variableCreationResponseList.get(1).getYamlExtraProperties().get(keys.get(1)).getOutputPropertiesList();
    List<String> serviceFqnPropertiesList =
        serviceOutputProperties.stream().map(YamlProperties::getLocalName).collect(Collectors.toList());

    assertThat(serviceFqnPropertiesList)
        .containsExactly("service.identifier", "service.name", "service.description", "service.type", "service.tags",
            "service.gitOpsEnabled");
  }
}
