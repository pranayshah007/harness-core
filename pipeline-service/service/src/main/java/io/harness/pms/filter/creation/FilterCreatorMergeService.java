/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.creation;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.ErrorResponseV2;
import io.harness.pms.contracts.plan.FilterCreationBlobRequest;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.FilterCreationResponse;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.exception.PmsExceptionUtils;
import io.harness.pms.filter.creation.FilterCreationResponseWrapper.FilterCreationResponseWrapperBuilder;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.pipeline.references.FilterCreationParams;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.sdk.core.plan.creation.creators.PlanCreatorServiceHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class FilterCreatorMergeService {
  private final PmsSdkHelper pmsSdkHelper;
  private final PipelineSetupUsageHelper pipelineSetupUsageHelper;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  private final GitSyncSdkService gitSyncSdkService;
  private final PrincipalInfoHelper principalInfoHelper;
  private final TriggeredByHelper triggeredByHelper;

  public static final int MAX_DEPTH = 10;
  private final Executor executor = Executors.newFixedThreadPool(5);

  @Inject
  public FilterCreatorMergeService(PmsSdkHelper pmsSdkHelper, PipelineSetupUsageHelper pipelineSetupUsageHelper,
      PmsGitSyncHelper pmsGitSyncHelper, PMSPipelineTemplateHelper pmsPipelineTemplateHelper,
      GitSyncSdkService gitSyncSdkService, PrincipalInfoHelper principalInfoHelper,
      TriggeredByHelper triggeredByHelper) {
    this.pmsSdkHelper = pmsSdkHelper;
    this.pipelineSetupUsageHelper = pipelineSetupUsageHelper;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.pmsPipelineTemplateHelper = pmsPipelineTemplateHelper;
    this.gitSyncSdkService = gitSyncSdkService;
    this.principalInfoHelper = principalInfoHelper;
    this.triggeredByHelper = triggeredByHelper;
  }

  public FilterCreatorMergeServiceResponse getPipelineInfo(FilterCreationParams filterCreationParams)
      throws IOException {
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("[PMS_FilterCreatorMergeService]")) {
      PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
      Map<String, PlanCreatorServiceInfo> services = getServices();
      Dependencies dependencies = getDependencies(pipelineEntity.getYaml());
      Map<String, String> filters = new HashMap<>();
      SetupMetadata.Builder setupMetadataBuilder = getSetupMetadataBuilder(
          pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier());
      ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
      if (gitSyncBranchContext != null) {
        setupMetadataBuilder.setGitSyncBranchContext(gitSyncBranchContext);
      }
      setupMetadataBuilder.setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext());
      if (!gitSyncSdkService.isGitSyncEnabled(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
              pipelineEntity.getProjectIdentifier())) {
        setupMetadataBuilder.setTriggeredInfo(triggeredByHelper.getFromSecurityContext());
      }
      FilterCreationBlobResponse response =
          obtainFiltersRecursively(services, dependencies, filters, setupMetadataBuilder.build());
      validateFilterCreationBlobResponse(response);
      if (GitContextHelper.isFullSyncFlow()) {
        deleteExistingSetupUsages(pipelineEntity);
      }
      if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
        List<EntityDetailProtoDTO> templateReferences =
            pmsPipelineTemplateHelper.getTemplateReferencesForGivenYaml(pipelineEntity.getAccountId(),
                pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getYaml());
        response = response.toBuilder().addAllReferredEntities(templateReferences).build();
      }
      Optional<EntityDetailProtoDTO> gitConnectorReference = getGitConnectorReference(pipelineEntity);
      if (gitConnectorReference.isPresent()) {
        response = response.toBuilder().addAllReferredEntities(Arrays.asList(gitConnectorReference.get())).build();
      }
      pipelineSetupUsageHelper.publishSetupUsageEvent(filterCreationParams, response.getReferredEntitiesList());
      return FilterCreatorMergeServiceResponse.builder()
          .filters(filters)
          .stageCount(response.getStageCount())
          .stageNames(new ArrayList<>(response.getStageNamesList()))
          .build();
    }
  }

  private void deleteExistingSetupUsages(PipelineEntity pipelineEntity) {
    GitEntityInfo oldGitEntityInfo = GitContextHelper.getGitEntityInfo();
    try (GlobalContextManager.GlobalContextGuard ignore = GlobalContextManager.ensureGlobalContextGuard()) {
      GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
      pipelineSetupUsageHelper.deleteExistingSetupUsages(pipelineEntity.getAccountIdentifier(),
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier());
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(oldGitEntityInfo).build());
    }
  }

  public SetupMetadata.Builder getSetupMetadataBuilder(String accountId, String orgId, String projectId) {
    SetupMetadata.Builder setupMetaData = SetupMetadata.newBuilder().setAccountId(accountId);
    if (isNotEmpty(projectId)) {
      setupMetaData.setProjectId(projectId);
    }
    if (isNotEmpty(orgId)) {
      setupMetaData.setOrgId(orgId);
    }
    return setupMetaData;
  }

  public Map<String, PlanCreatorServiceInfo> getServices() {
    return pmsSdkHelper.getServices();
  }

  public Dependencies getDependencies(String yaml) throws IOException {
    String processedYaml = YamlUtils.injectUuid(yaml);
    YamlField topRootFieldInYaml = YamlUtils.getTopRootFieldInYaml(processedYaml);

    return Dependencies.newBuilder()
        .setYaml(processedYaml)
        .putDependencies(topRootFieldInYaml.getNode().getUuid(), topRootFieldInYaml.getNode().getYamlPath())
        .build();
  }

  @VisibleForTesting
  public void validateFilterCreationBlobResponse(FilterCreationBlobResponse response) throws IOException {
    if (isNotEmpty(response.getDeps().getDependenciesMap())) {
      throw new InvalidRequestException(PmsExceptionUtils.getUnresolvedDependencyPathsErrorMessage(response.getDeps()));
    }
  }

  @VisibleForTesting
  public FilterCreationBlobResponse obtainFiltersRecursively(Map<String, PlanCreatorServiceInfo> services,
      Dependencies initialDependencies, Map<String, String> filters, SetupMetadata setupMetadata) throws IOException {
    try (AutoLogContext autoLogContext = PlanCreatorServiceHelper.autoLogContextFromSetupMetadata(setupMetadata)) {
      Dependencies initialDependenciesWithoutTemplates =
          FilterCreationBlobResponseUtils.removeTemplateDependencies(initialDependencies);
      FilterCreationBlobResponse.Builder finalResponseBuilder =
          FilterCreationBlobResponse.newBuilder().setDeps(initialDependenciesWithoutTemplates);

      if (isEmpty(services) || isEmpty(initialDependenciesWithoutTemplates.getDependenciesMap())) {
        return finalResponseBuilder.build();
      }

      for (int i = 0; i < MAX_DEPTH && EmptyPredicate.isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap());
           i++) {
        FilterCreationBlobResponse currIterResponse =
            obtainFiltersPerIteration(services, finalResponseBuilder, filters, setupMetadata);

        FilterCreationBlobResponseUtils.mergeResolvedDependencies(finalResponseBuilder, currIterResponse);
        if (isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap())) {
          throw new InvalidRequestException(
              PmsExceptionUtils.getUnresolvedDependencyPathsErrorMessage(finalResponseBuilder.getDeps()));
        }
        FilterCreationBlobResponseUtils.mergeDependencies(finalResponseBuilder, currIterResponse);
        FilterCreationBlobResponseUtils.updateStageCount(finalResponseBuilder, currIterResponse);
        FilterCreationBlobResponseUtils.mergeReferredEntities(finalResponseBuilder, currIterResponse);
        FilterCreationBlobResponseUtils.mergeStageNames(finalResponseBuilder, currIterResponse);
      }

      return finalResponseBuilder.build();
    }
  }

  @VisibleForTesting
  public FilterCreationBlobResponse obtainFiltersPerIteration(Map<String, PlanCreatorServiceInfo> services,
      FilterCreationBlobResponse.Builder responseBuilder, Map<String, String> filters, SetupMetadata setupMetadata) {
    CompletableFutures<FilterCreationResponseWrapper> completableFutures = new CompletableFutures<>(executor);
    for (Map.Entry<String, PlanCreatorServiceInfo> serviceEntry : services.entrySet()) {
      if (!PmsSdkHelper.containsSupportedDependencyByYamlPath(serviceEntry.getValue(), responseBuilder.getDeps())) {
        continue;
      }

      completableFutures.supplyAsync(() -> {
        FilterCreationResponseWrapperBuilder builder =
            FilterCreationResponseWrapper.builder().serviceName(serviceEntry.getKey());
        try {
          FilterCreationResponse filterCreationResponse =
              serviceEntry.getValue().getPlanCreationClient().createFilter(FilterCreationBlobRequest.newBuilder()
                                                                               .setDeps(responseBuilder.getDeps())
                                                                               .setSetupMetadata(setupMetadata)
                                                                               .build());
          if (filterCreationResponse.getResponseCase() == FilterCreationResponse.ResponseCase.ERRORRESPONSE) {
            builder.errorResponse(filterCreationResponse.getErrorResponse());
          } else if (filterCreationResponse.getResponseCase() == FilterCreationResponse.ResponseCase.ERRORRESPONSEV2) {
            builder.errorResponseV2(filterCreationResponse.getErrorResponseV2());
          } else {
            builder.response(filterCreationResponse.getBlobResponse());
          }
        } catch (StatusRuntimeException ex) {
          log.error(
              String.format("Error connecting with service: [%s]. Is this service Running?", serviceEntry.getKey()),
              ex);
          builder.errorResponse(
              ErrorResponse.newBuilder()
                  .addMessages(String.format("Error connecting with service: [%s]", serviceEntry.getKey()))
                  .build());
        }
        return builder.build();
      });
    }

    List<ErrorResponse> errorResponses;
    List<ErrorResponseV2> errorResponsesV2;
    FilterCreationBlobResponse.Builder currentIteration = FilterCreationBlobResponse.newBuilder();
    try {
      List<FilterCreationResponseWrapper> filterCreationResponseWrappers =
          completableFutures.allOf().get(5, TimeUnit.MINUTES);
      errorResponses = filterCreationResponseWrappers.stream()
                           .filter(resp -> resp != null && resp.getErrorResponse() != null)
                           .map(FilterCreationResponseWrapper::getErrorResponse)
                           .collect(Collectors.toList());
      errorResponsesV2 = filterCreationResponseWrappers.stream()
                             .filter(resp -> resp != null && resp.getErrorResponseV2() != null)
                             .map(FilterCreationResponseWrapper::getErrorResponseV2)
                             .collect(Collectors.toList());
      if (EmptyPredicate.isEmpty(errorResponses) && EmptyPredicate.isEmpty(errorResponsesV2)) {
        filterCreationResponseWrappers.forEach(
            response -> FilterCreationBlobResponseUtils.mergeResponses(currentIteration, response, filters));
      }
    } catch (Exception ex) {
      throw new UnexpectedException("Error fetching filter creation response from service", ex);
    }

    PmsExceptionUtils.checkAndThrowFilterCreatorException(errorResponses, errorResponsesV2);
    return currentIteration.build();
  }

  @VisibleForTesting
  public Optional<EntityDetailProtoDTO> getGitConnectorReference(PipelineEntity pipelineEntity) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (isGitSimplificationEnabled(pipelineEntity, gitEntityInfo)) {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(gitEntityInfo.getConnectorRef(), pipelineEntity.getAccountIdentifier(),
              pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier());

      IdentifierRefProtoDTO connectorReference =
          IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(identifierRef.getAccountIdentifier(),
              identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
      EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                  .setIdentifierRef(connectorReference)
                                                  .setType(EntityTypeProtoEnum.CONNECTORS)
                                                  .build();
      return Optional.of(connectorDetails);
    }
    return Optional.empty();
  }

  private boolean isGitSimplificationEnabled(PipelineEntity pipelineEntity, GitEntityInfo gitEntityInfo) {
    return gitEntityInfo != null && StoreType.REMOTE.equals(gitEntityInfo.getStoreType())
        && gitSyncSdkService.isGitSimplificationEnabled(pipelineEntity.getAccountIdentifier(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier());
  }
}
