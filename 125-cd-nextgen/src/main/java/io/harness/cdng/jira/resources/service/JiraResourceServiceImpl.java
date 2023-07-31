/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jira.resources.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraSearchUserData;
import io.harness.delegate.task.jira.JiraSearchUserParams;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.jira.JiraActionNG;
import io.harness.jira.JiraFieldNG;
import io.harness.jira.JiraFieldTypeNG;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class JiraResourceServiceImpl implements JiraResourceService {
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final CDFeatureFlagHelper cdFeatureFlagHelper;

  @Inject
  public JiraResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper,
      CDFeatureFlagHelper cdFeatureFlagHelper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.cdFeatureFlagHelper = cdFeatureFlagHelper;
  }

  @Override
  public boolean validateCredentials(IdentifierRef jiraConnectionRef, String orgId, String projectId) {
    JiraTaskNGParametersBuilder paramsBuilder =
        JiraTaskNGParameters.builder().action(JiraActionNG.VALIDATE_CREDENTIALS);
    obtainJiraTaskNGResponse(jiraConnectionRef, orgId, projectId, paramsBuilder);
    return true;
  }

  @Override
  public List<JiraProjectBasicNG> getProjects(IdentifierRef jiraConnectorRef, String orgId, String projectId) {
    JiraTaskNGParametersBuilder paramsBuilder = JiraTaskNGParameters.builder().action(JiraActionNG.GET_PROJECTS);
    JiraTaskNGResponse jiraTaskResponse = obtainJiraTaskNGResponse(jiraConnectorRef, orgId, projectId, paramsBuilder);
    return jiraTaskResponse.getProjects();
  }

  @Override
  public List<JiraStatusNG> getStatuses(
      IdentifierRef jiraConnectorRef, String orgId, String projectId, String projectKey, String issueType) {
    JiraTaskNGParametersBuilder paramsBuilder =
        JiraTaskNGParameters.builder().action(JiraActionNG.GET_STATUSES).projectKey(projectKey).issueType(issueType);
    JiraTaskNGResponse jiraTaskResponse = obtainJiraTaskNGResponse(jiraConnectorRef, orgId, projectId, paramsBuilder);
    return jiraTaskResponse.getStatuses();
  }

  @Override
  public JiraIssueCreateMetadataNG getIssueCreateMetadata(IdentifierRef jiraConnectorRef, String orgId,
      String projectId, String projectKey, String issueType, String expand, boolean fetchStatus,
      boolean ignoreComment) {
    boolean useNewMetaData =
        cdFeatureFlagHelper.isEnabled(jiraConnectorRef.getAccountIdentifier(), FeatureName.SPG_USE_NEW_METADATA);
    JiraTaskNGParametersBuilder paramsBuilder = JiraTaskNGParameters.builder()
                                                    .action(JiraActionNG.GET_ISSUE_CREATE_METADATA)
                                                    .projectKey(projectKey)
                                                    .issueType(issueType)
                                                    .expand(expand)
                                                    .newMetadata(useNewMetaData)
                                                    .fetchStatus(fetchStatus)
                                                    .ignoreComment(ignoreComment);
    JiraTaskNGResponse jiraTaskResponse = obtainJiraTaskNGResponse(jiraConnectorRef, orgId, projectId, paramsBuilder);

    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = jiraTaskResponse.getIssueCreateMetadata();
    if (!cdFeatureFlagHelper.isEnabled(
            jiraConnectorRef.getAccountIdentifier(), FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA)) {
      Set<JiraFieldNG> jiraUserFields = new HashSet<>();
      jiraIssueCreateMetadataNG.getProjects().values().forEach(
          proj -> proj.getIssueTypes().values().forEach(issueType1 -> issueType1.getFields().values().forEach(field -> {
            if (field.getSchema().getType() == JiraFieldTypeNG.USER) {
              jiraUserFields.add(field);
            }
          })));
      jiraUserFields.forEach(field -> jiraIssueCreateMetadataNG.removeField(field.getName()));
    }

    return jiraIssueCreateMetadataNG;
  }

  @Override
  public JiraIssueUpdateMetadataNG getIssueUpdateMetadata(
      IdentifierRef jiraConnectorRef, String orgId, String projectId, String issueKey) {
    JiraTaskNGParametersBuilder paramsBuilder =
        JiraTaskNGParameters.builder().action(JiraActionNG.GET_ISSUE_UPDATE_METADATA).issueKey(issueKey);
    JiraTaskNGResponse jiraTaskResponse = obtainJiraTaskNGResponse(jiraConnectorRef, orgId, projectId, paramsBuilder);
    return jiraTaskResponse.getIssueUpdateMetadata();
  }

  @Override
  public JiraSearchUserData searchUser(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorId, long defaultSyncCallTimeout, String userQuery, String offset) {
    if (!cdFeatureFlagHelper.isEnabled(accountId, FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA)) {
      return null;
    }
    JiraSearchUserParams jiraSearchUserParams =
        JiraSearchUserParams.builder().accountId(accountId).userQuery(userQuery).startAt(offset).build();
    JiraTaskNGParametersBuilder paramsBuilder =
        JiraTaskNGParameters.builder().jiraSearchUserParams(jiraSearchUserParams).action(JiraActionNG.SEARCH_USER);
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(connectorId, accountId, orgIdentifier, projectIdentifier);
    JiraTaskNGResponse jiraTaskNGResponse =
        obtainJiraTaskNGResponse(connectorRef, orgIdentifier, projectIdentifier, paramsBuilder);
    return jiraTaskNGResponse.getJiraSearchUserData();
  }

  @VisibleForTesting
  JiraTaskNGResponse obtainJiraTaskNGResponse(
      IdentifierRef jiraConnectionRef, String orgId, String projectId, JiraTaskNGParametersBuilder paramsBuilder) {
    JiraConnectorDTO connector = getConnector(jiraConnectionRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(jiraConnectionRef, orgId, projectId);
    JiraTaskNGParameters taskParameters = paramsBuilder.encryptionDetails(getEncryptionDetails(connector, baseNGAccess))
                                              .jiraConnectorDTO(connector)
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = createDelegateTaskRequest(baseNGAccess, taskParameters);
    DelegateResponseData responseData;
    try {
      responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.warn("Exception while executing jira task", ex);
      throw buildDelegateNotAvailableHintException("Delegates are not available for performing jira operation.");
    }
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new HarnessJiraException(errorNotifyResponseData.getErrorMessage(), WingsException.USER);
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new HarnessJiraException(
            "Unexpected error during authentication to JIRA server " + remoteMethodReturnValueData.getReturnValue(),
            WingsException.USER);
      }
    }

    return (JiraTaskNGResponse) responseData;
  }

  private JiraConnectorDTO getConnector(IdentifierRef jiraConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(jiraConnectorRef.getAccountIdentifier(),
        jiraConnectorRef.getOrgIdentifier(), jiraConnectorRef.getProjectIdentifier(), jiraConnectorRef.getIdentifier());
    if (!connectorDTO.isPresent() || !isJiraConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Jira connector not found for identifier : [%s] with scope: [%s]",
                                            jiraConnectorRef.getIdentifier(), jiraConnectorRef.getScope()),
          WingsException.USER);
    }

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (JiraConnectorDTO) connectors.getConnectorConfig();
  }

  private BaseNGAccess getBaseNGAccess(IdentifierRef ref, String orgId, String projectId) {
    return BaseNGAccess.builder()
        .accountIdentifier(ref.getAccountIdentifier())
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .build();
  }

  private boolean isJiraConnector(ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.JIRA == connectorResponseDTO.getConnector().getConnectorType();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(JiraConnectorDTO jiraConnectorDTO, NGAccess ngAccess) {
    if (!isNull(jiraConnectorDTO.getAuth()) && !isNull(jiraConnectorDTO.getAuth().getCredentials())) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, jiraConnectorDTO.getAuth().getCredentials());
    }
    return secretManagerClientService.getEncryptionDetails(ngAccess, jiraConnectorDTO);
  }

  private DelegateTaskRequest createDelegateTaskRequest(
      BaseNGAccess baseNGAccess, JiraTaskNGParameters taskNGParameters) {
    final Map<String, String> ngTaskSetupAbstractionsWithOwner = getNGTaskSetupAbstractionsWithOwner(
        baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier());

    return DelegateTaskRequest.builder()
        .accountId(baseNGAccess.getAccountIdentifier())
        .taskType(NGTaskType.JIRA_TASK_NG.name())
        .taskParameters(taskNGParameters)
        .executionTimeout(TIMEOUT)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .build();
  }

  private HintException buildDelegateNotAvailableHintException(String delegateDownErrorMessage) {
    return new HintException(
        String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
        new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
  }
}
