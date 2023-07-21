/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.ecr.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrListImagesDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrResponseDTO;
import io.harness.cdng.artifact.resources.ecr.mappers.EcrResourceMapper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.common.NGTaskType;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.MutablePair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@OwnedBy(PIPELINE)
public class EcrResourceServiceImpl implements EcrResourceService {
  private final AwsResourceServiceHelper serviceHelper;

  @Inject
  public EcrResourceServiceImpl(AwsResourceServiceHelper serviceHelper) {
    this.serviceHelper = serviceHelper;
  }

  @Override
  public EcrResponseDTO getBuildDetails(IdentifierRef ecrConnectorRef, String registryId, String imagePath,
      String region, String orgIdentifier, String projectIdentifier) {
    AwsConnectorDTO connector = serviceHelper.getAwsConnector(ecrConnectorRef);
    BaseNGAccess baseNGAccess =
        serviceHelper.getBaseNGAccess(ecrConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(connector, baseNGAccess);
    EcrArtifactDelegateRequest ecrRequest =
        ArtifactDelegateRequestUtils.getEcrDelegateRequest(registryId, imagePath, null, null, null, region,
            ecrConnectorRef.buildScopedIdentifier(), connector, encryptionDetails, ArtifactSourceType.ECR);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
        ecrRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess, "Ecr Get Builds task failure due to error");
    return getEcrResponseDTO(artifactTaskExecutionResponse);
  }

  @Override
  public EcrBuildDetailsDTO getSuccessfulBuild(IdentifierRef ecrConnectorRef, String registryId, String imagePath,
      EcrRequestDTO ecrRequestDTO, String orgIdentifier, String projectIdentifier) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.IMAGE_PATH, imagePath),
        MutablePair.of(NGArtifactConstants.REGION, ecrRequestDTO.getRegion()));
    ArtifactUtils.validateIfAnyValueAssigned(MutablePair.of(NGArtifactConstants.TAG, ecrRequestDTO.getTag()),
        MutablePair.of(NGArtifactConstants.TAG_REGEX, ecrRequestDTO.getTagRegex()));
    AwsConnectorDTO connector = serviceHelper.getAwsConnector(ecrConnectorRef);
    BaseNGAccess baseNGAccess =
        serviceHelper.getBaseNGAccess(ecrConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(connector, baseNGAccess);
    EcrArtifactDelegateRequest ecrRequest = ArtifactDelegateRequestUtils.getEcrDelegateRequest(registryId, imagePath,
        ecrRequestDTO.getTag(), ecrRequestDTO.getTagRegex(), null, ecrRequestDTO.getRegion(),
        ecrConnectorRef.buildScopedIdentifier(), connector, encryptionDetails, ArtifactSourceType.ECR);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(ecrRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Ecr Get last successful build task failure due to error");
    EcrResponseDTO ecrResponseDTO = getEcrResponseDTO(artifactTaskExecutionResponse);
    if (ecrResponseDTO.getBuildDetailsList().size() != 1) {
      throw new ArtifactServerException("Ecr get last successful build task failure.");
    }
    return ecrResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(IdentifierRef ecrConnectorRef, String registryId, String imagePath,
      String orgIdentifier, String projectIdentifier, String region) {
    AwsConnectorDTO connector = serviceHelper.getAwsConnector(ecrConnectorRef);
    BaseNGAccess baseNGAccess =
        serviceHelper.getBaseNGAccess(ecrConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(connector, baseNGAccess);
    EcrArtifactDelegateRequest ecrRequest =
        ArtifactDelegateRequestUtils.getEcrDelegateRequest(registryId, imagePath, null, null, null, region,
            ecrConnectorRef.buildScopedIdentifier(), connector, encryptionDetails, ArtifactSourceType.ECR);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(ecrRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Ecr validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  @Override
  public boolean validateArtifactSource(String registryId, String imagePath, IdentifierRef ecrConnectorRef,
      String region, String orgIdentifier, String projectIdentifier) {
    AwsConnectorDTO connector = serviceHelper.getAwsConnector(ecrConnectorRef);
    BaseNGAccess baseNGAccess =
        serviceHelper.getBaseNGAccess(ecrConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(connector, baseNGAccess);
    EcrArtifactDelegateRequest ecrRequest =
        ArtifactDelegateRequestUtils.getEcrDelegateRequest(registryId, imagePath, null, null, null, region,
            ecrConnectorRef.buildScopedIdentifier(), connector, encryptionDetails, ArtifactSourceType.ECR);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(ecrRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE, baseNGAccess,
            "Ecr validate artifact source task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  @Override
  public EcrListImagesDTO getImages(
      IdentifierRef ecrConnectorRef, String registryId, String region, String orgIdentifier, String projectIdentifier) {
    AwsConnectorDTO connector = serviceHelper.getAwsConnector(ecrConnectorRef);
    BaseNGAccess baseNGAccess =
        serviceHelper.getBaseNGAccess(ecrConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(connector, baseNGAccess);
    EcrArtifactDelegateRequest ecrRequest =
        ArtifactDelegateRequestUtils.getEcrDelegateRequest(registryId, null, null, null, null, region,
            ecrConnectorRef.buildScopedIdentifier(), connector, encryptionDetails, ArtifactSourceType.ECR);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
        ecrRequest, ArtifactTaskType.GET_IMAGES, baseNGAccess, "Ecr Get Images task failure due to error");
    return EcrListImagesDTO.builder().images(artifactTaskExecutionResponse.getArtifactImages()).build();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(
      EcrArtifactDelegateRequest ecrRequest, ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(taskType)
                                                        .attributes(ecrRequest)
                                                        .build();

    DelegateResponseData responseData = serviceHelper.getResponseData(
        ngAccess, ecrRequest, artifactTaskParameters, NGTaskType.ECR_ARTIFACT_TASK_NG.name());
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private EcrResponseDTO getEcrResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<EcrArtifactDelegateResponse> ecrArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (EcrArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return EcrResourceMapper.toEcrResponse(ecrArtifactDelegateResponses);
  }
}
