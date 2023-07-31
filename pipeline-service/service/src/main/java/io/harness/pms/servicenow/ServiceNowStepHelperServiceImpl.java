/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.servicenow;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

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
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
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
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.servicenow.ServiceNowTicketOutcome;
import io.harness.steps.servicenow.ServiceNowTicketOutcome.ServiceNowTicketOutcomeBuilder;
import io.harness.steps.servicenow.importset.ServiceNowImportSetOutcome;
import io.harness.steps.servicenow.importset.ServiceNowImportSetOutcome.ServiceNowImportSetOutcomeBuilder;
import io.harness.steps.servicenow.importset.ServiceNowImportSetTransformMapOutcome;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
public class ServiceNowStepHelperServiceImpl implements ServiceNowStepHelperService {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;
  private final KryoSerializer kryoSerializer;
  private static final String NULL_VALUE = "null";

  @Inject
  public ServiceNowStepHelperServiceImpl(ConnectorResourceClient connectorResourceClient,
      @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService,
      @Named("referenceFalseKryoSerializer") KryoSerializer kryoSerializer) {
    this.connectorResourceClient = connectorResourceClient;
    this.secretManagerClientService = secretManagerClientService;
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public TaskRequest prepareTaskRequest(ServiceNowTaskNGParametersBuilder paramsBuilder, Ambiance ambiance,
      String connectorRef, String timeStr, String taskName, List<TaskSelector> delegateSelectors) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    if (NULL_VALUE.equals(identifierRef.getIdentifier())) {
      throw new InvalidRequestException(
          String.format(
              "Invalid identifier for Connector: [%s]. Please check the expression/value for the field", connectorRef),
          WingsException.USER);
    }
    Optional<ConnectorDTO> connectorDTOOptional = NGRestUtils.getResponse(
        connectorResourceClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
    if (!connectorDTOOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier: [%s]", connectorRef), WingsException.USER);
    }

    ConnectorConfigDTO configDTO = connectorDTOOptional.get().getConnectorInfo().getConnectorConfig();
    if (!(configDTO instanceof ServiceNowConnectorDTO)) {
      throw new InvalidRequestException(
          String.format("Connector [%s] is not a ServiceNow connector", connectorRef), WingsException.USER);
    }

    ServiceNowConnectorDTO connectorDTO = (ServiceNowConnectorDTO) configDTO;
    paramsBuilder.serviceNowConnectorDTO(connectorDTO);
    paramsBuilder.encryptionDetails(getEncryptionDetails(connectorDTO, ngAccess));
    ServiceNowTaskNGParameters params = paramsBuilder.build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .timeout(NGTimeConversionHelper.convertTimeStringToMilliseconds(timeStr))
                            .taskType(NGTaskType.SERVICENOW_TASK_NG.name())
                            .parameters(new Object[] {params})
                            .build();
    return TaskRequestsUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT), true, taskName, delegateSelectors, Scope.PROJECT,
        EnvironmentType.ALL, false, Collections.emptyList(), false, null);
  }

  @Override
  public StepResponse prepareStepResponse(ThrowingSupplier<ServiceNowTaskNGResponse> responseSupplier)
      throws Exception {
    ServiceNowTaskNGResponse taskResponse = responseSupplier.get();
    ServiceNowTicketOutcomeBuilder serviceNowTicketOutcomeBuilder =
        ServiceNowTicketOutcome.builder()
            .ticketNumber(taskResponse.getTicket().getNumber())
            .ticketUrl(taskResponse.getTicket().getUrl());
    if (taskResponse.getTicket().getFields() != null) {
      Map<String, String> fields = new HashMap<>();
      taskResponse.getTicket().getFields().forEach((k, v) -> fields.put(k, v.getDisplayValue()));
      serviceNowTicketOutcomeBuilder.fields(fields);
    }
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(
            StepResponse.StepOutcome.builder().name("ticket").outcome(serviceNowTicketOutcomeBuilder.build()).build())
        .build();
  }

  @Override
  public StepResponse prepareImportSetStepResponse(ThrowingSupplier<ServiceNowTaskNGResponse> responseSupplier)
      throws Exception {
    ServiceNowTaskNGResponse taskResponse = responseSupplier.get();
    String importSet = taskResponse.getServiceNowImportSetResponseNG().getImportSet();
    String stagingTable = taskResponse.getServiceNowImportSetResponseNG().getStagingTable();
    if (isNull(importSet) || isNull(stagingTable)) {
      throw new ServiceNowException(
          "Invalid transform map details received from ServiceNow, missing import_set or staging_table field",
          SERVICENOW_ERROR, USER);
    }
    ServiceNowImportSetOutcomeBuilder serviceNowImportSetOutcomeBuilder =
        ServiceNowImportSetOutcome.builder().importSetNumber(importSet).stagingTable(stagingTable);

    if (taskResponse.getServiceNowImportSetResponseNG().getServiceNowImportSetTransformMapResultList() != null) {
      List<ServiceNowImportSetTransformMapOutcome> serviceNowImportSetTransformMapOutcomeList = new ArrayList<>();
      taskResponse.getServiceNowImportSetResponseNG().getServiceNowImportSetTransformMapResultList().forEach(
          transformMapResult
          -> serviceNowImportSetTransformMapOutcomeList.add(
              ServiceNowImportSetTransformMapOutcome.fromServiceNowImportSetTransformMapResult(transformMapResult)));
      serviceNowImportSetOutcomeBuilder.transformMapOutcomes(serviceNowImportSetTransformMapOutcomeList);
    }
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name("output")
                         .outcome(serviceNowImportSetOutcomeBuilder.build())
                         .build())
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      ServiceNowConnectorDTO serviceNowConnectorDTO, NGAccess ngAccess) {
    if (!isNull(serviceNowConnectorDTO.getAuth()) && !isNull(serviceNowConnectorDTO.getAuth().getCredentials())) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, serviceNowConnectorDTO.getAuth().getCredentials());
    }
    return secretManagerClientService.getEncryptionDetails(ngAccess, serviceNowConnectorDTO);
  }
}
