/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.jira;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGTaskType;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.jira.JiraIssueOutcome;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
public class JiraStepHelperServiceImpl implements JiraStepHelperService {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;
  private final KryoSerializer kryoSerializer;
  private static final String NULL_VALUE = "null";

  @Inject
  public JiraStepHelperServiceImpl(ConnectorResourceClient connectorResourceClient,
      @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService,
      @Named("referenceFalseKryoSerializer") KryoSerializer kryoSerializer) {
    this.connectorResourceClient = connectorResourceClient;
    this.secretManagerClientService = secretManagerClientService;
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public TaskRequest prepareTaskRequest(JiraTaskNGParametersBuilder paramsBuilder, Ambiance ambiance,
      String connectorRef, String timeStr, String taskName) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    if (NULL_VALUE.equals(identifierRef.getIdentifier())) {
      throw new InvalidRequestException(
          String.format(
              "Error retrieving connector information : [%s]. Please check if the connector in the step is valid",
              connectorRef),
          WingsException.USER);
    }
    Optional<ConnectorDTO> connectorDTOOptional = NGRestUtils.getResponse(
        connectorResourceClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
    if (connectorDTOOptional.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier: [%s]", connectorRef), WingsException.USER);
    }

    ConnectorConfigDTO configDTO = connectorDTOOptional.get().getConnectorInfo().getConnectorConfig();
    if (!(configDTO instanceof JiraConnectorDTO)) {
      throw new InvalidRequestException(
          String.format("Connector [%s] is not a jira connector", connectorRef), WingsException.USER);
    }

    JiraConnectorDTO connectorDTO = (JiraConnectorDTO) configDTO;
    paramsBuilder.jiraConnectorDTO(connectorDTO);
    paramsBuilder.encryptionDetails(getEncryptionDetails(connectorDTO, ngAccess));
    JiraTaskNGParameters params = paramsBuilder.build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .timeout(NGTimeConversionHelper.convertTimeStringToMilliseconds(timeStr))
                            .taskType(NGTaskType.JIRA_TASK_NG.name())
                            .parameters(new Object[] {params})
                            .build();
    return TaskRequestsUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.emptyList(), false, taskName,
        params.getDelegateSelectors()
            .stream()
            .map(s -> TaskSelector.newBuilder().setSelector(s).build())
            .collect(Collectors.toList()),
        Scope.PROJECT, EnvironmentType.ALL, false, Collections.emptyList(), false, null);
  }

  @Override
  public StepResponse prepareStepResponse(ThrowingSupplier<JiraTaskNGResponse> responseSupplier) throws Exception {
    JiraTaskNGResponse taskResponse = responseSupplier.get();

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name("issue")
                         .outcome(new JiraIssueOutcome(taskResponse.getIssue()))
                         .build())
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(JiraConnectorDTO jiraNowConnectorDTO, NGAccess ngAccess) {
    if (!isNull(jiraNowConnectorDTO.getAuth()) && !isNull(jiraNowConnectorDTO.getAuth().getCredentials())) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, jiraNowConnectorDTO.getAuth().getCredentials());
    }
    return secretManagerClientService.getEncryptionDetails(ngAccess, jiraNowConnectorDTO);
  }
}
