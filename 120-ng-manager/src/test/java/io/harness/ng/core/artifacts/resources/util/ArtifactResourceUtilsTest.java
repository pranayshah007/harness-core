/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.NgManagerTestBase;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusConstant;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRequestDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryDockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryImagePathsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.cdng.artifact.resources.custom.CustomResourceService;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceService;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrRequestDTO;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.custom.CustomScriptInfo;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class ArtifactResourceUtilsTest extends NgManagerTestBase {
  @InjectMocks ArtifactResourceUtils artifactResourceUtils;
  @Mock PipelineServiceClient pipelineServiceClient;
  @Mock TemplateResourceClient templateResourceClient;
  @Mock ServiceEntityService serviceEntityService;
  @Mock EnvironmentService environmentService;
  @Mock DockerResourceService dockerResourceService;
  @Mock GARResourceService garResourceService;
  @Mock GcrResourceService gcrResourceService;
  @Mock EcrResourceService ecrResourceService;
  @Mock AcrResourceService acrResourceService;
  @Mock AzureResourceService azureResourceService;
  @Mock NexusResourceService nexusResourceService;
  @Mock ArtifactoryResourceService artifactoryResourceService;
  @Mock AccessControlClient accessControlClient;
  @Mock CustomResourceService customResourceService;
  @Mock CDYamlExpressionEvaluator cdYamlExpressionEvaluator;
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "image_expression_test";
  private static final String SERVICE_REF = "serviceRef";
  private static final String FQN = "fqn";
  private static final String REPO_NAME = "repoName";
  private static final String VERSION = "version";
  private static final String VERSION_REGEX = "versionRegex";
  private static final String REGION = "region";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String IMAGE = "image";
  private static final String HOST = "host";
  private static final String PKG = "pkg";
  private static final String SUBSCRIPTION = "subscription";
  private static final String REGISTRY = "registry";
  private static final String PORT = "port";
  private static final String PROJECT = "project";
  private static final String URL = "url";
  private static final String DOCKER = "docker";
  private static final String IMAGE_2 = "image2";
  private static final String HOST_2 = "host2";
  private static final String TAG_2 = "tag2";
  private static final String TAG_REGEX_2 = "tagRegex2";
  private static final String CONNECTOR_REF_2 = "connectorRef2";
  private static final String REGION_2 = "region2";
  private static final String PORT_2 = "port2";
  private static final String URL_2 = "url2";
  private static final String PKG_2 = "pkg2";
  private static final String PROJECT_2 = "project2";
  private static final String REPO_NAME_2 = "repoName2";
  private static final String REGISTRY_2 = "registry2";
  private static final String SUBSCRIPTION_2 = "subscription2";
  private static final String pipelineYamlWithoutTemplates = "pipeline:\n"
      + "    name: image expression test\n"
      + "    identifier: image_expression_test\n"
      + "    projectIdentifier: projectId\n"
      + "    orgIdentifier: orgId\n"
      + "    storeType: \"\"\n"
      + "    tags: {}\n"
      + "    stages:\n"
      + "        - stage:\n"
      + "              name: test\n"
      + "              identifier: test\n"
      + "              description: \"\"\n"
      + "              type: Deployment\n"
      + "              spec:\n"
      + "                  serviceConfig:\n"
      + "                      serviceRef: svc1\n"
      + "                      serviceDefinition:\n"
      + "                          spec:\n"
      + "                              variables: []\n"
      + "                              artifacts:\n"
      + "                                  primary:\n"
      + "                                      spec:\n"
      + "                                          connectorRef: docker_test\n"
      + "                                          imagePath: <+pipeline.variables.image_path>\n"
      + "                                          tag: <+input>\n"
      + "                                      type: DockerRegistry\n"
      + "                                  sidecars:\n"
      + "                                      - sidecar:\n"
      + "                                            spec:\n"
      + "                                                connectorRef: Docker_Connector\n"
      + "                                                imagePath: <+service.name>\n"
      + "                                                tag: <+input>\n"
      + "                                            identifier: sidecar_id\n"
      + "                                            type: DockerRegistry\n"
      + "                          type: Kubernetes\n"
      + "                  infrastructure:\n"
      + "                      environmentRef: env1\n"
      + "                      infrastructureDefinition:\n"
      + "                          type: KubernetesDirect\n"
      + "                          spec:\n"
      + "                              connectorRef: cdcd\n"
      + "                              namespace: deafult\n"
      + "                              releaseName: release-<+INFRA_KEY>\n"
      + "                      allowSimultaneousDeployments: false\n"
      + "                  execution:\n"
      + "                      steps:\n"
      + "                          - step:\n"
      + "                                name: Rollout Deployment\n"
      + "                                identifier: rolloutDeployment\n"
      + "                                type: K8sRollingDeploy\n"
      + "                                timeout: 10m\n"
      + "                                spec:\n"
      + "                                    skipDryRun: false\n"
      + "                      rollbackSteps:\n"
      + "                          - step:\n"
      + "                                name: Rollback Rollout Deployment\n"
      + "                                identifier: rollbackRolloutDeployment\n"
      + "                                type: K8sRollingRollback\n"
      + "                                timeout: 10m\n"
      + "                                spec: {}\n"
      + "              tags: {}\n"
      + "              failureStrategies:\n"
      + "                  - onFailure:\n"
      + "                        errors:\n"
      + "                            - AllErrors\n"
      + "                        action:\n"
      + "                            type: StageRollback\n"
      + "    variables:\n"
      + "        - name: image_path\n"
      + "          type: String\n"
      + "          value: library/nginx\n";

  private static final String pipelineYamlWithTemplate = "pipeline:\n"
      + "    name: image expression test\n"
      + "    identifier: image_expression_test\n"
      + "    projectIdentifier: inderproj\n"
      + "    orgIdentifier: Archit\n"
      + "    storeType: \"\"\n"
      + "    tags: {}\n"
      + "    stages:\n"
      + "        - stage:\n"
      + "              name: test\n"
      + "              identifier: test\n"
      + "              template:\n"
      + "                  templateRef: image_expression_test_template\n"
      + "                  versionLabel: v1\n"
      + "                  templateInputs:\n"
      + "                      type: Deployment\n"
      + "                      spec:\n"
      + "                          serviceConfig:\n"
      + "                              serviceDefinition:\n"
      + "                                  type: Kubernetes\n"
      + "                                  spec:\n"
      + "                                      artifacts:\n"
      + "                                          primary:\n"
      + "                                              type: DockerRegistry\n"
      + "                                              spec:\n"
      + "                                                  tag: <+input>\n"
      + "    variables:\n"
      + "        - name: image_path\n"
      + "          type: String\n"
      + "          value: <+input>\n";

  private static final GitEntityFindInfoDTO GIT_ENTITY_FIND_INFO_DTO = GitEntityFindInfoDTO.builder().build();
  private static final DockerRequestDTO DOCKER_REQUEST_DTO = DockerRequestDTO.builder()
                                                                 .tag(VERSION)
                                                                 .tagRegex(VERSION_REGEX)
                                                                 .runtimeInputYaml(pipelineYamlWithoutTemplates)
                                                                 .build();
  private static final GcrRequestDTO GCR_REQUEST_DTO_2 = GcrRequestDTO.builder()
                                                             .tag(VERSION)
                                                             .registryHostname(HOST)
                                                             .tagRegex(VERSION_REGEX)
                                                             .runtimeInputYaml(pipelineYamlWithoutTemplates)
                                                             .build();
  private static final EcrRequestDTO ECR_REQUEST_DTO = EcrRequestDTO.builder()
                                                           .tag(VERSION)
                                                           .tagRegex(VERSION_REGEX)
                                                           .region(REGION)
                                                           .runtimeInputYaml(pipelineYamlWithoutTemplates)
                                                           .build();
  private static final AcrRequestDTO ACR_REQUEST_DTO = AcrRequestDTO.builder()
                                                           .tag(VERSION)
                                                           .tagRegex(VERSION_REGEX)
                                                           .runtimeInputYaml(pipelineYamlWithoutTemplates)
                                                           .build();
  private static final NexusRequestDTO NEXUS_REQUEST_DTO = NexusRequestDTO.builder()
                                                               .tag(VERSION)
                                                               .tagRegex(VERSION_REGEX)
                                                               .runtimeInputYaml(pipelineYamlWithoutTemplates)
                                                               .build();
  private static final ArtifactoryRequestDTO ARTIFACTORY_REQUEST_DTO =
      ArtifactoryRequestDTO.builder()
          .tag(VERSION)
          .tagRegex(VERSION_REGEX)
          .runtimeInputYaml(pipelineYamlWithoutTemplates)
          .build();
  private static final GarRequestDTO GAR_REQUEST_DTO_2 = GarRequestDTO.builder()
                                                             .version(VERSION)
                                                             .versionRegex(VERSION_REGEX)
                                                             .runtimeInputYaml(pipelineYamlWithoutTemplates)
                                                             .build();

  private static final DockerBuildDetailsDTO DOCKER_BUILD_DETAILS_DTO = DockerBuildDetailsDTO.builder().build();
  private static final GcrBuildDetailsDTO GCR_BUILD_DETAILS_DTO = GcrBuildDetailsDTO.builder().build();
  private static final EcrBuildDetailsDTO ECR_BUILD_DETAILS_DTO = EcrBuildDetailsDTO.builder().build();
  private static final AcrBuildDetailsDTO ACR_BUILD_DETAILS_DTO = AcrBuildDetailsDTO.builder().build();
  private static final AcrResponseDTO ACR_RESPONSE_DTO = AcrResponseDTO.builder().build();
  private static final AcrRepositoriesDTO ACR_REPOSITORIES_DTO = AcrRepositoriesDTO.builder().build();
  private static final AcrRegistriesDTO ACR_REGISTRIES_DTO = AcrRegistriesDTO.builder().build();
  private static final AzureSubscriptionsDTO AZURE_SUBSCRIPTIONS_DTO = AzureSubscriptionsDTO.builder().build();
  private static final NexusBuildDetailsDTO NEXUS_BUILD_DETAILS_DTO = NexusBuildDetailsDTO.builder().build();
  private static final ArtifactoryDockerBuildDetailsDTO ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO =
      ArtifactoryDockerBuildDetailsDTO.builder().build();
  private static final IdentifierRef IDENTIFIER_REF =
      IdentifierRefHelper.getIdentifierRef(CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID);

  private static final GARBuildDetailsDTO GAR_BUILD_DETAILS_DTO = GARBuildDetailsDTO.builder().build();

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetResolvedPathWithImagePathAsExpressionAndNoTemplates() throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                                                 .isErrorResponse(false)
                                                                 .completePipelineYaml(pipelineYamlWithoutTemplates)
                                                                 .build())));
    String imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+pipeline.variables.image_path>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("library/nginx");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetResolvedPathWithImagePathWhenPipelineUnderConstruction() {
    assertThatThrownBy(
        ()
            -> artifactResourceUtils.getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID,
                "-1", "", "<+pipeline.variables.image_path>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Couldn't resolve artifact image path expression <+pipeline.variables.image_path>, as pipeline has not been saved yet.");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveParameterFieldValuesNoTemplates() throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                                                 .isErrorResponse(false)
                                                                 .completePipelineYaml(pipelineYamlWithoutTemplates)
                                                                 .build())));
    ParameterField<String> paramWithExpression = new ParameterField<>();
    paramWithExpression.updateWithExpression("<+pipeline.variables.image_path>");
    ParameterField<String> paramWithoutExpression = new ParameterField<>();
    paramWithoutExpression.updateWithValue("value");
    List<ParameterField<String>> paramFields = Arrays.asList(paramWithExpression, paramWithoutExpression);
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", paramFields,
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramWithExpression.getValue()).isEqualTo("library/nginx");
    assertThat(paramWithoutExpression.getValue()).isEqualTo("value");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testResolveParameterFieldValuesWithInvalidPipelineIdentifier() throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenThrow(new InvalidRequestException(
            "Pipeline [-1] under Project[s], Organization [default] doesn't exist or has been deleted"));
    ParameterField<String> paramWithExpression = new ParameterField<>();
    paramWithExpression.updateWithExpression("<+pipeline.variables.image_path>");
    ParameterField<String> paramWithoutExpression = new ParameterField<>();
    paramWithoutExpression.updateWithValue("value");
    List<ParameterField<String>> paramFields = Arrays.asList(paramWithExpression, paramWithoutExpression);
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", paramFields,
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramWithExpression.getExpressionValue()).isEqualTo("<+pipeline.variables.image_path>");
    assertThat(paramWithoutExpression.getValue()).isEqualTo("value");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetResolvedPathWithImagePathAsExpressionFromTemplate() throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                                                 .isErrorResponse(false)
                                                                 .completePipelineYaml(pipelineYamlWithTemplate)
                                                                 .build())));

    Call<ResponseDTO<TemplateMergeResponseDTO>> mergeTemplateToYamlCall = mock(Call.class);
    when(
        templateResourceClient.applyTemplatesOnGivenYaml(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeTemplateToYamlCall);
    when(mergeTemplateToYamlCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateMergeResponseDTO.builder().mergedPipelineYaml(pipelineYamlWithoutTemplates).build())));

    String imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+pipeline.variables.image_path>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("library/nginx");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(templateResourceClient)
        .applyTemplatesOnGivenYaml(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveParameterFieldValuesFromTemplate() throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                                                 .isErrorResponse(false)
                                                                 .completePipelineYaml(pipelineYamlWithTemplate)
                                                                 .build())));

    Call<ResponseDTO<TemplateMergeResponseDTO>> mergeTemplateToYamlCall = mock(Call.class);
    when(
        templateResourceClient.applyTemplatesOnGivenYaml(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeTemplateToYamlCall);
    when(mergeTemplateToYamlCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateMergeResponseDTO.builder().mergedPipelineYaml(pipelineYamlWithoutTemplates).build())));

    ParameterField<String> paramWithExpression = new ParameterField<>();
    paramWithExpression.updateWithExpression("<+pipeline.variables.image_path>");
    ParameterField<String> paramWithoutExpression = new ParameterField<>();
    paramWithoutExpression.updateWithValue("value");
    List<ParameterField<String>> paramFields = Arrays.asList(paramWithExpression, paramWithoutExpression);
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", paramFields,
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramWithExpression.getValue()).isEqualTo("library/nginx");
    assertThat(paramWithoutExpression.getValue()).isEqualTo("value");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(templateResourceClient)
        .applyTemplatesOnGivenYaml(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetResolvedPathWithImagePathAsServiceAndEnvExpression() throws IOException {
    String yaml = readFile("artifacts/pipeline-without-ser-env-refactoring.yaml");
    mockMergeInputSetCall(yaml);
    mockServiceGetCall("svc1");
    mockEnvironmentGetCall();

    // resolve expressions like <+service.name> in normal stage
    String imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // resolve expressions like <+service.name> in parallel stage
    imagePath = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                        "<+service.name>",
                        "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                        GitEntityFindInfoDTO.builder().build(), "", null)
                    .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in normal stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in parallel stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // resolve env expressions in normal stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+env.name>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("env1");

    // resolve env expressions in parallel stage
    imagePath = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                        "<+env.name>",
                        "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                        GitEntityFindInfoDTO.builder().build(), "", null)
                    .getValue();
    assertThat(imagePath).isEqualTo("env1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveParameterFieldValuesWithImagePathAsServiceAndEnvExpression() throws IOException {
    String yaml = readFile("artifacts/pipeline-without-ser-env-refactoring.yaml");
    mockMergeInputSetCall(yaml);
    mockServiceGetCall("svc1");
    mockEnvironmentGetCall();

    ParameterField<String> paramServiceNormal = new ParameterField<>();
    paramServiceNormal.updateWithExpression("<+service.name>");
    ParameterField<String> paramEnvNormal = new ParameterField<>();
    paramEnvNormal.updateWithExpression("<+env.name>");
    List<ParameterField<String>> paramFieldsNormal = Arrays.asList(paramServiceNormal, paramEnvNormal);

    ParameterField<String> paramServiceParallel = new ParameterField<>();
    paramServiceParallel.updateWithExpression("<+service.name>");
    ParameterField<String> paramEnvParallel = new ParameterField<>();
    paramEnvParallel.updateWithExpression("<+env.name>");
    List<ParameterField<String>> paramFieldsParallel = Arrays.asList(paramServiceParallel, paramEnvParallel);

    // resolve expressions like <+service.name> and <+env.name> in normal stage
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        paramFieldsNormal, "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramServiceNormal.getValue()).isEqualTo("svc1");
    assertThat(paramEnvNormal.getValue()).isEqualTo("env1");

    // resolve expressions like <+service.name> and <+env.name> in parallel stage
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        paramFieldsParallel,
        "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramServiceParallel.getValue()).isEqualTo("svc1");
    assertThat(paramEnvParallel.getValue()).isEqualTo("env1");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetResolvedPathWithImagePathAsServiceAndEnvExpressionAfterSerEnvRefactoring() throws IOException {
    String yaml = readFile("artifacts/pipeline-with-service-env-ref.yaml");
    mockMergeInputSetCall(yaml);
    mockServiceV2GetCall("variableTestSvc");
    mockEnvironmentV2GetCall();

    // resolve expressions like <+service.name> in normal stage
    String imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // resolve expressions like <+service.name> in parallel stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in normal stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in parallel stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    // resolve env expressions in normal stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+env.name>",
                "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("env1");

    // resolve env expressions in parallel stage
    imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+env.name>",
                "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("env1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveParameterFieldValuesWithImagePathAsServiceAndEnvExpressionAfterSerEnvRefactoring()
      throws IOException {
    String yaml = readFile("artifacts/pipeline-with-service-env-ref.yaml");
    mockMergeInputSetCall(yaml);
    mockServiceV2GetCall("variableTestSvc");
    mockEnvironmentV2GetCall();

    ParameterField<String> paramServiceNormal = new ParameterField<>();
    paramServiceNormal.updateWithExpression("<+service.name>");
    ParameterField<String> paramEnvNormal = new ParameterField<>();
    paramEnvNormal.updateWithExpression("<+env.name>");
    List<ParameterField<String>> paramFieldsNormal = Arrays.asList(paramServiceNormal, paramEnvNormal);

    ParameterField<String> paramServiceParallel = new ParameterField<>();
    paramServiceParallel.updateWithExpression("<+service.name>");
    ParameterField<String> paramEnvParallel = new ParameterField<>();
    paramEnvParallel.updateWithExpression("<+env.name>");
    List<ParameterField<String>> paramFieldsParallel = Arrays.asList(paramServiceParallel, paramEnvParallel);

    // resolve expressions like <+service.name> and <+env.name> in normal stage
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        paramFieldsNormal,
        "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramServiceNormal.getValue()).isEqualTo("svc1");
    assertThat(paramEnvNormal.getValue()).isEqualTo("env1");

    // resolve expressions like <+service.name> and <+env.name> in parallel stage
    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        paramFieldsParallel,
        "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramServiceParallel.getValue()).isEqualTo("svc1");
    assertThat(paramEnvParallel.getValue()).isEqualTo("env1");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetResolvedPathWhenServiceAndEnvironmentDoesNotHaveYaml() throws IOException {
    String yaml = readFile("artifacts/pipeline-without-ser-env-refactoring.yaml");
    mockMergeInputSetCall(yaml);
    when(serviceEntityService.get(anyString(), anyString(), anyString(), eq("svc1"), anyBoolean()))
        .thenReturn(Optional.of(ServiceEntity.builder().name("svc1").identifier("svc1").build()));

    when(environmentService.get(anyString(), anyString(), anyString(), eq("env1"), anyBoolean()))
        .thenReturn(Optional.of(Environment.builder().name("env1").identifier("env1").build()));

    String imagePath =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                "<+service.name>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), "", null)
            .getValue();
    assertThat(imagePath).isEqualTo("svc1");

    imagePath = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
                        "<+env.name>",
                        "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                        GitEntityFindInfoDTO.builder().build(), "", null)
                    .getValue();
    assertThat(imagePath).isEqualTo("env1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testResolveParameterFieldValuesWhenServiceAndEnvironmentDoesNotHaveYaml() throws IOException {
    String yaml = readFile("artifacts/pipeline-without-ser-env-refactoring.yaml");
    mockMergeInputSetCall(yaml);
    when(serviceEntityService.get(anyString(), anyString(), anyString(), eq("svc1"), anyBoolean()))
        .thenReturn(Optional.of(ServiceEntity.builder().name("svc1").identifier("svc1").build()));

    when(environmentService.get(anyString(), anyString(), anyString(), eq("env1"), anyBoolean()))
        .thenReturn(Optional.of(Environment.builder().name("env1").identifier("env1").build()));

    ParameterField<String> paramService = new ParameterField<>();
    paramService.updateWithExpression("<+service.name>");
    ParameterField<String> paramEnv = new ParameterField<>();
    paramEnv.updateWithExpression("<+env.name>");
    List<ParameterField<String>> paramFields = Arrays.asList(paramService, paramEnv);

    artifactResourceUtils.resolveParameterFieldValues(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", paramFields,
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(paramService.getValue()).isEqualTo("svc1");
    assertThat(paramEnv.getValue()).isEqualTo("env1");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetBuildDetailsV2GAR() {
    // spy for ArtifactResourceUtils
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    // Creating GoogleArtifactRegistryConfig for mock
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("connectorref").build())
            .project(ParameterField.<String>builder().value("project").build())
            .pkg(ParameterField.<String>builder().value("pkg").build())
            .repositoryName(ParameterField.<String>builder().value("reponame").build())
            .region(ParameterField.<String>builder().value("region").build())
            .version(ParameterField.<String>builder().value("version").build())
            .build();

    // Creating GARResponseDTO for mock
    GARResponseDTO buildDetails = GARResponseDTO.builder().build();

    // Creating IdentifierRef for mock
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef("connectorref", "accountId", "orgId", "projectId");

    doReturn(googleArtifactRegistryConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any());

    doReturn(buildDetails)
        .when(garResourceService)
        .getBuildDetails(
            identifierRef, "region", "reponame", "project", "pkg", "version", "versionRegex", "orgId", "projectId");

    assertThat(spyartifactResourceUtils.getBuildDetailsV2GAR(null, null, null, null, null, "accountId", "orgId",
                   "projectId", "pipeId", "version", "versionRegex", "", "", "serviceref", null))
        .isEqualTo(buildDetails);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsV2Custom() {
    // spy for ArtifactResourceUtils
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    List<TaskSelectorYaml> delegateSelectorsValue = new ArrayList<>();
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("abc");
    delegateSelectorsValue.add(taskSelectorYaml);

    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("build-x"))
            .delegateSelectors(ParameterField.<List<TaskSelectorYaml>>builder().value(delegateSelectorsValue).build())
            .build();

    doReturn(customArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any());
    doReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().withArtifactPath("Test").build()))
        .when(customResourceService)
        .getBuilds("test", "version", "path", null, "accountId", "orgId", "projectId", 1234, null);
    List<BuildDetails> buildDetails = spyartifactResourceUtils.getCustomGetBuildDetails("path", "version",
        CustomScriptInfo.builder().build(), "test", "accountId", "orgId", "projectId", null, null, null);
    assertThat(buildDetails).isNotNull();
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetBuildDetailsV2CustomWithInputAsExpression() {
    // spy for ArtifactResourceUtils
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    List<TaskSelectorYaml> delegateSelectorsValue = new ArrayList<>();
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("abc");
    delegateSelectorsValue.add(taskSelectorYaml);

    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("build-x"))
            .delegateSelectors(ParameterField.<List<TaskSelectorYaml>>builder().value(delegateSelectorsValue).build())
            .build();

    List<NGVariable> inputs = new ArrayList<>();
    inputs.add(StringNGVariable.builder()
                   .name("var1")
                   .value(ParameterField.<String>builder()
                              .expressionValue("<+pipeline.variables.filename>")
                              .expression(true)
                              .value(null)
                              .build())
                   .build());
    inputs.add(StringNGVariable.builder()
                   .name("var2")
                   .value(ParameterField.<String>builder().value("test").expression(false).build())
                   .build());
    inputs.add(StringNGVariable.builder()
                   .name("var3")
                   .value(ParameterField.<String>builder()
                              .expressionValue("<+pipeline.variables.path>")
                              .expression(true)
                              .value(null)
                              .build())
                   .build());
    Map<String, String> inputVariables = new HashMap<>();
    inputVariables.put("var1", "<+pipeline.variables.filename>");
    inputVariables.put("var2", "test");
    inputVariables.put("var3", "<+pipeline.variables.path>");
    Map<String, String> resolvedInputVariables = new HashMap<>();
    resolvedInputVariables.put("var1", "json-payload");
    resolvedInputVariables.put("var2", "test");
    resolvedInputVariables.put("var3", "/Users/Desktop/json-payload");
    doReturn(cdYamlExpressionEvaluator)
        .when(spyartifactResourceUtils)
        .getYamlExpressionEvaluator(
            eq("accountId"), eq("orgId"), eq("projectId"), eq(null), eq(null), eq(null), eq(null), eq("test"));
    when(cdYamlExpressionEvaluator.renderExpression(
             "<+pipeline.variables.filename>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .thenReturn("json-payload");
    when(cdYamlExpressionEvaluator.renderExpression(
             "<+pipeline.variables.path>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .thenReturn("/Users/Desktop/json-payload");
    doReturn(customArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any());
    doReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().withArtifactPath("Test").build()))
        .when(customResourceService)
        .getBuilds(eq("cat $var1 > $HARNESS_ARTIFACT_RESULT_PATH"), eq("version"), eq("path"),
            eq(resolvedInputVariables), eq("accountId"), eq("orgId"), eq("projectId"), anyInt(), eq(null));
    List<BuildDetails> buildDetails = spyartifactResourceUtils.getCustomGetBuildDetails("path", "version",
        CustomScriptInfo.builder().script("cat $var1 > $HARNESS_ARTIFACT_RESULT_PATH").inputs(inputs).build(), "test",
        "accountId", "orgId", "projectId", null, null, null);
    assertThat(buildDetails.size()).isEqualTo(1);
    verify(spyartifactResourceUtils, times(1))
        .getYamlExpressionEvaluator(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testArtifactoryImagePaths() {
    // spy for ArtifactResourceUtils
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    // Creating ArtifactoryRegistryArtifactConfig for mock

    ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("connectorref").build())
            .repository(ParameterField.<String>builder().value("repository").build())
            .build();

    // Creating IdentifierRef for mock
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef("connectorref", "accountId", "orgId", "projectId");

    ArtifactoryImagePathsDTO result = ArtifactoryImagePathsDTO.builder().build();

    doReturn(artifactoryRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(any(), any(), any(), any(), any());

    doReturn(result)
        .when(artifactoryResourceService)
        .getImagePaths("", identifierRef, "orgId", "projectId", "repository");

    assertThat(spyartifactResourceUtils.getArtifactoryImagePath("", "connectorref", "accountId", "orgId", "projectId",
                   "repository", "fqnPath", "runtimeInputYaml", "pipelineIdentifier", "serviceRef", null))
        .isEqualTo(result);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testLocateArtifactForSourcesFromTemplate() throws IOException {
    String serviceYaml = readFile("artifacts/service-with-primary-artifact-source-templates.yaml");
    YamlNode serviceNode = YamlNode.fromYamlPath(serviceYaml, "service");

    String imageTagFqnWithinService =
        "serviceDefinition.spec.artifacts.primary.sources.ft1.template.templateInputs.spec.tag";

    YamlNode artifactSpecNode = YamlNodeUtils.goToPathUsingFqn(serviceNode, imageTagFqnWithinService);
    when(serviceEntityService.getYamlNodeForFqn(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(artifactSpecNode);

    Call<ResponseDTO<TemplateResponseDTO>> callRequest = mock(Call.class);

    String artifactSourceTemplate = readFile("artifacts/artifact-source-template-1.yaml");

    doReturn(callRequest).when(templateResourceClient).get(any(), any(), any(), any(), any(), anyBoolean());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                        .templateEntityType(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)
                                        .yaml(artifactSourceTemplate)
                                        .build())));

    ArtifactConfig artifactConfig =
        artifactResourceUtils.locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc1", imageTagFqnWithinService);

    assertThat(artifactConfig).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getConnectorRef().getValue())
        .isEqualTo("account.harnessImage");
    assertThat(((DockerHubArtifactConfig) artifactConfig).getImagePath().getExpressionValue())
        .isEqualTo("library/<+service.name>");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testLocateArtifactForNonTemplateSources() throws IOException {
    String serviceYaml = readFile("artifacts/service-with-primary-artifact-source-templates.yaml");
    YamlNode serviceNode = YamlNode.fromYamlPath(serviceYaml, "service");

    // non template source
    String imageTagFqnWithinService = "serviceDefinition.spec.artifacts.primary.sources.nontemp.spec.tag";

    YamlNode artifactSpecNode = YamlNodeUtils.goToPathUsingFqn(serviceNode, imageTagFqnWithinService);
    when(serviceEntityService.getYamlNodeForFqn(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(artifactSpecNode);

    ArtifactConfig artifactConfig =
        artifactResourceUtils.locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc1", imageTagFqnWithinService);

    assertThat(artifactConfig).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getConnectorRef()).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getImagePath()).isNotNull();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testLocateArtifactForSourcesFromAccountLevelTemplateStableVersion() throws IOException {
    String serviceYaml = readFile("artifacts/service-with-primary-artifact-source-templates.yaml");
    YamlNode serviceNode = YamlNode.fromYamlPath(serviceYaml, "service");

    String imageTagFqnWithinService =
        "serviceDefinition.spec.artifacts.primary.sources.ft2.template.templateInputs.spec.tag";

    YamlNode artifactSpecNode = YamlNodeUtils.goToPathUsingFqn(serviceNode, imageTagFqnWithinService);
    when(serviceEntityService.getYamlNodeForFqn(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(artifactSpecNode);

    Call<ResponseDTO<TemplateResponseDTO>> callRequest = mock(Call.class);

    String artifactSourceTemplate = readFile("artifacts/artifact-source-template-1.yaml");

    // template service called with null params when account level template is used as stable version
    doReturn(callRequest).when(templateResourceClient).get(any(), any(), eq(null), eq(null), eq(null), anyBoolean());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                        .templateEntityType(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)
                                        .yaml(artifactSourceTemplate)
                                        .build())));

    ArtifactConfig artifactConfig =
        artifactResourceUtils.locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc1", imageTagFqnWithinService);

    assertThat(artifactConfig).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getConnectorRef()).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getImagePath()).isNotNull();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testLocateArtifactForSourcesFromTemplateWithTemplateInputs() throws IOException {
    String serviceYaml = readFile("artifacts/service-with-primary-artifact-source-templates.yaml");
    YamlNode serviceNode = YamlNode.fromYamlPath(serviceYaml, "service");

    String imageTagFqnWithinService =
        "serviceDefinition.spec.artifacts.primary.sources.withInputs1.template.templateInputs.spec.tag";

    YamlNode artifactSpecNode = YamlNodeUtils.goToPathUsingFqn(serviceNode, imageTagFqnWithinService);
    when(serviceEntityService.getYamlNodeForFqn(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(artifactSpecNode);

    Call<ResponseDTO<TemplateResponseDTO>> callRequest = mock(Call.class);

    String artifactSourceTemplate = readFile("artifacts/artifact-source-template-2.yaml");

    doReturn(callRequest).when(templateResourceClient).get(any(), any(), any(), any(), any(), anyBoolean());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                        .templateEntityType(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)
                                        .yaml(artifactSourceTemplate)
                                        .build())));

    ArtifactConfig artifactConfig =
        artifactResourceUtils.locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc1", imageTagFqnWithinService);
    // final artifact config created by merging template inputs with template
    assertThat(artifactConfig).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getConnectorRef().getValue())
        .isEqualTo("account.harnessImage");
    assertThat(((DockerHubArtifactConfig) artifactConfig).getImagePath().getValue()).isEqualTo("library/nginx");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testLocateArtifactForSourceWithAllTemplateInputs() throws IOException {
    String serviceYaml = readFile("artifacts/service-with-primary-artifact-source-templates.yaml");
    YamlNode serviceNode = YamlNode.fromYamlPath(serviceYaml, "service");

    String imageTagFqnWithinService =
        "serviceDefinition.spec.artifacts.primary.sources.withAllInputs.template.templateInputs.spec.tag";

    YamlNode artifactSpecNode = YamlNodeUtils.goToPathUsingFqn(serviceNode, imageTagFqnWithinService);
    when(serviceEntityService.getYamlNodeForFqn(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(artifactSpecNode);

    Call<ResponseDTO<TemplateResponseDTO>> callRequest = mock(Call.class);

    String artifactSourceTemplate = readFile("artifacts/artifact-source-template-all-inputs.yaml");

    doReturn(callRequest).when(templateResourceClient).get(any(), any(), any(), any(), any(), anyBoolean());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateResponseDTO.builder()
                                        .templateEntityType(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE)
                                        .yaml(artifactSourceTemplate)
                                        .build())));

    ArtifactConfig artifactConfig =
        artifactResourceUtils.locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, "svc1", imageTagFqnWithinService);

    // final artifact config will have Inputs but the API call will already contain concrete imagePath, connectorRef
    // so it not be overriden
    assertThat(artifactConfig).isNotNull();
    assertThat(((DockerHubArtifactConfig) artifactConfig).getConnectorRef().getExpressionValue()).isEqualTo("<+input>");
    assertThat(((DockerHubArtifactConfig) artifactConfig).getImagePath().getExpressionValue()).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters({"library/nginx.allowedValues(library/nginx), library/nginx",
      "library/http.regex(library.*), library/http", "http, http"})
  public void
  testGetResolvedImagePathWithFixed(String imagePathInput, String expectedImagePath) {
    String resolvedImagePath = artifactResourceUtils
                                   .getResolvedFieldValueWithYamlExpressionEvaluator(
                                       "a", "o", "p", "p", "", imagePathInput, "fqn", null, null, null)
                                   .getValue();
    assertThat(resolvedImagePath).isEqualTo(expectedImagePath);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testFetLastSuccessfulBuildV2GAR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .project(ParameterField.<String>builder().value(PROJECT).build())
            .pkg(ParameterField.<String>builder().value(PKG).build())
            .repositoryName(ParameterField.<String>builder().value(REPO_NAME).build())
            .region(ParameterField.<String>builder().value(REGION).build())
            .version(ParameterField.<String>builder().value(VERSION).build())
            .versionRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .build();

    doReturn(googleArtifactRegistryConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(GAR_BUILD_DETAILS_DTO)
        .when(garResourceService)
        .getLastSuccessfulBuild(eq(IDENTIFIER_REF), eq(REGION), eq(REPO_NAME), eq(PROJECT), eq(PKG),
            eq(GAR_REQUEST_DTO_2), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(
        spyartifactResourceUtils.getLastSuccessfulBuildV2GAR(null, null, null, null, null, ACCOUNT_ID, ORG_ID,
            PROJECT_ID, PIPELINE_ID, GarRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build(),
            FQN, SERVICE_REF, GIT_ENTITY_FIND_INFO_DTO))
        .isSameAs(GAR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PKG, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PROJECT, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testFetLastSuccessfulBuildV2GAR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder().build();

    doReturn(googleArtifactRegistryConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(GAR_BUILD_DETAILS_DTO)
        .when(garResourceService)
        .getLastSuccessfulBuild(eq(IDENTIFIER_REF), eq(REGION), eq(REPO_NAME), eq(PROJECT), eq(PKG),
            eq(GAR_REQUEST_DTO_2), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(
        spyartifactResourceUtils.getLastSuccessfulBuildV2GAR(CONNECTOR_REF, REGION, REPO_NAME, PROJECT, PKG, ACCOUNT_ID,
            ORG_ID, PROJECT_ID, PIPELINE_ID, GAR_REQUEST_DTO_2, FQN, SERVICE_REF, GIT_ENTITY_FIND_INFO_DTO))
        .isSameAs(GAR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PKG, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PROJECT, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testFetLastSuccessfulBuildV2GAR_ValuesFromResolvedExpression() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder()
            .version(ParameterField.createValueField(TAG_2))
            .versionRegex(ParameterField.createValueField(TAG_REGEX_2))
            .build();

    doReturn(googleArtifactRegistryConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REGION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REPO_NAME).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(PKG).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PKG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(PROJECT).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PROJECT_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    doReturn(GAR_BUILD_DETAILS_DTO)
        .when(garResourceService)
        .getLastSuccessfulBuild(eq(IDENTIFIER_REF), eq(REGION), eq(REPO_NAME), eq(PROJECT), eq(PKG),
            eq(GAR_REQUEST_DTO_2), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2GAR(CONNECTOR_REF_2, REGION_2, REPO_NAME_2, PROJECT_2,
                   PKG_2, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
                   GarRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build(), FQN, SERVICE_REF,
                   GIT_ENTITY_FIND_INFO_DTO))
        .isSameAs(GAR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PKG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PROJECT_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2GCR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .imagePath(ParameterField.<String>builder().value(IMAGE).build())
            .registryHostname(ParameterField.<String>builder().value(HOST).build())
            .tag(ParameterField.<String>builder().value(VERSION).build())
            .tagRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .build();

    doReturn(gcrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(GCR_BUILD_DETAILS_DTO)
        .when(gcrResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), eq(IMAGE), eq(GCR_REQUEST_DTO_2), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getSuccessfulBuildV2GCR(null, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, FQN,
                   SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO,
                   GcrRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(GCR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, HOST, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2GCR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder().build();

    doReturn(gcrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(GCR_BUILD_DETAILS_DTO)
        .when(gcrResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), eq(IMAGE), eq(GCR_REQUEST_DTO_2), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getSuccessfulBuildV2GCR(IMAGE, CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO, GCR_REQUEST_DTO_2))
        .isSameAs(GCR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, HOST, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2GCR_ValuesFromResolvedExpression() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder()
                                              .tag(ParameterField.createValueField(TAG_2))
                                              .tagRegex(ParameterField.createValueField(TAG_REGEX_2))
                                              .registryHostname(ParameterField.createValueField(HOST_2))
                                              .build();

    doReturn(gcrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(GCR_BUILD_DETAILS_DTO)
        .when(gcrResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), eq(IMAGE), eq(GCR_REQUEST_DTO_2), eq(ORG_ID), eq(PROJECT_ID));

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(IMAGE).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(HOST).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, HOST_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(spyartifactResourceUtils.getSuccessfulBuildV2GCR(IMAGE_2, CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID,
                   PROJECT_ID, FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO,
                   GcrRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(GCR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, HOST_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2ECR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    EcrArtifactConfig ecrArtifactConfig =
        EcrArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .imagePath(ParameterField.<String>builder().value(IMAGE).build())
            .tag(ParameterField.<String>builder().value(VERSION).build())
            .tagRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .region(ParameterField.createValueField(REGION))
            .build();

    doReturn(ecrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ECR_BUILD_DETAILS_DTO)
        .when(ecrResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), isNull(), eq(IMAGE), eq(ECR_REQUEST_DTO), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2ECR(null, null, null, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO,
                   EcrRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(ECR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2ECR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().build();

    doReturn(ecrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ECR_BUILD_DETAILS_DTO)
        .when(ecrResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), isNull(), eq(IMAGE), eq(ECR_REQUEST_DTO), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2ECR(null, IMAGE, CONNECTOR_REF, ACCOUNT_ID, ORG_ID,
                   PROJECT_ID, FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO, ECR_REQUEST_DTO))
        .isSameAs(ECR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2ECR_ValuesFromResolvedExpression() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder()
                                              .tag(ParameterField.<String>builder().value(TAG_2).build())
                                              .tagRegex(ParameterField.<String>builder().value(TAG_REGEX_2).build())
                                              .region(ParameterField.createValueField(REGION_2))
                                              .registryId(ParameterField.createValueField(REGISTRY))
                                              .build();

    doReturn(ecrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ECR_BUILD_DETAILS_DTO)
        .when(ecrResourceService)
        .getSuccessfulBuild(
            eq(IDENTIFIER_REF), eq(REGISTRY), eq(IMAGE), eq(ECR_REQUEST_DTO), eq(ORG_ID), eq(PROJECT_ID));

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REGION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(IMAGE).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2ECR(null, IMAGE_2, CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID,
                   PROJECT_ID, FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO,
                   EcrRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(ECR_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2ACR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .tag(ParameterField.<String>builder().value(VERSION).build())
            .tagRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .subscriptionId(ParameterField.createValueField(SUBSCRIPTION))
            .registry(ParameterField.createValueField(REGISTRY))
            .repository(ParameterField.createValueField(REPO_NAME))
            .build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_BUILD_DETAILS_DTO)
        .when(acrResourceService)
        .getLastSuccessfulBuild(IDENTIFIER_REF, SUBSCRIPTION, REGISTRY, REPO_NAME, ORG_ID, PROJECT_ID, ACR_REQUEST_DTO);

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2ACR(null, null, null, null, ACCOUNT_ID, ORG_ID,
                   PROJECT_ID, FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO,
                   AcrRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(ACR_BUILD_DETAILS_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2ACR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_BUILD_DETAILS_DTO)
        .when(acrResourceService)
        .getLastSuccessfulBuild(IDENTIFIER_REF, SUBSCRIPTION, REGISTRY, REPO_NAME, ORG_ID, PROJECT_ID, ACR_REQUEST_DTO);

    assertThat(
        spyartifactResourceUtils.getLastSuccessfulBuildV2ACR(SUBSCRIPTION, REGISTRY, REPO_NAME, CONNECTOR_REF,
            ACCOUNT_ID, ORG_ID, PROJECT_ID, FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO, ACR_REQUEST_DTO))
        .isSameAs(ACR_BUILD_DETAILS_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2ACR_ValuesFromResolvedExpressions() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_BUILD_DETAILS_DTO)
        .when(acrResourceService)
        .getLastSuccessfulBuild(IDENTIFIER_REF, SUBSCRIPTION, REGISTRY, REPO_NAME, ORG_ID, PROJECT_ID, ACR_REQUEST_DTO);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REGISTRY).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(SUBSCRIPTION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REPO_NAME).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(
        spyartifactResourceUtils.getLastSuccessfulBuildV2ACR(SUBSCRIPTION_2, REGISTRY_2, REPO_NAME_2, CONNECTOR_REF_2,
            ACCOUNT_ID, ORG_ID, PROJECT_ID, FQN, SERVICE_REF, PIPELINE_ID, GIT_ENTITY_FIND_INFO_DTO,
            AcrRequestDTO.builder()
                .tag(TAG_2)
                .tagRegex(TAG_REGEX_2)
                .runtimeInputYaml(pipelineYamlWithoutTemplates)
                .build()))
        .isSameAs(ACR_BUILD_DETAILS_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testBuildDetailsV2ACR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .subscriptionId(ParameterField.createValueField(SUBSCRIPTION))
            .registry(ParameterField.createValueField(REGISTRY))
            .repository(ParameterField.createValueField(REPO_NAME))
            .build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_RESPONSE_DTO)
        .when(acrResourceService)
        .getBuildDetails(IDENTIFIER_REF, SUBSCRIPTION, REGISTRY, REPO_NAME, ORG_ID, PROJECT_ID);

    assertThat(spyartifactResourceUtils.getBuildDetailsV2ACR(null, null, null, null, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_RESPONSE_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testBuildDetailsV2ACR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_RESPONSE_DTO)
        .when(acrResourceService)
        .getBuildDetails(IDENTIFIER_REF, SUBSCRIPTION, REGISTRY, REPO_NAME, ORG_ID, PROJECT_ID);

    assertThat(
        spyartifactResourceUtils.getBuildDetailsV2ACR(SUBSCRIPTION, REGISTRY, REPO_NAME, CONNECTOR_REF, ACCOUNT_ID,
            ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_RESPONSE_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testBuildDetailsV2ACR_ValuesFromResolvedExpressions() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_RESPONSE_DTO)
        .when(acrResourceService)
        .getBuildDetails(IDENTIFIER_REF, SUBSCRIPTION, REGISTRY, REPO_NAME, ORG_ID, PROJECT_ID);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REGISTRY).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(SUBSCRIPTION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REPO_NAME).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(spyartifactResourceUtils.getBuildDetailsV2ACR(SUBSCRIPTION_2, REGISTRY_2, REPO_NAME_2, CONNECTOR_REF_2,
                   ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
                   pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_RESPONSE_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureRepositoriesV3ACR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .subscriptionId(ParameterField.createValueField(SUBSCRIPTION))
            .registry(ParameterField.createValueField(REGISTRY))
            .build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_REPOSITORIES_DTO)
        .when(acrResourceService)
        .getRepositories(IDENTIFIER_REF, ORG_ID, PROJECT_ID, SUBSCRIPTION, REGISTRY);

    assertThat(spyartifactResourceUtils.getAzureRepositoriesV3(null, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null,
                   null, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_REPOSITORIES_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureRepositoriesV3ACR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_REPOSITORIES_DTO)
        .when(acrResourceService)
        .getRepositories(IDENTIFIER_REF, ORG_ID, PROJECT_ID, SUBSCRIPTION, REGISTRY);

    assertThat(
        spyartifactResourceUtils.getAzureRepositoriesV3(CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            SUBSCRIPTION, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_REPOSITORIES_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureRepositoriesV3ACR_ValuesFromResolvedExpressions() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_REPOSITORIES_DTO)
        .when(acrResourceService)
        .getRepositories(IDENTIFIER_REF, ORG_ID, PROJECT_ID, SUBSCRIPTION, REGISTRY);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REGISTRY).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(SUBSCRIPTION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(
        spyartifactResourceUtils.getAzureRepositoriesV3(CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            SUBSCRIPTION_2, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_REPOSITORIES_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REGISTRY_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureContainerRegisteriesV3ACR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .subscriptionId(ParameterField.createValueField(SUBSCRIPTION))
            .build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_REGISTRIES_DTO)
        .when(acrResourceService)
        .getRegistries(IDENTIFIER_REF, ORG_ID, PROJECT_ID, SUBSCRIPTION);

    assertThat(spyartifactResourceUtils.getAzureContainerRegisteriesV3(null, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   PIPELINE_ID, null, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_REGISTRIES_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureContainerRegisteriesV3ACR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_REGISTRIES_DTO)
        .when(acrResourceService)
        .getRegistries(IDENTIFIER_REF, ORG_ID, PROJECT_ID, SUBSCRIPTION);

    assertThat(spyartifactResourceUtils.getAzureContainerRegisteriesV3(CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   PIPELINE_ID, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_REGISTRIES_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureContainerRegisteriesV3ACR_ValuesFromResolvedExpressions() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(ACR_REGISTRIES_DTO)
        .when(acrResourceService)
        .getRegistries(IDENTIFIER_REF, ORG_ID, PROJECT_ID, SUBSCRIPTION);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(SUBSCRIPTION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(
        spyartifactResourceUtils.getAzureContainerRegisteriesV3(CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID, PROJECT_ID,
            PIPELINE_ID, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(ACR_REGISTRIES_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, SUBSCRIPTION_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureSubscriptionV2ACR_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build()).build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(AZURE_SUBSCRIPTIONS_DTO).when(azureResourceService).getSubscriptions(IDENTIFIER_REF, ORG_ID, PROJECT_ID);

    assertThat(spyartifactResourceUtils.getAzureSubscriptionV2(null, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, FQN,
                   GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(AZURE_SUBSCRIPTIONS_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureSubscriptionV2ACR_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(AZURE_SUBSCRIPTIONS_DTO).when(azureResourceService).getSubscriptions(IDENTIFIER_REF, ORG_ID, PROJECT_ID);

    assertThat(spyartifactResourceUtils.getAzureSubscriptionV2(CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(AZURE_SUBSCRIPTIONS_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAzureSubscriptionV2ACR_ValuesFromResolvedExpressions() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().build();

    doReturn(acrArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doNothing()
        .when(spyartifactResourceUtils)
        .resolveParameterFieldValues(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID),
            eq(pipelineYamlWithoutTemplates), any(), eq(FQN), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));

    doReturn(AZURE_SUBSCRIPTIONS_DTO).when(azureResourceService).getSubscriptions(IDENTIFIER_REF, ORG_ID, PROJECT_ID);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(spyartifactResourceUtils.getAzureSubscriptionV2(CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, pipelineYamlWithoutTemplates, SERVICE_REF))
        .isSameAs(AZURE_SUBSCRIPTIONS_DTO);

    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Nexus_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    NexusRegistryDockerConfig nexusRegistryDockerConfig = NexusRegistryDockerConfig.builder()
                                                              .repositoryPort(ParameterField.createValueField(PORT))
                                                              .artifactPath(ParameterField.createValueField(IMAGE))
                                                              .repositoryUrl(ParameterField.createValueField(URL))
                                                              .build();
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .tag(ParameterField.<String>builder().value(VERSION).build())
            .tagRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .repositoryFormat(ParameterField.createValueField(NexusConstant.DOCKER))
            .repository(ParameterField.createValueField(REPO_NAME))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .build();

    doReturn(nexusRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(NEXUS_BUILD_DETAILS_DTO)
        .when(nexusResourceService)
        .getSuccessfulBuild(
            IDENTIFIER_REF, REPO_NAME, PORT, IMAGE, NexusConstant.DOCKER, URL, NEXUS_REQUEST_DTO, ORG_ID, PROJECT_ID);

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Nexus3(null, null, null, null, null, null, ACCOUNT_ID,
                   ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF,
                   NexusRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(NEXUS_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PORT, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Nexus_ValuesFromResolvedExpression() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder()
            .tag(ParameterField.<String>builder().value(TAG_2).build())
            .tagRegex(ParameterField.<String>builder().value(TAG_REGEX_2).build())
            .repositoryFormat(ParameterField.createValueField(NexusConstant.DOCKER))
            .build();

    doReturn(nexusRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(NEXUS_BUILD_DETAILS_DTO)
        .when(nexusResourceService)
        .getSuccessfulBuild(
            IDENTIFIER_REF, REPO_NAME, PORT, IMAGE, NexusConstant.DOCKER, URL, NEXUS_REQUEST_DTO, ORG_ID, PROJECT_ID);

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(IMAGE).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(PORT).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PORT_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(URL).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REPO_NAME).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(
        spyartifactResourceUtils.getLastSuccessfulBuildV2Nexus3(REPO_NAME_2, PORT_2, IMAGE_2, NexusConstant.DOCKER,
            URL_2, CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
            SERVICE_REF, NexusRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(NEXUS_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PORT_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Nexus_NonDocker() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repositoryFormat(ParameterField.createValueField(NexusConstant.MAVEN))
            .build();

    doReturn(nexusRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    assertThatThrownBy(()
                           -> spyartifactResourceUtils.getLastSuccessfulBuildV2Nexus3(null, null, null, null, null,
                               null, ACCOUNT_ID, ORG_ID, PROJECT_ID, null, FQN, null, SERVICE_REF, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please select a docker artifact");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Nexus_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repositoryFormat(ParameterField.createValueField(NexusConstant.DOCKER))
            .build();

    doReturn(nexusRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(NEXUS_BUILD_DETAILS_DTO)
        .when(nexusResourceService)
        .getSuccessfulBuild(
            IDENTIFIER_REF, REPO_NAME, PORT, IMAGE, NexusConstant.DOCKER, URL, NEXUS_REQUEST_DTO, ORG_ID, PROJECT_ID);

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Nexus3(REPO_NAME, PORT, IMAGE, NexusConstant.DOCKER,
                   URL, CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
                   SERVICE_REF, NEXUS_REQUEST_DTO))
        .isSameAs(NEXUS_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, PORT, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Artifactory_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .artifactPath(ParameterField.createValueField(IMAGE))
            .repositoryFormat(ParameterField.createValueField(DOCKER))
            .repository(ParameterField.createValueField(REPO_NAME))
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .tag(ParameterField.<String>builder().value(VERSION).build())
            .tagRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .repositoryUrl(ParameterField.createValueField(URL))
            .build();

    doReturn(artifactoryRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO)
        .when(artifactoryResourceService)
        .getSuccessfulBuild(
            eq(IDENTIFIER_REF), eq(REPO_NAME), eq(IMAGE), eq(DOCKER), eq(URL), any(), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Artifactory(null, null, null, null, null, ACCOUNT_ID,
                   ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF,
                   ArtifactoryRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Artifactory_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
        ArtifactoryRegistryArtifactConfig.builder().build();

    doReturn(artifactoryRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO)
        .when(artifactoryResourceService)
        .getSuccessfulBuild(
            eq(IDENTIFIER_REF), eq(REPO_NAME), eq(IMAGE), eq(DOCKER), eq(URL), any(), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Artifactory(REPO_NAME, IMAGE, DOCKER, URL,
                   CONNECTOR_REF, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
                   SERVICE_REF, ARTIFACTORY_REQUEST_DTO))
        .isSameAs(ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Artifactory_ValuesFromResolvedExpression() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .tag(ParameterField.createValueField(TAG_2))
            .tagRegex(ParameterField.createValueField(TAG_REGEX_2))
            .build();

    doReturn(artifactoryRegistryArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO)
        .when(artifactoryResourceService)
        .getSuccessfulBuild(
            eq(IDENTIFIER_REF), eq(REPO_NAME), eq(IMAGE), eq(DOCKER), eq(URL), any(), eq(ORG_ID), eq(PROJECT_ID));

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(IMAGE).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(URL).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(REPO_NAME).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Artifactory(REPO_NAME_2, IMAGE_2, DOCKER, URL_2,
                   CONNECTOR_REF_2, ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
                   SERVICE_REF, ArtifactoryRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build()))
        .isSameAs(ARTIFACTORY_DOCKER_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, URL_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, REPO_NAME_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Docker_ValuesFromConfig() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build())
            .imagePath(ParameterField.<String>builder().value(IMAGE).build())
            .tag(ParameterField.<String>builder().value(VERSION).build())
            .tagRegex(ParameterField.<String>builder().value(VERSION_REGEX).build())
            .build();

    doReturn(dockerHubArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(DOCKER_BUILD_DETAILS_DTO)
        .when(dockerResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), eq(IMAGE), eq(DOCKER_REQUEST_DTO), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Docker(null, null, null, ACCOUNT_ID, ORG_ID, PROJECT_ID,
                   PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
                   DockerRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build(), SERVICE_REF))
        .isSameAs(DOCKER_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Docker_ValuesFromParams() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    DockerHubArtifactConfig dockerHubArtifactConfig = DockerHubArtifactConfig.builder().build();

    doReturn(dockerHubArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(DOCKER_BUILD_DETAILS_DTO)
        .when(dockerResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), eq(IMAGE), eq(DOCKER_REQUEST_DTO), eq(ORG_ID), eq(PROJECT_ID));

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Docker(IMAGE, CONNECTOR_REF, null, ACCOUNT_ID, ORG_ID,
                   PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO, DOCKER_REQUEST_DTO, SERVICE_REF))
        .isSameAs(DOCKER_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, VERSION_REGEX, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuildV2Docker_ValuesFromResolvedExpression() {
    ArtifactResourceUtils spyartifactResourceUtils = spy(artifactResourceUtils);

    DockerHubArtifactConfig dockerHubArtifactConfig = DockerHubArtifactConfig.builder()
                                                          .tag(ParameterField.createValueField(TAG_2))
                                                          .tagRegex(ParameterField.createValueField(TAG_REGEX_2))
                                                          .build();

    doReturn(dockerHubArtifactConfig)
        .when(spyartifactResourceUtils)
        .locateArtifactInService(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_REF, FQN);

    doReturn(DOCKER_BUILD_DETAILS_DTO)
        .when(dockerResourceService)
        .getSuccessfulBuild(eq(IDENTIFIER_REF), eq(IMAGE), eq(DOCKER_REQUEST_DTO), eq(ORG_ID), eq(PROJECT_ID));

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(CONNECTOR_REF).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(IMAGE).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder().value(VERSION_REGEX).build())
        .when(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);

    assertThat(spyartifactResourceUtils.getLastSuccessfulBuildV2Docker(IMAGE_2, CONNECTOR_REF_2, null, ACCOUNT_ID,
                   ORG_ID, PROJECT_ID, PIPELINE_ID, FQN, GIT_ENTITY_FIND_INFO_DTO,
                   DockerRequestDTO.builder().runtimeInputYaml(pipelineYamlWithoutTemplates).build(), SERVICE_REF))
        .isSameAs(DOCKER_BUILD_DETAILS_DTO);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, IMAGE_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, TAG_REGEX_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
    verify(spyartifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
            pipelineYamlWithoutTemplates, CONNECTOR_REF_2, FQN, GIT_ENTITY_FIND_INFO_DTO, SERVICE_REF, null);
  }

  private void mockEnvironmentGetCall() {
    when(environmentService.get(anyString(), anyString(), anyString(), eq("env1"), anyBoolean()))
        .thenReturn(Optional.of(Environment.builder()
                                    .name("env1")
                                    .identifier("env1")
                                    .yaml("environment:\n"
                                        + "    name: env1\n"
                                        + "    identifier: env1\n"
                                        + "    orgIdentifier: org\n"
                                        + "    tags: {}")
                                    .build()));
  }

  private void mockServiceGetCall(String svcId) {
    when(serviceEntityService.get(anyString(), anyString(), anyString(), eq(svcId), anyBoolean()))
        .thenReturn(Optional.of(ServiceEntity.builder()
                                    .name("svc1")
                                    .identifier("svc1")
                                    .yaml("service:\n"
                                        + "    name: svc1\n"
                                        + "    identifier: svc1\n"
                                        + "    tags: {}")
                                    .build()));
  }

  private void mockEnvironmentV2GetCall() {
    when(environmentService.get(anyString(), anyString(), anyString(), eq("env1"), anyBoolean()))
        .thenReturn(Optional.of(Environment.builder()
                                    .name("env1")
                                    .identifier("env1")
                                    .yaml("environment:\n"
                                        + "    name: env1\n"
                                        + "    identifier: env1\n"
                                        + "    description: \"\"\n"
                                        + "    tags: {}\n"
                                        + "    type: Production\n"
                                        + "    orgIdentifier: default\n"
                                        + "    projectIdentifier: org\n"
                                        + "    variables: []")
                                    .build()));
  }

  private void mockServiceV2GetCall(String svcId) {
    when(serviceEntityService.get(anyString(), anyString(), anyString(), eq(svcId), anyBoolean()))
        .thenReturn(Optional.of(ServiceEntity.builder()
                                    .name("svc1")
                                    .identifier("svc1")
                                    .yaml("service:\n"
                                        + "  name: svc1\n"
                                        + "  identifier: svc1\n"
                                        + "  tags: {}\n"
                                        + "  serviceDefinition:\n"
                                        + "    spec:\n"
                                        + "      artifacts:\n"
                                        + "        sidecars:\n"
                                        + "          - sidecar:\n"
                                        + "              spec:\n"
                                        + "                connectorRef: Docker_Connector\n"
                                        + "                imagePath: <+service.name>\n"
                                        + "                tag: <+input>\n"
                                        + "              identifier: sidecar_id\n"
                                        + "              type: DockerRegistry\n"
                                        + "        primary:\n"
                                        + "          spec:\n"
                                        + "            connectorRef: account.harnessImage\n"
                                        + "            imagePath: library/nginx\n"
                                        + "            tag: <+input>\n"
                                        + "          type: DockerRegistry\n"
                                        + "      variables:\n"
                                        + "        - name: svar1\n"
                                        + "          type: String\n"
                                        + "          value: ServiceVariable1\n"
                                        + "        - name: svar2\n"
                                        + "          type: String\n"
                                        + "          value: ServiceVariable2\n"
                                        + "    type: Kubernetes\n"
                                        + "  gitOpsEnabled: false")
                                    .build()));
  }

  private void mockMergeInputSetCall(String yaml) throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            MergeInputSetResponseDTOPMS.builder().isErrorResponse(false).completePipelineYaml(yaml).build())));
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
