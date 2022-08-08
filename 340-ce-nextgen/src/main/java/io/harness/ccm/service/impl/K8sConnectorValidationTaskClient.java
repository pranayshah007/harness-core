/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.beans.DelegateTaskRequest;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.k8Connector.CEKubernetesConnectionTaskParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class K8sConnectorValidationTaskClient {
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private K8sConnectorHelper connectorHelper;

  public ConnectorValidationResult validateConnectorForCePermissions(
      @NonNull String connectorIdentifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    DelegateTaskRequest delegateTaskRequest =
        createCeValidateKubernetesConfigTask(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);

    DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);

    checkForErrorResponseForValidationResult(responseData);
    return ((KubernetesConnectionTaskResponse) responseData).getConnectorValidationResult();
  }

  private DelegateTaskRequest createCeValidateKubernetesConfigTask(
      String connectorIdentifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ConnectorConfigDTO connectorConfig =
        connectorHelper.getConnectorConfig(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);

    TaskParameters taskParameters = createCEKubernetesConnectionTaskParams(
        (KubernetesClusterConfigDTO) connectorConfig, accountIdentifier, orgIdentifier, projectIdentifier);

    if (taskParameters instanceof ConnectorTaskParams && connectorConfig instanceof DelegateSelectable) {
      ((ConnectorTaskParams) taskParameters)
          .setDelegateSelectors(((DelegateSelectable) connectorConfig).getDelegateSelectors());
    }

    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountIdentifier, orgIdentifier, projectIdentifier);

    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskType(TaskType.CE_VALIDATE_KUBERNETES_CONFIG.name())
        .taskParameters(taskParameters)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .executionTimeout(Duration.ofMinutes(5))
        .taskDescription(TaskType.CE_VALIDATE_KUBERNETES_CONFIG.getDisplayName())
        .forceExecute(true)
        .build();
  }

  private TaskParameters createCEKubernetesConnectionTaskParams(KubernetesClusterConfigDTO kubernetesClusterConfig,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<EncryptedDataDetail> encryptedDataDetailList = connectorHelper.getEncryptionDetail(
        kubernetesClusterConfig, accountIdentifier, orgIdentifier, projectIdentifier);

    return CEKubernetesConnectionTaskParams.builder()
        .kubernetesClusterConfig(kubernetesClusterConfig)
        .encryptionDetails(encryptedDataDetailList)
        .featuresEnabled(ImmutableList.of(CEFeatures.VISIBILITY))
        .build();
  }

  private static void checkForErrorResponseForValidationResult(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      log.info("Error in {} task for connector : [{}] with failure types [{}]", TaskType.K8S_SERVICE_ACCOUNT_INFO,
          errorNotifyResponseData.getErrorMessage(), errorNotifyResponseData.getFailureTypes());
      throw new InvalidRequestException(errorNotifyResponseData.getErrorMessage());
    }

    if (responseData instanceof RemoteMethodReturnValueData
        && (((RemoteMethodReturnValueData) responseData).getException() instanceof InvalidRequestException)) {
      String errorMessage = ((RemoteMethodReturnValueData) responseData).getException().getMessage();
      throw new InvalidRequestException(errorMessage);
    }

    Preconditions.checkState(responseData instanceof KubernetesConnectionTaskResponse,
        String.format("Please catch new DelegateResponseData type %s", responseData.getClass().toString()));
  }
}
