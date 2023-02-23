/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.gcp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;

import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;
import io.harness.delegate.task.gcp.response.GcpResponse;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class GcpHelperServiceManager {
  @Inject private GcpHelperService gcpHelperService;
  @Inject private DelegateService delegateService;
  @Inject private EncryptionService encryptionService;

  public void validateCredential(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    if (gcpConfig.isUseDelegateSelectors()) {
      validateDelegateSelector(gcpConfig);
      GcpValidationRequest gcpValidationRequest =
          GcpValidationRequest.builder().delegateSelectors(new HashSet<>(gcpConfig.getDelegateSelectors())).build();
      GcpTaskParameters gcpTaskParameters =
          GcpTaskParameters.builder().gcpTaskType(GcpTaskType.VALIDATE).gcpRequest(gcpValidationRequest).build();
      final GcpResponse gcpResponse = executeSyncTask(gcpConfig.getAccountId(), gcpTaskParameters);
      ConnectorValidationResult validationResult =
          ((GcpValidationTaskResponse) gcpResponse).getConnectorValidationResult();
      if (validationResult.getStatus() != ConnectivityStatus.SUCCESS) {
        String errorMessage = getErrorMessage(validationResult.getErrors());
        throw new InvalidRequestException(errorMessage, USER);
      }
    } else {
      // Decrypt gcpConfig
      encryptionService.decrypt(gcpConfig, encryptedDataDetails, false);
      gcpHelperService.getGkeContainerService(
          gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors());
    }
  }

  private String getErrorMessage(List<ErrorDetail> errors) {
    if (isNotEmpty(errors) && errors.size() == 1) {
      return errors.get(0).getMessage();
    }
    return "Invalid Credentials";
  }

  private void validateDelegateSelector(GcpConfig gcpConfig) {
    if (isEmpty(gcpConfig.getDelegateSelectors())) {
      throw new InvalidRequestException("No Delegate Selector Found. Unable to validate", USER);
    }
  }

  private GcpResponse executeSyncTask(String accountId, GcpTaskParameters gcpTaskParameters) {
    List<String> tags = isNotEmpty(gcpTaskParameters.getGcpRequest().getDelegateSelectors())
        ? new ArrayList<>(gcpTaskParameters.getGcpRequest().getDelegateSelectors())
        : Collections.emptyList();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, SCOPE_WILDCARD)
                                    .tags(tags)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GCP_TASK.name())
                                              .parameters(new Object[] {gcpTaskParameters})
                                              .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      validateDelegateSuccessForSyncTask(notifyResponseData);
      return (GcpResponse) notifyResponseData;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), USER);
    }
  }

  @VisibleForTesting
  void validateDelegateSuccessForSyncTask(DelegateResponseData notifyResponseData) {
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      throw new InvalidRequestException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage(), USER);
    } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
      throw new InvalidRequestException(
          getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()), USER);
    } else if (!(notifyResponseData instanceof GcpResponse)) {
      throw new InvalidRequestException(
          format("Unknown response from delegate: [%s]", notifyResponseData.getClass().getSimpleName()), USER);
    }
  }
}
