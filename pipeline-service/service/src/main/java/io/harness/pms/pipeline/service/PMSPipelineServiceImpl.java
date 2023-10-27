/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
import static io.harness.pms.pipeline.service.PMSPipelineServiceStepHelper.LIBRARY;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineSettingsService;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.PipelineException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.EntityObjectIdUtils;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.pms.pipeline.StepPalleteModuleInfo;
import io.harness.pms.pipeline.filters.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.helper.PipelineAsyncValidationHelper;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.PipelineValidationService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.schema.inputs.beans.YamlInputDetails;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.InternalServerErrorException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineServiceImpl implements PMSPipelineService {
  public static final String ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM = "Error connecting to systems upstream";
  public static final String EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE =
      "Events framework is down for Pipeline Service.";
  public static final String TEMPLATE_REF_PIPELINE = "template_ref_by_pipeline";
  public static final String INVALID_YAML_IN_NODE = "Invalid yaml in node [%s]";
  @Inject private final PMSPipelineRepository pmsPipelineRepository;
  @Inject private final PmsSdkInstanceService pmsSdkInstanceService;
  @Inject private final PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;

  @Inject private final PipelineGovernanceService pipelineGovernanceService;
  @Inject private final PMSPipelineServiceStepHelper pmsPipelineServiceStepHelper;
  @Inject private final GitSyncSdkService gitSyncSdkService;
  @Inject private final CommonStepInfo commonStepInfo;
  @Inject private final PipelineCloneHelper pipelineCloneHelper;
  @Inject private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private final PipelineSettingsService pipelineSettingsService;
  @Inject private final EntitySetupUsageClient entitySetupUsageClient;
  @Inject private final PipelineAsyncValidationService pipelineAsyncValidationService;
  @Inject private final PipelineValidationService pipelineValidationService;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject @Named("PRIVILEGED") private OrganizationClient organizationClient;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject GitXSettingsHelper gitXSettingsHelper;
  @Inject private final AccountClient accountClient;
  @Inject NGSettingsClient settingsClient;
  @Inject private final GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final PMSYamlSchemaService pmsYamlSchemaService;

  public static final String CREATING_PIPELINE = "creating new pipeline";
  public static final String UPDATING_PIPELINE = "updating existing pipeline";

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Pipeline [%s] under Project[%s], Organization [%s] already exists or has been deleted.";

  private static final int MAX_LIST_SIZE = 1000;
  private static final String REPO_LIST_SIZE_EXCEPTION = "The size of unique repository list is greater than [%d]";

  public static final String DEFAULT = "__default__";

  @Override
  public PipelineCRUDResult validateAndCreatePipeline(
      PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceFails) {
    try {
      if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
        log.info("Creating Draft Pipeline with identifier: {}", pipelineEntity.getIdentifier());
        return createPipeline(pipelineEntity);
      }

      PMSPipelineServiceHelper.validatePresenceOfRequiredFields(pipelineEntity);
      applyGitXSettingsIfApplicable(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier());
      checkProjectExists(
          pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier());

      GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
          pipelineEntity, throwExceptionIfGovernanceFails, false);
      try {
        if (governanceMetadata.getDeny()) {
          return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).build();
        }
        // TODO: As part of this ticket https://harness.atlassian.net/browse/CDS-70970, we should publish the setup
        // usages after the entity has been created
        PipelineEntity entityWithUpdatedInfo = pipelineEntity;
        // If PIE_ASYNC_FILTER_CREATION is ON, then we do filter creation async
        if (!pmsFeatureFlagHelper.isEnabled(pipelineEntity.getAccountId(), FeatureName.PIE_ASYNC_FILTER_CREATION)) {
          entityWithUpdatedInfo =
              pmsPipelineServiceHelper.updatePipelineInfo(pipelineEntity, pipelineEntity.getHarnessVersion());
        }

        PipelineEntity createdEntity;
        PipelineCRUDResult pipelineCRUDResult = createPipeline(entityWithUpdatedInfo);
        createdEntity = pipelineCRUDResult.getPipelineEntity();
        computeReferencesIfRemotePipeline(createdEntity);
        try {
          String branchInRequest = GitAwareContextHelper.getBranchInRequest();
          pipelineAsyncValidationService.createRecordForSuccessfulSyncValidation(createdEntity,
              GitAwareContextHelper.DEFAULT.equals(branchInRequest) ? "" : branchInRequest, governanceMetadata,
              Action.CRUD);
        } catch (Exception e) {
          log.error("Unable to save validation event for Pipeline: " + e.getMessage(), e);
        }
        return PipelineCRUDResult.builder()
            .governanceMetadata(governanceMetadata)
            .pipelineEntity(createdEntity)
            .build();
      } catch (IOException ex) {
        log.error(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
        throw new InvalidYamlException(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
      }
    } catch (NGTemplateException ex) {
      throw new PipelineException(
          PipelineException.PIPELINE_CREATE_MESSAGE, ex, ErrorCode.NG_PIPELINE_CREATE_EXCEPTION);
    }
  }

  private PipelineCRUDResult createPipeline(PipelineEntity pipelineEntity) {
    PipelineEntity createdEntity;
    try {
      if (gitSyncSdkService.isGitSyncEnabled(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
              pipelineEntity.getProjectIdentifier())) {
        createdEntity = pmsPipelineRepository.saveForOldGitSync(pipelineEntity);
      } else {
        createdEntity = pmsPipelineRepository.save(pipelineEntity);
      }
      pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(createdEntity, CREATING_PIPELINE);
      pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(createdEntity, TEMPLATE_REF_PIPELINE);
      GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
      return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(createdEntity).build();
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(),
                                            pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (EventsFrameworkDownException ex) {
      log.error(EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE, ex);
      throw new InvalidRequestException(ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM, ex);

    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while creating pipeline " + pipelineEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving pipeline [%s]", pipelineEntity.getIdentifier()), e);
      throw new InvalidRequestException(String.format(
          "Error while saving pipeline [%s]: %s", pipelineEntity.getIdentifier(), ExceptionUtils.getMessage(e)));
    }
  }
  @Override
  public PipelineSaveResponse validateAndClonePipeline(ClonePipelineDTO clonePipelineDTO, String accountId) {
    PipelineEntity sourcePipelineEntity = getSourcePipelineEntity(clonePipelineDTO, accountId);

    String sourcePipelineEntityYaml = sourcePipelineEntity.getYaml();

    String destYaml;
    String pipelineName = null;
    String pipelineId = null;
    String sourcePipelineVersion = sourcePipelineEntity.getHarnessVersion();
    switch (sourcePipelineVersion) {
      case HarnessYamlVersion.V1:
        destYaml = pipelineCloneHelper.updatePipelineMetadataInSourceYamlV1(clonePipelineDTO, sourcePipelineEntityYaml);
        pipelineName = clonePipelineDTO.getDestinationConfig().getPipelineName();
        pipelineId = clonePipelineDTO.getDestinationConfig().getPipelineIdentifier();
        break;
      default:
        destYaml = pipelineCloneHelper.updatePipelineMetadataInSourceYaml(
            clonePipelineDTO, sourcePipelineEntityYaml, accountId);
    }
    PipelineEntity destPipelineEntity =
        PMSPipelineDtoMapper.toPipelineEntity(accountId, clonePipelineDTO.getDestinationConfig().getOrgIdentifier(),
            clonePipelineDTO.getDestinationConfig().getProjectIdentifier(), pipelineId, pipelineName, destYaml, false,
            sourcePipelineVersion);

    PipelineCRUDResult pipelineCRUDResult = validateAndCreatePipeline(destPipelineEntity, false);
    GovernanceMetadata destGovernanceMetadata = pipelineCRUDResult.getGovernanceMetadata();
    if (destGovernanceMetadata.getDeny()) {
      return PipelineSaveResponse.builder().governanceMetadata(destGovernanceMetadata).build();
    }
    PipelineEntity clonedPipelineEntity = pipelineCRUDResult.getPipelineEntity();

    return PipelineSaveResponse.builder()
        .governanceMetadata(destGovernanceMetadata)
        .identifier(clonedPipelineEntity.getIdentifier())
        .build();
  }

  @NotNull
  private PipelineEntity getSourcePipelineEntity(ClonePipelineDTO clonePipelineDTO, String accountId) {
    Optional<PipelineEntity> sourcePipelineEntity =
        getPipeline(accountId, clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
            clonePipelineDTO.getSourceConfig().getProjectIdentifier(),
            clonePipelineDTO.getSourceConfig().getPipelineIdentifier(), false, false);

    if (sourcePipelineEntity.isEmpty()) {
      log.error(String.format("Pipeline with id [%s] in org [%s] in project [%s] is not present or deleted",
          clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
          clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
          clonePipelineDTO.getSourceConfig().getProjectIdentifier()));
      throw new InvalidRequestException(
          String.format("Pipeline with id [%s] in org [%s] in project [%s] is not present or deleted",
              clonePipelineDTO.getSourceConfig().getPipelineIdentifier(),
              clonePipelineDTO.getSourceConfig().getOrgIdentifier(),
              clonePipelineDTO.getSourceConfig().getProjectIdentifier()));
    }
    return sourcePipelineEntity.get();
  }

  @Override
  public Optional<PipelineEntity> getAndValidatePipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier, boolean deleted) {
    return getAndValidatePipeline(accountId, orgIdentifier, projectIdentifier, identifier, deleted, false, false);
  }

  @Override
  public PipelineGetResult getAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineId, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, boolean validateAsync) {
    Optional<PipelineEntity> pipelineEntity;
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID = null;
    if (validateAsync) {
      PipelineGetResult pipelineEventPair = getPipelineAndAsyncValidationId(
          accountId, orgIdentifier, projectIdentifier, pipelineId, loadFromFallbackBranch, loadFromCache);
      pipelineEntity = pipelineEventPair.getPipelineEntity();
      validationUUID = pipelineEventPair.getAsyncValidationUUID();
    } else {
      pipelineEntity = getAndValidatePipeline(
          accountId, orgIdentifier, projectIdentifier, pipelineId, false, loadFromFallbackBranch, loadFromCache);
    }
    if (pipelineEntity.isPresent()
        && PipelineGitXHelper.shouldPublishSetupUsages(loadFromCache, pipelineEntity.get().getStoreType())) {
      pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity.get());
    }
    return PipelineGetResult.builder().pipelineEntity(pipelineEntity).asyncValidationUUID(validationUUID).build();
  }

  @Override
  public String validatePipeline(String accountId, String orgIdentifier, String projectIdentifier, String pipelineId,
      boolean loadFromFallbackBranch, boolean loadFromCache, boolean validateAsync, PipelineEntity pipelineEntity) {
    // if validateAsync is true, then this ID wil be of the event started for the async validation process, which can be
    // queried on using another API to get the result of the async validation. If validateAsync is false, then this ID
    // is not needed and will be null
    String validationUUID = null;
    if (validateAsync) {
      validationUUID = getAsyncValidationIdAndValidatePipeline(
          accountId, orgIdentifier, projectIdentifier, loadFromCache, pipelineEntity);
    } else {
      validatePipelineSync(orgIdentifier, projectIdentifier, pipelineId, loadFromCache, pipelineEntity);
    }
    if (PipelineGitXHelper.shouldPublishSetupUsages(loadFromCache, pipelineEntity.getStoreType())) {
      pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity);
    }
    return validationUUID;
  }

  @Override
  public Optional<PipelineEntity> getAndValidatePipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String identifier, boolean deleted, boolean loadFromFallbackBranch,
      boolean loadFromCache) {
    Optional<PipelineEntity> optionalPipelineEntity = getPipeline(
        accountId, orgIdentifier, projectIdentifier, identifier, deleted, false, loadFromFallbackBranch, loadFromCache);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgIdentifier, projectIdentifier, identifier));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    validatePipelineSync(orgIdentifier, projectIdentifier, identifier, loadFromCache, pipelineEntity);
    return optionalPipelineEntity;
  }

  void validatePipelineSync(String orgIdentifier, String projectIdentifier, String identifier, boolean loadFromCache,
      PipelineEntity pipelineEntity) {
    if (pipelineEntity.getStoreType() == null || pipelineEntity.getStoreType() == StoreType.INLINE) {
      // This is added to add validation for stored invalid yaml (duplicate yaml fields)
      validateStoredYaml(pipelineEntity);
    } else {
      if (EmptyPredicate.isEmpty(pipelineEntity.getData())) {
        String errorMessage = PipelineCRUDErrorResponse.errorMessageForEmptyYamlOnGit(
            orgIdentifier, projectIdentifier, identifier, GitAwareContextHelper.getBranchInRequest());
        YamlSchemaErrorWrapperDTO errorWrapperDTO =
            YamlSchemaErrorWrapperDTO.builder()
                .schemaErrors(Collections.singletonList(
                    YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.pipeline").build()))
                .build();
        throw new io.harness.yaml.validator.InvalidYamlException(
            errorMessage, errorWrapperDTO, pipelineEntity.getData());
      }
      pmsPipelineServiceHelper.resolveTemplatesAndValidatePipelineEntity(pipelineEntity, loadFromCache);
    }
  }

  // This function validate the duplicate fields in yaml and throws error if any. This method will be called during get
  // call of inline Pipeline
  public void validateStoredYaml(PipelineEntity pipelineEntity) {
    try {
      YamlUtils.readTree(pipelineEntity.getYaml());
    } catch (Exception ex) {
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder().message(ex.getMessage()).build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  @Override
  public Optional<PipelineEntity> getPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnlyIfApplicable) {
    return getPipeline(
        accountId, orgIdentifier, projectIdentifier, identifier, deleted, getMetadataOnlyIfApplicable, false, false);
  }

  @Override
  public Optional<PipelineEntity> getPipelineByUUID(String uuid) {
    return pmsPipelineRepository.find(uuid);
  }

  @Override
  public Optional<PipelineEntity> getPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache) {
    Optional<PipelineEntity> optionalPipelineEntity;
    long start = System.currentTimeMillis();
    try {
      if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
        optionalPipelineEntity =
            pmsPipelineRepository.findForOldGitSync(accountId, orgIdentifier, projectIdentifier, identifier, !deleted);
      } else {
        // TODO: currently we are setting up the same in PipelineStageFilterCreator. Check if can call this only once
        PipelineGitXHelper.setupGitParentEntityDetails(accountId, orgIdentifier, projectIdentifier, null, null);
        optionalPipelineEntity = pmsPipelineRepository.find(accountId, orgIdentifier, projectIdentifier, identifier,
            !deleted, getMetadataOnly, loadFromFallbackBranch, loadFromCache);
      }
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving pipeline [%s]", identifier), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while retrieving pipeline [%s]", identifier), e);
      throw new InvalidRequestException(
          String.format("Error while retrieving pipeline [%s]: %s", identifier, ExceptionUtils.getMessage(e)));
    } finally {
      log.info("[PMS_PipelineService] get Pipeline took {}ms for projectId {}, orgId {}, accountId {}",
          System.currentTimeMillis() - start, projectIdentifier, orgIdentifier, accountId);
    }
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgIdentifier, projectIdentifier, identifier));
    }
    return optionalPipelineEntity;
  }

  @Override
  public PipelineEntity getPipelineMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly) {
    Optional<PipelineEntity> pipelineEntityOnlyMetadata =
        getPipeline(accountId, orgIdentifier, projectIdentifier, identifier, deleted, getMetadataOnly, false, false);
    if (pipelineEntityOnlyMetadata.isEmpty()) {
      throw new InvalidRequestException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgIdentifier, projectIdentifier, identifier));
    }
    return pipelineEntityOnlyMetadata.get();
  }

  PipelineGetResult getPipelineAndAsyncValidationId(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean loadFromFallbackBranch, boolean loadFromCache) {
    Optional<PipelineEntity> optionalPipelineEntity = getPipeline(
        accountId, orgIdentifier, projectIdentifier, identifier, false, false, loadFromFallbackBranch, loadFromCache);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted.", identifier));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String validationUUID = getAsyncValidationIdAndValidatePipeline(
        accountId, orgIdentifier, projectIdentifier, loadFromCache, pipelineEntity);
    return PipelineGetResult.builder()
        .pipelineEntity(optionalPipelineEntity)
        .asyncValidationUUID(validationUUID)
        .build();
  }
  String getAsyncValidationIdAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      boolean loadFromCache, PipelineEntity pipelineEntity) {
    pipelineValidationService.validateYamlWithUnresolvedTemplates(
        accountId, orgIdentifier, projectIdentifier, pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());

    // if the branch in the request is null, then the branch from where the remote pipeline is taken from is set
    // inside the scm git metadata. Hence, the branch from there is the actual branch we need
    String branchFromScm = GitAwareContextHelper.getBranchInSCMGitMetadata();
    return getValidationUuid(pipelineEntity, loadFromCache, branchFromScm);
  }

  String getValidationUuid(PipelineEntity pipelineEntity, boolean loadFromCache, String branchFromScm) {
    String validationUUID;
    if (!loadFromCache && pipelineEntity.getStoreType() == StoreType.REMOTE) {
      // loadFromCache = false means user is reloading from Git. In this case, the validation data being shown can't be
      // for an older yaml as user expects everything to be refreshed. That's why it makes sense to have a fresh
      // validation process in this case
      PipelineValidationEvent newEvent =
          pipelineAsyncValidationService.startEvent(pipelineEntity, branchFromScm, Action.CRUD, loadFromCache);
      validationUUID = newEvent.getUuid();
    } else {
      String fqn = PipelineAsyncValidationHelper.buildFQN(pipelineEntity, branchFromScm);
      Optional<PipelineValidationEvent> optionalEvent =
          pipelineAsyncValidationService.getLatestEventByFQNAndAction(fqn, Action.CRUD);
      if (optionalEvent.isPresent()) {
        validationUUID = optionalEvent.get().getUuid();
      } else {
        PipelineValidationEvent newEvent =
            pipelineAsyncValidationService.startEvent(pipelineEntity, branchFromScm, Action.CRUD, loadFromCache);
        validationUUID = newEvent.getUuid();
      }
    }
    return validationUUID;
  }

  @Override
  public PipelineCRUDResult validateAndUpdatePipeline(
      PipelineEntity pipelineEntity, ChangeType changeType, boolean throwExceptionIfGovernanceFails) {
    try {
      if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
        log.info("Updating Draft Pipeline with identifier: {}", pipelineEntity.getIdentifier());
        PipelineEntity updatedEntity = updatePipelineWithoutValidation(pipelineEntity, changeType);
        GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
        return PipelineCRUDResult.builder()
            .governanceMetadata(governanceMetadata)
            .pipelineEntity(updatedEntity)
            .build();
      }
      PMSPipelineServiceHelper.validatePresenceOfRequiredFields(pipelineEntity);
      GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
          pipelineEntity, throwExceptionIfGovernanceFails, false);
      if (governanceMetadata.getDeny()) {
        return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).build();
      }
      PipelineEntity updatedEntity = updatePipelineWithoutValidation(pipelineEntity, changeType);
      computeReferencesIfRemotePipeline(updatedEntity);
      try {
        String branchInRequest = GitAwareContextHelper.getBranchInRequest();
        pipelineAsyncValidationService.createRecordForSuccessfulSyncValidation(updatedEntity,
            GitAwareContextHelper.DEFAULT.equals(branchInRequest) ? "" : branchInRequest, governanceMetadata,
            Action.CRUD);
      } catch (Exception e) {
        log.error("Unable to save validation event for Pipeline: " + e.getMessage(), e);
      }
      return PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(updatedEntity).build();
    } catch (NGTemplateException ex) {
      throw new PipelineException(PipelineException.PIPELINE_UPDATE_MESSAGE, ex, ErrorCode.PIPELINE_UPDATE_EXCEPTION);
    }
  }

  private PipelineEntity updatePipelineWithoutValidation(PipelineEntity pipelineEntity, ChangeType changeType) {
    PipelineEntity updatedEntity;
    if (gitSyncSdkService.isGitSyncEnabled(
            pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier())) {
      updatedEntity = updatePipelineForOldGitSync(pipelineEntity, changeType);
    } else {
      updatedEntity = makePipelineUpdateCall(pipelineEntity, null, changeType, false);
    }
    return updatedEntity;
  }

  private PipelineEntity updatePipelineForOldGitSync(PipelineEntity pipelineEntity, ChangeType changeType) {
    if (GitContextHelper.getGitEntityInfo() != null && GitContextHelper.getGitEntityInfo().isNewBranch()) {
      // sending old entity as null here because a new mongo entity will be created. If audit trail needs to be added
      // to git synced projects, a get call needs to be added here to the base branch of this pipeline update
      return makePipelineUpdateCall(pipelineEntity, null, changeType, true);
    }
    Optional<PipelineEntity> optionalOriginalEntity =
        pmsPipelineRepository.findForOldGitSync(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), true);
    if (optionalOriginalEntity.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier()));
    }
    PipelineEntity entityToUpdate = optionalOriginalEntity.get();
    PipelineEntity tempEntity = entityToUpdate.withYaml(pipelineEntity.getYaml())
                                    .withName(pipelineEntity.getName())
                                    .withDescription(pipelineEntity.getDescription())
                                    .withTags(pipelineEntity.getTags())
                                    .withIsEntityInvalid(false)
                                    .withAllowStageExecutions(pipelineEntity.getAllowStageExecutions());

    return makePipelineUpdateCall(tempEntity, entityToUpdate, changeType, true);
  }

  @Override
  public PipelineEntity syncPipelineEntityWithGit(EntityDetailProtoDTO entityDetail) {
    IdentifierRefProtoDTO identifierRef = entityDetail.getIdentifierRef();
    String accountId = StringValueUtils.getStringFromStringValue(identifierRef.getAccountIdentifier());
    String orgId = StringValueUtils.getStringFromStringValue(identifierRef.getOrgIdentifier());
    String projectId = StringValueUtils.getStringFromStringValue(identifierRef.getProjectIdentifier());
    String pipelineId = StringValueUtils.getStringFromStringValue(identifierRef.getIdentifier());

    Optional<PipelineEntity> optionalPipelineEntity;
    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(null, false)) {
      // Get and validate pipeline only for old git exp full sync
      optionalPipelineEntity = getAndValidatePipeline(accountId, orgId, projectId, pipelineId, false);
    }
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(
          PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgId, projectId, pipelineId));
    }
    // Non Git synced Pipelines are being marked as INLINE. Marking storeType as null here so that pipelines in old git
    // sync don't have any value for storeType.
    return makePipelineUpdateCall(
        optionalPipelineEntity.get().withStoreType(null), optionalPipelineEntity.get(), ChangeType.ADD, true);
  }

  private PipelineEntity makePipelineUpdateCall(
      PipelineEntity pipelineEntity, PipelineEntity oldEntity, ChangeType changeType, boolean isOldFlow) {
    try {
      PipelineEntity entityWithUpdatedInfo = pipelineEntity;
      // If PIE_ASYNC_FILTER_CREATION is ON, then we do filter creation async
      if (!pmsFeatureFlagHelper.isEnabled(pipelineEntity.getAccountId(), FeatureName.PIE_ASYNC_FILTER_CREATION)) {
        entityWithUpdatedInfo =
            pmsPipelineServiceHelper.updatePipelineInfo(pipelineEntity, pipelineEntity.getHarnessVersion());
      }

      PipelineEntity updatedResult;
      if (isOldFlow) {
        updatedResult =
            pmsPipelineRepository.updatePipelineYamlForOldGitSync(entityWithUpdatedInfo, oldEntity, changeType);
      } else {
        updatedResult = pmsPipelineRepository.updatePipelineYaml(entityWithUpdatedInfo);
      }

      if (updatedResult == null) {
        throw new InvalidRequestException(format(
            "Pipeline [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
            pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()));
      }

      pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(updatedResult, UPDATING_PIPELINE);
      pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(updatedResult, TEMPLATE_REF_PIPELINE);
      return updatedResult;
    } catch (EventsFrameworkDownException ex) {
      log.error(EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE, ex);
      throw new InvalidRequestException(ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM, ex);
    } catch (IOException ex) {
      log.error(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while updating pipeline " + pipelineEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while updating pipeline [%s]", pipelineEntity.getIdentifier()), e);
      throw new InvalidRequestException(String.format(
          "Error while updating pipeline [%s]: %s", pipelineEntity.getIdentifier(), ExceptionUtils.getMessage(e)));
    }
  }

  @Override
  public PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update updateOperations) {
    return pmsPipelineRepository.updatePipelineMetadata(
        accountId, orgIdentifier, projectIdentifier, criteria, updateOperations);
  }

  @Override
  public void saveExecutionInfo(
      String accountId, String orgId, String projectId, String pipelineId, ExecutionSummaryInfo executionSummaryInfo) {
    Criteria criteria =
        PMSPipelineServiceHelper.getPipelineEqualityCriteria(accountId, orgId, projectId, pipelineId, false, null);

    Update update = new Update();
    update.set(PipelineEntityKeys.executionSummaryInfo, executionSummaryInfo);
    updatePipelineMetadata(accountId, orgId, projectId, criteria, update);
  }

  @Override
  public boolean markEntityInvalid(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String invalidYaml) {
    Optional<PipelineEntity> optionalPipelineEntity =
        getPipeline(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false, false);
    if (optionalPipelineEntity.isEmpty()) {
      log.warn(String.format(
          "Marking pipeline [%s] as invalid failed as it does not exist or has been deleted", identifier));
      return false;
    }
    PipelineEntity existingPipeline = optionalPipelineEntity.get();
    PipelineEntity pipelineEntityUpdated = existingPipeline.withYaml(invalidYaml)
                                               .withObjectIdOfYaml(EntityObjectIdUtils.getObjectIdOfYaml(invalidYaml))
                                               .withIsEntityInvalid(true);
    pmsPipelineRepository.updatePipelineYamlForOldGitSync(pipelineEntityUpdated, existingPipeline, ChangeType.NONE);
    return true;
  }

  private boolean isForceDeleteEnabled(String accountIdentifier) {
    try {
      return isForceDeleteFFEnabledViaSettings(accountIdentifier);
    } catch (Exception e) {
      log.error("Failed to fetch feature flag info for force delete ", e);
      return false;
    }
  }

  @VisibleForTesting
  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }
  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, Long version) {
    if (!isForceDeleteEnabled(accountId)) {
      validateSetupUsage(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return deleteForOldGitSync(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    }
    try {
      pmsPipelineRepository.delete(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      return true;
    } catch (Exception e) {
      log.error(format("Pipeline [%s] under Project[%s], Organization [%s] could not be deleted: %s",
                    pipelineIdentifier, projectIdentifier, orgIdentifier, ExceptionUtils.getMessage(e)),
          e);
      throw new InvalidRequestException(
          format("Pipeline [%s] under Project[%s], Organization [%s] could not be deleted.", pipelineIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  public void validateSetupUsage(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(pipelineIdentifier)
                                      .build();
    Boolean isEntityReferenced;
    try {
      isEntityReferenced = NGRestUtils.getResponse(entitySetupUsageClient.isEntityReferenced(
          accountId, identifierRef.getFullyQualifiedName(), EntityType.PIPELINES));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          pipelineIdentifier, ex);
      throw new UnexpectedException("Error while deleting the Pipeline");
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(
          String.format("Could not delete the pipeline %s as it is referenced by other entities", pipelineIdentifier));
    }
  }

  private boolean deleteForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineRepository.findForOldGitSync(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, true);
    if (optionalPipelineEntity.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }

    PipelineEntity existingEntity = optionalPipelineEntity.get();
    PipelineEntity withDeleted = existingEntity.withDeleted(true);
    try {
      pmsPipelineRepository.deleteForOldGitSync(withDeleted);
      return true;
    } catch (Exception e) {
      log.error(String.format("Error while deleting pipeline [%s]", pipelineIdentifier), e);

      throw new InvalidRequestException(
          format("Pipeline [%s] under Project[%s], Organization [%s] could not be deleted.", pipelineIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public Page<PipelineEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches) {
    checkProjectExists(accountId, orgIdentifier, projectIdentifier);
    if (Boolean.TRUE.equals(getDistinctFromBranches)
        && gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return pmsPipelineRepository.findAll(criteria, pageable, accountId, orgIdentifier, projectIdentifier, true);
    }
    return pmsPipelineRepository.findAll(criteria, pageable, accountId, orgIdentifier, projectIdentifier, false);
  }

  @Override
  public PipelineEntity importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, Boolean isForceImport) {
    String repoUrl = pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, isForceImport);
    String importedPipelineYAML =
        pmsPipelineServiceHelper.importPipelineFromRemote(accountId, orgIdentifier, projectIdentifier, true);
    String pipelineVersion = pipelineVersion(accountId, importedPipelineYAML);
    PMSPipelineServiceHelper.checkAndThrowMismatchInImportedPipelineMetadata(orgIdentifier, projectIdentifier,
        pipelineIdentifier, pipelineImportRequest, importedPipelineYAML, pipelineVersion);
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, pipelineImportRequest.getPipelineName(), importedPipelineYAML, false, pipelineVersion);
    pipelineEntity.setRepoURL(repoUrl);
    pipelineEntity.setStoreType(StoreType.REMOTE);
    try {
      PipelineEntity entityWithUpdatedInfo =
          pmsPipelineServiceHelper.updatePipelineInfo(pipelineEntity, pipelineVersion);
      PipelineEntity savedPipelineEntity =
          pmsPipelineRepository.savePipelineEntityForImportedYAML(entityWithUpdatedInfo);
      pmsPipelineServiceHelper.sendPipelineSaveTelemetryEvent(savedPipelineEntity, CREATING_PIPELINE);
      pmsPipelineServiceHelper.sendTemplatesUsedInPipelinesTelemetryEvent(savedPipelineEntity, TEMPLATE_REF_PIPELINE);
      return savedPipelineEntity;
    } catch (DuplicateKeyException ex) {
      log.error(format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(),
                    pipelineEntity.getOrgIdentifier()),
          ex);
      throw new DuplicateFieldException(format(DUP_KEY_EXP_FORMAT_STRING, pipelineEntity.getIdentifier(),
                                            pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (EventsFrameworkDownException ex) {
      log.error(EVENTS_FRAMEWORK_IS_DOWN_FOR_PIPELINE_SERVICE, ex);
      throw new InvalidRequestException(ERROR_CONNECTING_TO_SYSTEMS_UPSTREAM, ex);
    } catch (IOException ex) {
      log.error(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format(INVALID_YAML_IN_NODE, YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @Override
  public Long countAllPipelines(Criteria criteria) {
    return pmsPipelineRepository.countAllPipelines(criteria);
  }

  @Override
  public StepCategory getSteps(String module, String category, String accountId) {
    Map<String, StepPalleteInfo> serviceInstanceNameToSupportedSteps =
        pmsSdkInstanceService.getModuleNameToStepPalleteInfo();
    StepCategory stepCategory = pmsPipelineServiceStepHelper.calculateStepsForModuleBasedOnCategory(
        category, serviceInstanceNameToSupportedSteps.get(module).getStepTypes(), accountId);
    for (Map.Entry<String, StepPalleteInfo> entry : serviceInstanceNameToSupportedSteps.entrySet()) {
      if (entry.getKey().equals(module) || EmptyPredicate.isEmpty(entry.getValue().getStepTypes())) {
        continue;
      }
      stepCategory.addStepCategory(pmsPipelineServiceStepHelper.calculateStepsForCategory(
          entry.getValue().getModuleName(), entry.getValue().getStepTypes(), accountId));
    }
    return stepCategory;
  }

  @Override
  public StepCategory getStepsV2(String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper) {
    Map<String, StepPalleteInfo> serviceInstanceNameToSupportedSteps =
        pmsSdkInstanceService.getModuleNameToStepPalleteInfo();
    if (stepPalleteFilterWrapper.getStepPalleteModuleInfos().isEmpty()) {
      // Return all the steps.
      return pmsPipelineServiceStepHelper.getAllSteps(accountId, serviceInstanceNameToSupportedSteps);
    }
    StepCategory stepCategory = StepCategory.builder().name(LIBRARY).build();
    for (StepPalleteModuleInfo request : stepPalleteFilterWrapper.getStepPalleteModuleInfos()) {
      String module = request.getModule();
      String category = request.getCategory();

      StepPalleteInfo stepPalleteInfo = serviceInstanceNameToSupportedSteps.get(module);
      if (stepPalleteInfo == null) {
        continue;
      }
      List<StepInfo> stepInfoList = stepPalleteInfo.getStepTypes();
      String displayModuleName = stepPalleteInfo.getModuleName();
      if (EmptyPredicate.isEmpty(stepInfoList)) {
        continue;
      }
      StepCategory moduleCategory;
      if (EmptyPredicate.isNotEmpty(category)) {
        moduleCategory = pmsPipelineServiceStepHelper.calculateStepsForModuleBasedOnCategoryV2(
            displayModuleName, category, stepInfoList, accountId);
      } else {
        moduleCategory =
            pmsPipelineServiceStepHelper.calculateStepsForCategory(displayModuleName, stepInfoList, accountId);
      }
      stepCategory.addStepCategory(moduleCategory);
      if (request.isShouldShowCommonSteps()) {
        pmsPipelineServiceStepHelper.addStepsToStepCategory(
            moduleCategory, commonStepInfo.getCommonSteps(request.getCommonStepCategory()), accountId);
      }
    }

    return stepCategory;
  }

  // Todo: Remove only if there are no references to the pipeline
  @Override
  public boolean deleteAllPipelinesInAProject(String accountId, String orgId, String projectId) {
    boolean isOldGitSyncEnabled = gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId);
    if (isOldGitSyncEnabled) {
      Criteria criteria = PMSPipelineFilterHelper.getCriteriaForAllPipelinesInProject(accountId, orgId, projectId);
      Pageable pageRequest = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));

      Page<PipelineEntity> pipelineEntities =
          pmsPipelineRepository.findAll(criteria, pageRequest, accountId, orgId, projectId, false);
      for (PipelineEntity pipelineEntity : pipelineEntities) {
        pmsPipelineRepository.deleteForOldGitSync(pipelineEntity.withDeleted(true));
      }
      return true;
    }
    return pmsPipelineRepository.deleteAllPipelinesInAProject(accountId, orgId, projectId);
  }

  @Override
  public String fetchExpandedPipelineJSON(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Optional<PipelineEntity> pipelineEntityOptional =
        getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    if (pipelineEntityOptional.isEmpty()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }

    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
      return null;
    }

    String branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata();
    return pipelineGovernanceService.getExpandedPipelineJSONFromYaml(accountId, orgIdentifier, projectIdentifier,
        pipelineEntityOptional.get().getYaml(), branch, pipelineEntityOptional.get());
  }

  @Override
  public PipelineEntity updateGitFilePath(PipelineEntity pipelineEntity, String newFilePath) {
    Criteria criteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(pipelineEntity.getAccountId(),
        pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), false,
        null);

    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(newFilePath);
    Update update = new Update()
                        .set(PipelineEntityKeys.filePath, gitEntityFilePath.getFilePath())
                        .set(PipelineEntityKeys.rootFolder, gitEntityFilePath.getRootFolder());
    return updatePipelineMetadata(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), criteria, update);
  }

  @Override
  public String pipelineVersion(String accountId, String yaml) {
    boolean isYamlSimplificationEnabled = pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CI_YAML_VERSIONING);
    return NGYamlHelper.getVersion(yaml, isYamlSimplificationEnabled);
  }

  @Override
  public PMSPipelineListRepoResponse getListOfRepos(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria =
        PMSPipelineServiceHelper.buildCriteriaForRepoListing(accountIdentifier, orgIdentifier, projectIdentifier);
    List<String> uniqueRepos = pmsPipelineRepository.findAllUniqueRepos(criteria);
    CollectionUtils.filter(uniqueRepos, PredicateUtils.notNullPredicate());
    if (uniqueRepos.size() > MAX_LIST_SIZE) {
      log.error(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
      throw new InternalServerErrorException(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
    }
    return PMSPipelineListRepoResponse.builder().repositories(uniqueRepos).build();
  }

  @Override
  public PipelineCRUDResult moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, MoveConfigOperationDTO moveConfigDTO) {
    PipelineEntity pipeline =
        getPipeline(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false).get();

    PipelineEntity movedPipelineEntity = movePipelineEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, moveConfigDTO, pipeline);

    return PipelineCRUDResult.builder().pipelineEntity(movedPipelineEntity).build();
  }

  @Override
  public String updateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams) {
    validateRepo(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, updateGitDetailsParams);

    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, true);
    Update update = PMSPipelineFilterHelper.getUpdateWithGitMetadata(updateGitDetailsParams);

    PipelineEntity pipelineAfterUpdate = pmsPipelineRepository.updateEntity(criteria, update);
    if (pipelineAfterUpdate == null) {
      throw new EntityNotFoundException(
          format("Pipeline with id [%s] is not present or has been deleted", pipelineIdentifier));
    }

    return pipelineAfterUpdate.getIdentifier();
  }

  @Override
  public List<String> getPermittedPipelineIdentifier(
      String accountId, String orgId, String projectId, List<String> pipelineIdentifierList) {
    AccessCheckResponseDTO accessCheckResponseDTO =
        getAccessCheckResponseDTO(accountId, orgId, projectId, pipelineIdentifierList);
    List<String> permittedPipelineIdentifier = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponseDTO.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedPipelineIdentifier.add(accessControlDTO.getResourceIdentifier());
      }
    }
    return permittedPipelineIdentifier;
  }

  /*
 getAccessCheckResponseDTO return the access response for pipeline view permission on the pipeline identifier list
  */
  private AccessCheckResponseDTO getAccessCheckResponseDTO(
      String accountId, String orgId, String projectId, List<String> entityIdentifierList) {
    List<PermissionCheckDTO> permissionChecks =
        entityIdentifierList.stream()
            .map(identifier
                -> PermissionCheckDTO.builder()
                       .permission(PipelineRbacPermissions.PIPELINE_VIEW)
                       .resourceIdentifier(identifier)
                       .resourceScope(ResourceScope.of(accountId, orgId, projectId))
                       .resourceType("PIPELINE")
                       .build())
            .collect(Collectors.toList());
    return accessControlClient.checkForAccessOrThrow(permissionChecks);
  }

  @Override
  public List<String> listAllIdentifiers(Criteria criteria) {
    return pmsPipelineRepository.findAllPipelineIdentifiers(criteria);
  }

  @Override
  public boolean validateViewPermission(String accountId, String orgId, String projectId) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgId, projectId), Resource.of("PIPELINE", null),
        PipelineRbacPermissions.PIPELINE_VIEW);
  }

  public List<YamlInputDetails> getInputSchemaDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Optional<PipelineEntity> optionalPipelineEntity =
        getPipeline(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
    return pmsYamlSchemaService.getInputSchemaDetails(optionalPipelineEntity.get().getYaml());
  }

  private void validateRepo(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams) {
    if (isEmpty(updateGitDetailsParams.getRepoName())) {
      return;
    }

    String connectorRef = updateGitDetailsParams.getConnectorRef();
    if (isEmpty(connectorRef)) {
      Optional<PipelineEntity> optionalPipelineEntity =
          getPipeline(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
      checkIfPipelineIsPresent(orgIdentifier, projectIdentifier, pipelineIdentifier, optionalPipelineEntity);

      connectorRef = optionalPipelineEntity.get().getConnectorRef();
    }

    gitAwareEntityHelper.validateRepo(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, updateGitDetailsParams.getRepoName());
  }

  private void checkIfPipelineIsPresent(String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      Optional<PipelineEntity> optionalPipelineEntity) {
    if (optionalPipelineEntity.isEmpty()) {
      throw new EntityNotFoundException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          orgIdentifier, projectIdentifier, pipelineIdentifier));
    }
  }

  @VisibleForTesting
  protected PipelineEntity movePipelineEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, MoveConfigOperationDTO moveConfigDTO, PipelineEntity pipeline) {
    Criteria pipelineCriteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, null);

    Criteria metadataCriteria = pmsPipelineServiceHelper.getPipelineMetadataV2Criteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);

    Update pipelineUpdate;
    Update metadataUpdate = new Update();

    if (INLINE_TO_REMOTE.equals(moveConfigDTO.getMoveConfigOperationType())) {
      setupGitContext(moveConfigDTO);

      pipelineUpdate = pmsPipelineServiceHelper.getPipelineUpdateForInlineToRemote(
          accountIdentifier, orgIdentifier, projectIdentifier, moveConfigDTO);
      metadataUpdate = metadataUpdate.set(PipelineMetadataV2Keys.branch, moveConfigDTO.getBranch());

    } else if (REMOTE_TO_INLINE.equals(moveConfigDTO.getMoveConfigOperationType())) {
      pipelineUpdate = pmsPipelineServiceHelper.getPipelineUpdateForRemoteToInline();
      metadataUpdate = metadataUpdate.unset(PipelineMetadataV2Keys.entityGitDetails);
    } else {
      log.error("Invalid move config operation provided: {}", moveConfigDTO.getMoveConfigOperationType().name());
      throw new InvalidRequestException(String.format(
          "Invalid move config operation specified [%s].", moveConfigDTO.getMoveConfigOperationType().name()));
    }
    return pmsPipelineRepository.updatePipelineEntity(pipeline, pipelineUpdate, pipelineCriteria, metadataUpdate,
        metadataCriteria, moveConfigDTO.getMoveConfigOperationType());
  }

  private void setupGitContext(MoveConfigOperationDTO moveConfigDTO) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(moveConfigDTO.getBranch())
                                                 .filePath(moveConfigDTO.getFilePath())
                                                 .commitMsg(moveConfigDTO.getCommitMessage())
                                                 .isNewBranch(moveConfigDTO.isNewBranch())
                                                 .baseBranch(moveConfigDTO.getBaseBranch())
                                                 .connectorRef(moveConfigDTO.getConnectorRef())
                                                 .storeType(StoreType.REMOTE)
                                                 .repoName(moveConfigDTO.getRepoName())
                                                 .build());
  }

  private void checkProjectExists(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      getResponse(projectClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier),
          format("Project with orgIdentifier %s and identifier %s not found", orgIdentifier, projectIdentifier));
    }
  }
  public void checkThatTheModuleExists(String module) {
    if (isNotEmpty(module)
        && isEmpty(ModuleType.getModules()
                       .stream()
                       .filter(moduleType -> moduleType.name().equalsIgnoreCase(module))
                       .collect(Collectors.toList()))) {
      throw new HintException(format(
          "Invalid module type [%s]. Please select the correct module type %s", module, ModuleType.getModules()));
    }
  }

  @VisibleForTesting
  void applyGitXSettingsIfApplicable(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    gitXSettingsHelper.enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        accountIdentifier, orgIdentifier, projIdentifier, EntityType.PIPELINES);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultRepoForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
  }

  private void computeReferencesIfRemotePipeline(PipelineEntity pipelineEntity) {
    if (PipelineGitXHelper.shouldPublishSetupUsages(pipelineEntity.getStoreType())) {
      pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity);
    }
  }
}
