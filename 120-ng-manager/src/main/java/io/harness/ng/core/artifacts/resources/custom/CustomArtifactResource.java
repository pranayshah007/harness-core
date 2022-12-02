/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.resources.custom.CustomResourceService;
import io.harness.data.algorithm.HashGenerator;
import io.harness.exception.HintException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/custom")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class CustomArtifactResource {
  private final CustomResourceService customResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;
  @POST
  @Path("builds")
  @Hidden
  @ApiOperation(value = "Gets Job details for Custom Artifact", nickname = "getJobDetailsForCustom")
  public ResponseDTO<List<BuildDetails>> getBuildsDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @RequestBody(required = true, description = "Shell Script to fetch builds")
      CustomScriptInfo customScriptInfo, @NotNull @QueryParam("versionPath") String versionPath,
      @NotNull @QueryParam("arrayPath") String arrayPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("fqnPath") String fqnPath, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    String script = customScriptInfo.getScript();
    List<NGVariable> inputs = customScriptInfo.getInputs();
    List<TaskSelectorYaml> delegateSelector = customScriptInfo.getDelegateSelector();
    int secretFunctor = HashGenerator.generateIntegerHash();
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      CustomArtifactConfig customArtifactConfig = (CustomArtifactConfig) artifactSpecFromService;
      if (isEmpty(customScriptInfo.getScript())) {
        if (customArtifactConfig.getScripts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo() != null
            && customArtifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource() != null
            && customArtifactConfig.getScripts()
                    .getFetchAllArtifacts()
                    .getShellScriptBaseStepInfo()
                    .getSource()
                    .getSpec()
                != null) {
          CustomScriptInlineSource customScriptInlineSource =
              (CustomScriptInlineSource) customArtifactConfig.getScripts()
                  .getFetchAllArtifacts()
                  .getShellScriptBaseStepInfo()
                  .getSource()
                  .getSpec();
          if (customScriptInlineSource.getScript() != null
              && isNotEmpty(customScriptInlineSource.getScript().fetchFinalValue().toString())) {
            script = customScriptInlineSource.getScript().fetchFinalValue().toString();
          }
        }
        if (customScriptInfo.getInputs() != null && isEmpty(customScriptInfo.getInputs())) {
          inputs = customArtifactConfig.getInputs();
        }
        if (customScriptInfo.getDelegateSelector() != null && isEmpty(customScriptInfo.getDelegateSelector())) {
          delegateSelector = (List<TaskSelectorYaml>) customArtifactConfig.getDelegateSelectors().fetchFinalValue();
        }
      }

      if (isEmpty(arrayPath)
          && customArtifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath() != null) {
        arrayPath = customArtifactConfig.getScripts()
                        .getFetchAllArtifacts()
                        .getArtifactsArrayPath()
                        .fetchFinalValue()
                        .toString();
      }
      if (isEmpty(versionPath) && customArtifactConfig.getScripts().getFetchAllArtifacts().getVersionPath() != null) {
        versionPath =
            customArtifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().fetchFinalValue().toString();
      }
    }

    if (isEmpty(script) || script.equalsIgnoreCase("<+input>")) {
      return ResponseDTO.newResponse(Collections.emptyList());
    }
    if (isEmpty(arrayPath) || arrayPath.equalsIgnoreCase("<+input>")) {
      throw new HintException("Array path can not be empty");
    }

    if (isEmpty(versionPath) || versionPath.equalsIgnoreCase("<+input>")) {
      throw new HintException("Version path can not be empty");
    }
    if (isNotEmpty(customScriptInfo.getRuntimeInputYaml())) {
      script =
          artifactResourceUtils.getResolvedExpression(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
              customScriptInfo.getRuntimeInputYaml(), script, fqnPath, gitEntityBasicInfo, serviceRef, secretFunctor);
      arrayPath =
          artifactResourceUtils.getResolvedImagePath(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
              customScriptInfo.getRuntimeInputYaml(), arrayPath, fqnPath, gitEntityBasicInfo, serviceRef);
      versionPath =
          artifactResourceUtils.getResolvedImagePath(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
              customScriptInfo.getRuntimeInputYaml(), versionPath, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    List<BuildDetails> buildDetails = customResourceService.getBuilds(script, versionPath, arrayPath,
        NGVariablesUtils.getStringMapVariables(inputs, 0L), accountId, orgIdentifier, projectIdentifier, secretFunctor,
        delegateSelector);
    return ResponseDTO.newResponse(buildDetails);
  }
}
