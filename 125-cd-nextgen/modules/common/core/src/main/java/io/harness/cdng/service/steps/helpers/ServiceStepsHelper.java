/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceStepDetails;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.constants.ServiceConfigStepConstants;
import io.harness.cdng.service.steps.constants.ServiceSectionStepConstants;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceStepsHelper {
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private OutcomeService outcomeService;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;

  public void checkForVariablesAccessOrThrow(Ambiance ambiance, NGServiceConfig serviceConfig, String serviceRef) {
    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }

    io.harness.accesscontrol.principals.PrincipalType principalType =
        PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
            executionPrincipalInfo.getPrincipalType());

    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(serviceRef, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    accessControlClient.checkForAccessOrThrow(io.harness.accesscontrol.acl.api.Principal.of(principalType, principal),
        io.harness.accesscontrol.acl.api.ResourceScope.of(serviceIdentifierRef.getAccountIdentifier(),
            serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier()),
        io.harness.accesscontrol.acl.api.Resource.of(NGResourceType.SERVICE, serviceIdentifierRef.getIdentifier()),
        CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION,
        String.format("Missing Access Permission for Service: [%s]", serviceRef));

    List<NGVariable> serviceVariables =
        serviceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables();

    checkForAccessOrThrow(ambiance, serviceVariables);
  }

  public void checkForAccessOrThrow(Ambiance ambiance, List<NGVariable> serviceVariables) {
    if (EmptyPredicate.isEmpty(serviceVariables)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (NGVariable ngVariable : serviceVariables) {
      Set<EntityDetailProtoDTO> entityDetailsProto =
          ngVariable == null ? Set.of() : entityReferenceExtractorUtils.extractReferredEntities(ambiance, ngVariable);
      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));
      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  public void checkForVariablesAccessOrThrow(
      Ambiance ambiance, ServiceDefinition serviceDefinition, String identifier) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }

    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    io.harness.accesscontrol.principals.PrincipalType principalType =
        PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
            executionPrincipalInfo.getPrincipalType());
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceDefinition);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
    accessControlClient.checkForAccessOrThrow(io.harness.accesscontrol.acl.api.Principal.of(principalType, principal),
        io.harness.accesscontrol.acl.api.ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        io.harness.accesscontrol.acl.api.Resource.of(NGResourceType.SERVICE, identifier),
        CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION, "Validation for Service Step failed");
  }

  public NGLogCallback getServiceLogCallback(Ambiance ambiance) {
    return getServiceLogCallback(ambiance, false);
  }

  public NGLogCallback getServiceLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, prepareServiceAmbiance(ambiance), null, shouldOpenStream);
  }

  private Ambiance prepareServiceAmbiance(Ambiance ambiance) {
    List<Level> levels = ambiance.getLevelsList();
    for (int i = levels.size() - 1; i >= 0; i--) {
      Level level = levels.get(i);
      if (ServiceConfigStepConstants.STEP_TYPE.equals(level.getStepType())
          || ServiceSectionStepConstants.STEP_TYPE.equals(level.getStepType())
          || ServiceStepV3Constants.STEP_TYPE.equals(level.getStepType())) {
        return AmbianceUtils.clone(ambiance, i + 1);
      }
    }
    throw new UnsupportedOperationException("Not inside service step or one of it's children");
  }

  public List<Outcome> getChildrenOutcomes(Map<String, ResponseData> responseDataMap) {
    List<StepOutcomeRef> outcomeRefs = new ArrayList<>();
    for (ResponseData responseData : responseDataMap.values()) {
      if (!(responseData instanceof StepResponseNotifyData)) {
        continue;
      }

      StepResponseNotifyData stepResponseNotifyData = (StepResponseNotifyData) responseData;
      if (EmptyPredicate.isNotEmpty(stepResponseNotifyData.getStepOutcomeRefs())) {
        outcomeRefs.addAll(stepResponseNotifyData.getStepOutcomeRefs());
      }
    }

    if (isEmpty(outcomeRefs)) {
      return Collections.emptyList();
    }

    Set<String> runtimeIds = new HashSet<>();
    outcomeRefs.forEach(or -> runtimeIds.add(or.getInstanceId()));
    return outcomeService.fetchOutcomes(new ArrayList<>(runtimeIds));
  }

  public void saveServiceExecutionDataToStageInfo(Ambiance ambiance, StepResponse stepResponse) {
    stageExecutionInfoService.updateStageExecutionInfo(ambiance,
        StageExecutionInfoUpdateDTO.builder().serviceInfo(createServiceInfoFromResponse(stepResponse)).build());
  }

  private ServiceExecutionSummaryDetails createServiceInfoFromResponse(StepResponse stepResponse) {
    if (stepResponse.getStepOutcomes() != null) {
      for (StepResponse.StepOutcome stepOutcome : stepResponse.getStepOutcomes()) {
        if (stepOutcome.getOutcome() instanceof ServiceStepOutcome) {
          ServiceStepOutcome serviceStepOutcome = (ServiceStepOutcome) stepOutcome.getOutcome();
          return ServiceExecutionSummaryDetails.builder()
              .identifier(serviceStepOutcome.getIdentifier())
              .displayName(serviceStepOutcome.getName())
              .deploymentType(serviceStepOutcome.getServiceDefinitionType())
              .gitOpsEnabled(serviceStepOutcome.isGitOpsEnabled())
              .build();
        }
      }
    }
    return ServiceExecutionSummaryDetails.builder().build();
  }

  public void publishTaskIdsStepDetailsForServiceStep(Ambiance ambiance, Set<String> taskIds, String name) {
    if (isNotEmpty(taskIds)) {
      sdkGraphVisualizationDataService.publishStepDetailInformation(
          prepareServiceAmbiance(ambiance), ServiceStepDetails.builder().taskIds(taskIds).build(), name);
    }
  }
}
