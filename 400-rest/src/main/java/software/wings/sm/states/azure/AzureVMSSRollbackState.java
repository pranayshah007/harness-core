/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.SKIP_VMSS_ROLLBACK;
import static io.harness.exception.utils.ExceptionUtils.getMessage;

import static software.wings.service.impl.azure.manager.AzureVMSSAllPhaseRollbackData.AZURE_VMSS_ALL_PHASE_ROLLBACK;
import static software.wings.sm.StateType.AZURE_VMSS_ROLLBACK;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.beans.InstanceUnitType;
import software.wings.service.impl.azure.manager.AzureVMSSAllPhaseRollbackData;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureVMSSRollbackState extends AzureVMSSDeployState {
  @Inject private transient SweepingOutputService sweepingOutputService;

  public AzureVMSSRollbackState(String name) {
    super(name, AZURE_VMSS_ROLLBACK);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      if (allPhaseRollbackDone(context)) {
        return ExecutionResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build();
      }
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      if (isSuccess(response)) {
        markAllPhaseRollbackDone(context);
      }
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  private boolean isSuccess(Map<String, ResponseData> response) {
    AzureVMSSTaskExecutionResponse executionResponse =
        (AzureVMSSTaskExecutionResponse) response.values().iterator().next();
    return executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS;
  }

  protected boolean allPhaseRollbackDone(ExecutionContext context) {
    SweepingOutputInquiry sweepingOutputInquiry =
        context.prepareSweepingOutputInquiryBuilder().name(AZURE_VMSS_ALL_PHASE_ROLLBACK).build();
    AzureVMSSAllPhaseRollbackData rollbackData = sweepingOutputService.findSweepingOutput(sweepingOutputInquiry);
    return rollbackData != null && rollbackData.isAllPhaseRollbackDone();
  }

  protected void markAllPhaseRollbackDone(ExecutionContext context) {
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(AZURE_VMSS_ALL_PHASE_ROLLBACK)
                                   .value(AzureVMSSAllPhaseRollbackData.builder().allPhaseRollbackDone(true).build())
                                   .build());
  }

  @Override
  public boolean isRollback() {
    return true;
  }

  @Override
  @SchemaIgnore
  public Integer getInstanceCount() {
    return super.getInstanceCount();
  }

  @Override
  @SchemaIgnore
  public InstanceUnitType getInstanceUnitType() {
    return super.getInstanceUnitType();
  }

  @Override
  protected int getNewDesiredCount(AzureVMSSSetupContextElement azureVMSSSetupContextElement) {
    return 0;
  }

  @Override
  protected int getOldDesiredCount(AzureVMSSSetupContextElement azureVMSSSetupContextElement, int newDesiredCount) {
    AzureVMSSPreDeploymentData preDeploymentData = azureVMSSSetupContextElement.getPreDeploymentData();
    return preDeploymentData != null ? preDeploymentData.getDesiredCapacity() : 0;
  }

  @Override
  protected String getSkipMessage() {
    return SKIP_VMSS_ROLLBACK;
  }

  @Override
  public Map<String, String> validateFields() {
    return Collections.emptyMap();
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }
}
