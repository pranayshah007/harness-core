/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactResourceUtilsTest extends NgManagerTestBase {
  @InjectMocks ArtifactResourceUtils artifactResourceUtils;
  @Mock PipelineServiceClient pipelineServiceClient;
  @Mock TemplateResourceClient templateResourceClient;

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
      + "                                  sidecars: []\n"
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

    String imagePath = artifactResourceUtils.getResolvedImagePath(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+pipeline.variables.image_path>",
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build());
    assertThat(imagePath).isEqualTo("library/nginx");
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
    when(templateResourceClient.applyTemplatesOnGivenYaml(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeTemplateToYamlCall);
    when(mergeTemplateToYamlCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateMergeResponseDTO.builder().mergedPipelineYaml(pipelineYamlWithoutTemplates).build())));

    String imagePath = artifactResourceUtils.getResolvedImagePath(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "",
        "<+pipeline.variables.image_path>",
        "pipeline.stages.test.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        GitEntityFindInfoDTO.builder().build());
    assertThat(imagePath).isEqualTo("library/nginx");
    verify(pipelineServiceClient)
        .getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(templateResourceClient).applyTemplatesOnGivenYaml(any(), any(), any(), any(), any(), any(), any());
  }
}
