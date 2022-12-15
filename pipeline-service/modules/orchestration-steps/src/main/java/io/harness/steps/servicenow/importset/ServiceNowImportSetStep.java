/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.importset;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.servicenow.beans.ImportDataSpecWrapperDTO;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(CDC)
public class ServiceNowImportSetStep extends TaskExecutableWithRollbackAndRbac<ServiceNowTaskNGResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SERVICE_NOW_IMPORT_SET_STEP_TYPE;

  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceNowStepHelperService serviceNowStepHelperService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ServiceNowImportSetSpecParameters specParameters = (ServiceNowImportSetSpecParameters) stepParameters.getSpec();
    String connectorRef = specParameters.getConnectorRef().getValue();
    if (isBlank(connectorRef)) {
      throw new InvalidRequestException("connectorRef can't be empty");
    }
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    List<EntityDetail> entityDetailList = new ArrayList<>();
    entityDetailList.add(entityDetail);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    ServiceNowImportSetSpecParameters specParameters = (ServiceNowImportSetSpecParameters) stepParameters.getSpec();

    return serviceNowStepHelperService.prepareTaskRequest(getServiceNowTaskNGFromSpecParameters(specParameters),
        ambiance, specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(),
        String.format("ServiceNow Task: %s", ServiceNowActionNG.IMPORT_SET));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<ServiceNowTaskNGResponse> responseSupplier) throws Exception {
    return serviceNowStepHelperService.prepareImportSetStepResponse(responseSupplier);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private ServiceNowTaskNGParametersBuilder getServiceNowTaskNGFromSpecParameters(
      ServiceNowImportSetSpecParameters specParameters) {
    String stagingTableName = specParameters.getStagingTableName().getValue();
    if (isBlank(stagingTableName)) {
      throw new InvalidRequestException("Staging table name can't be empty");
    }

    ImportDataSpecWrapperDTO importDataSpecWrapperDTO =
        ImportDataSpecWrapperDTO.fromImportDataSpecWrapper(specParameters.getImportData());
    if (isNull(importDataSpecWrapperDTO)) {
      throw new InvalidRequestException("Import data can't be null");
    }
    String importDataJson = importDataSpecWrapperDTO.getImportDataSpecDTO().getImportDataJson();
    if (isNull(importDataJson)) {
      throw new InvalidRequestException("Json string representing import data can't be null");
    }
    return ServiceNowTaskNGParameters.builder()
        .action(ServiceNowActionNG.IMPORT_SET)
        .stagingTableName(stagingTableName)
        .importData(importDataJson)
        .delegateSelectors(
            StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()));
  }
}
