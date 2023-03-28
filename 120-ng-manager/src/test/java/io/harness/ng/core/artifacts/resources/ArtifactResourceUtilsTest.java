/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.NgManagerTestBase;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryImagePathsDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.cdng.artifact.resources.custom.CustomResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.custom.CustomScriptInfo;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
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

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
  @Mock GARResourceService garResourceService;
  @Mock ArtifactoryResourceService artifactoryResourceService;
  @Mock AccessControlClient accessControlClient;
  @Mock CustomResourceService customResourceService;
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "image_expression_test";
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
    String imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+pipeline.variables.image_path>",
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
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
            -> artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, "-1", "",
                "<+pipeline.variables.image_path>",
                "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
                GitEntityFindInfoDTO.builder().build(), ""))
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

    String imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+pipeline.variables.image_path>",
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
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
    String imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+service.name>", "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // resolve expressions like <+service.name> in parallel stage
    imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+service.name>", "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in normal stage
    imagePath =
        artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "<+service.name>",
            "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
            GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in parallel stage
    imagePath =
        artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "<+service.name>",
            "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
            GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // resolve env expressions in normal stage
    imagePath =
        artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "<+env.name>",
            "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
            GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("env1");

    // resolve env expressions in parallel stage
    imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+env.name>", "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
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
        artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "<+service.name>",
            "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
            GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // resolve expressions like <+service.name> in parallel stage
    imagePath =
        artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "<+service.name>",
            "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
            GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in normal stage
    imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+service.name>",
        "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // fqnPath is for sidecar tag in parallel stage
    imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+service.name>",
        "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    // resolve env expressions in normal stage
    imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+env.name>",
        "pipeline.stages.test.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sidecar_id.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("env1");

    // resolve env expressions in parallel stage
    imagePath =
        artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "<+env.name>",
            "pipeline.stages.test2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag",
            GitEntityFindInfoDTO.builder().build(), "");
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

    String imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+service.name>", "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
    assertThat(imagePath).isEqualTo("svc1");

    imagePath = artifactResourceUtils.getResolvedFieldValue(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+env.name>", "pipeline.stages.test2.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build(), "");
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
    String resolvedImagePath =
        artifactResourceUtils.getResolvedFieldValue("a", "o", "p", "p", "", imagePathInput, "fqn", null, null);
    assertThat(resolvedImagePath).isEqualTo(expectedImagePath);
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

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetResolvedConnectorId() throws IOException {
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetCall = mock(Call.class);
    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetCall);
    when(mergeInputSetCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder()
                                                                 .isErrorResponse(false)
                                                                 .completePipelineYaml(pipelineYamlWithoutTemplates)
                                                                 .build())));
    String connectorExpression = "<+pipeline.variables.image_path>";
    String connectorResolvedValue = artifactResourceUtils.getResolvedConnectorId(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        PIPELINE_ID, "", connectorExpression,
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build());
    assertThat(connectorResolvedValue).isEqualTo("library/nginx");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }
}
