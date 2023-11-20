/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.api;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.InputSetsApi;
import io.harness.spec.server.pipeline.v1.model.GitImportInfo;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.GitMetadataUpdateResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetImportRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.MergeInputSetRequestBody;
import io.harness.spec.server.pipeline.v1.model.MergeInputSetResponseBody;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class InputSetsApiImpl implements InputSetsApi {
  private final PMSInputSetService pmsInputSetService;
  private final InputSetsApiUtils inputSetsApiUtils;
  private final ValidateAndMergeHelper validateAndMergeHelper;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Timed
  @ResponseMetered
  public Response createInputSet(InputSetCreateRequestBody requestBody, @ResourceIdentifier String pipeline,
      @OrgIdentifier String org, @ProjectIdentifier String project, @AccountIdentifier String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("Input Set create request body must not be null.");
    }
    GitAwareContextHelper.populateGitDetails(InputSetsApiUtils.populateGitCreateDetails(requestBody.getGitDetails()));
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, requestBody.getInputSetYaml());
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityFromVersion(
        InputSetsApiUtils.mapCreateToRequestInfoDTO(requestBody), account, org, project, pipeline, inputSetVersion);
    log.info(String.format("Create input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        entity.getIdentifier(), pipeline, project, org, account));
    InputSetResponseBody inputSetResponse =
        inputSetsApiUtils.getInputSetResponse(pmsInputSetService.create(entity, true));
    return Response.status(201).entity(inputSetResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  @Timed
  @ResponseMetered
  public Response deleteInputSet(@OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account) {
    log.info(String.format("Deleting input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSet, pipeline, project, org, account));
    pmsInputSetService.delete(account, org, project, pipeline, inputSet, null);
    return Response.status(204).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  public Response getInputSet(@OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, String branchGitX,
      String parentEntityConnectorRef, String parentEntityRepoName, Boolean loadFromFallbackBranch,
      String loadFromCache) {
    if (null == loadFromFallbackBranch) {
      loadFromFallbackBranch = false;
    }
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branchGitX)
                                                 .parentEntityConnectorRef(parentEntityConnectorRef)
                                                 .parentEntityRepoName(parentEntityRepoName)
                                                 .build());
    log.info(String.format("Retrieving input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSet, pipeline, project, org, account));
    Optional<InputSetEntity> optionalInputSetEntity = Optional.empty();
    try {
      optionalInputSetEntity = pmsInputSetService.get(account, org, project, pipeline, inputSet, false, null, null,
          true, loadFromFallbackBranch, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    } catch (InvalidInputSetException e) {
      return Response.ok()
          .entity(inputSetsApiUtils.getInputSetResponseWithError(
              e.getInputSetEntity(), (InputSetErrorWrapperDTOPMS) e.getMetadata()))
          .build();
    }
    if (!optionalInputSetEntity.isPresent()) {
      throw new EntityNotFoundException(
          String.format("InputSet with the given ID: %s does not exist or has been deleted", inputSet));
    }
    InputSetResponseBody inputSetResponse = inputSetsApiUtils.getInputSetResponse(optionalInputSetEntity.get());
    return Response.ok().entity(inputSetResponse).build();
  }

  @Override
  public Response importInputSetFromGit(@NotNull String pipeline, @OrgIdentifier String org,
      @ProjectIdentifier String project, String inputSet, @Valid InputSetImportRequestBody body,
      String harnessAccount) {
    GitImportInfo gitImportInfo = body.getGitImportInfo();
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(gitImportInfo.getBranchName())
                                                 .connectorRef(gitImportInfo.getConnectorRef())
                                                 .filePath(gitImportInfo.getFilePath())
                                                 .repoName(gitImportInfo.getRepoName())
                                                 .build());
    InputSetImportRequestDTO inputSetImportRequestDTO =
        InputSetImportRequestDTO.builder()
            .inputSetName(body.getInputSetImportRequest().getInputSetName())
            .inputSetDescription(body.getInputSetImportRequest().getInputSetDescription())
            .build();
    InputSetEntity inputSetEntity = pmsInputSetService.importInputSetFromRemote(harnessAccount, org, project, pipeline,
        inputSet, inputSetImportRequestDTO, Boolean.TRUE.equals(body.getGitImportInfo().isIsForceImport()));
    InputSetMoveConfigResponseBody inputSetMoveConfigResponseBody = new InputSetMoveConfigResponseBody();
    inputSetMoveConfigResponseBody.setInputSetIdentifier(inputSetEntity.getIdentifier());
    return Response.ok().entity(inputSetMoveConfigResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  public Response listInputSets(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String account, Integer page, Integer limit,
      String searchTerm, String sort, String order) {
    log.info(String.format(
        "Get List of input sets for pipeline %s in project %s, org %s, account %s", pipeline, project, org, account));
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(
        account, org, project, pipeline, InputSetListTypePMS.INPUT_SET, searchTerm, false);
    Pageable pageRequest = PageUtils.getPageRequest(page, limit, PipelinesApiUtils.getSorting(sort, order),
        Sort.by(Direction.DESC, InputSetEntityKeys.lastUpdatedAt));
    Page<InputSetEntity> inputSetEntities = pmsInputSetService.list(criteria, pageRequest, account, org, project);

    Page<InputSetResponseBody> inputSetList = inputSetEntities.map(inputSetsApiUtils::getInputSetResponse);

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, inputSetList.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(inputSetList.getContent()).build();
  }

  @Override
  public Response mergedInputSets(@NotNull String pipeline, String org, String project,
      @Valid MergeInputSetRequestBody body, String harnessAccount, String loadFromCache, String pipelineRepoId,
      String pipelineBranch, String branchName, String parentEntityConnectorRef, String parentEntityRepoName) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branchName)
                                                 .parentEntityConnectorRef(parentEntityConnectorRef)
                                                 .parentEntityRepoName(parentEntityRepoName)
                                                 .build());
    inputSetsApiUtils.checkAndSetContextIfGetOnlyFileContentEnabled(harnessAccount, body.isGetOnlyFileContent());
    List<String> inputSetReferences = body.getInputSetReferences();
    String mergedYaml = null;
    MergeInputSetResponseBody mergeInputSetResponseBody = new MergeInputSetResponseBody();
    try {
      mergedYaml =
          validateAndMergeHelper.getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(harnessAccount,
              org, project, pipeline, inputSetReferences, pipelineBranch, pipelineRepoId, body.getStageIdentifiers(),
              body.getLastYamlToMerge(), GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    } catch (InvalidInputSetException e) {
      InputSetErrorWrapperDTOPMS errorWrapperDTO = (InputSetErrorWrapperDTOPMS) e.getMetadata();
      mergeInputSetResponseBody.setIsErrorResponse(true);
      mergeInputSetResponseBody.setInputsetErrorWrapper(inputSetsApiUtils.getInputSetErrorWrapper(errorWrapperDTO));
      Response.ok().entity(mergeInputSetResponseBody).build();
    }
    String fullYaml = "";
    if (body.isWithMergedPipelineYaml()) {
      fullYaml = validateAndMergeHelper.mergeInputSetIntoPipeline(harnessAccount, org, project, pipeline, mergedYaml,
          pipelineBranch, pipelineRepoId, body.getStageIdentifiers(),
          GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    }
    mergeInputSetResponseBody.setIsErrorResponse(false);
    mergeInputSetResponseBody.setInputsYamlMerged(mergedYaml);
    mergeInputSetResponseBody.setMergedPipelineYaml(fullYaml);
    return Response.ok().entity(mergeInputSetResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  @Timed
  @ResponseMetered
  public Response updateInputSet(InputSetUpdateRequestBody requestBody, @ResourceIdentifier String pipeline,
      @OrgIdentifier String org, @ProjectIdentifier String project, String inputSet,
      @AccountIdentifier String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("Input Set update request body must not be null.");
    }
    if (!Objects.equals(inputSet, requestBody.getIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Input Set identifier in Request Body to be [%s], but was [%s]", inputSet,
              requestBody.getIdentifier()));
    }
    GitAwareContextHelper.populateGitDetails(InputSetsApiUtils.populateGitUpdateDetails(requestBody.getGitDetails()));
    log.info(String.format("Updating input set with identifier %s for pipeline %s in project %s, org %s, account %s",
        inputSet, pipeline, project, org, account));
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, requestBody.getInputSetYaml());
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityFromVersion(
        InputSetsApiUtils.mapUpdateToRequestInfoDTO(requestBody), account, org, project, pipeline, inputSetVersion);
    InputSetEntity updatedEntity = pmsInputSetService.update(ChangeType.MODIFY, entity, true);
    InputSetResponseBody inputSetResponse = inputSetsApiUtils.getInputSetResponse(updatedEntity);
    return Response.ok().entity(inputSetResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public Response inputSetsMoveConfig(
      String org, String project, String inputSet, @Valid InputSetMoveConfigRequestBody requestBody, String account) {
    if (requestBody == null) {
      throw new InvalidRequestException("InputSet MoveConfig request body must not be null.");
    }
    if (!Objects.equals(inputSet, requestBody.getInputSetIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected InputSet identifier in Request Body to be [%s], but was [%s]", inputSet,
              requestBody.getInputSetIdentifier()));
    }
    log.info(
        String.format("Move Config for InputSet of move type %s with identifier %s in project %s, org %s, account %s",
            requestBody.getMoveConfigOperationType().toString(), inputSet, project, org, account));
    GitAwareContextHelper.populateGitDetails(PipelinesApiUtils.populateGitMoveDetails(requestBody.getGitDetails()));
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperation = InputSetsApiUtils.buildMoveConfigOperationDTO(
        requestBody.getGitDetails(), requestBody.getMoveConfigOperationType(), requestBody.getPipelineIdentifier());
    InputSetEntity movedInputSetEntity =
        pmsInputSetService.moveConfig(account, org, project, inputSet, inputSetMoveConfigOperation);
    InputSetMoveConfigResponseBody responseBody = new InputSetMoveConfigResponseBody();
    responseBody.setInputSetIdentifier(movedInputSetEntity.getIdentifier());
    return Response.ok().entity(responseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT)
  public Response updateInputSetGitMetadata(@ResourceIdentifier @NotNull String pipeline, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String inputSet, @Valid GitMetadataUpdateRequestBody body,
      @AccountIdentifier String harnessAccount) {
    String inputSetAfterUpdate = pmsInputSetService.updateGitMetadata(harnessAccount, org, project, pipeline, inputSet,
        PMSUpdateGitDetailsParams.builder()
            .connectorRef(body.getConnectorRef())
            .repoName(body.getRepoName())
            .filePath(body.getFilePath())
            .build());

    GitMetadataUpdateResponseBody gitMetadataUpdateResponseBody = new GitMetadataUpdateResponseBody();
    gitMetadataUpdateResponseBody.setEntityIdentifier(inputSetAfterUpdate);
    return Response.ok().entity(gitMetadataUpdateResponseBody).build();
  }
}
