/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.cek8s;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.k8Connector.CEK8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.CEKubernetesConnectionTaskParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.CE)
public class CEKubernetesTestConnectionDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private CEKubernetesValidationHandler ceKubernetesValidationHandler;

  public CEKubernetesTestConnectionDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CEKubernetesConnectionTaskParams ceKubernetesConnectionTaskParams = (CEKubernetesConnectionTaskParams) parameters;
    final CEK8sValidationParams cek8sValidationParams =
        CEK8sValidationParams.builder()
            .encryptedDataDetails(ceKubernetesConnectionTaskParams.getEncryptionDetails())
            .kubernetesClusterConfigDTO(ceKubernetesConnectionTaskParams.getKubernetesClusterConfig())
            .featuresEnabled(ceKubernetesConnectionTaskParams.getFeaturesEnabled())
            .build();
    ConnectorValidationResult connectorValidationResult =
        ceKubernetesValidationHandler.validate(cek8sValidationParams, getAccountId());
    connectorValidationResult.setDelegateId(getDelegateId());
    return KubernetesConnectionTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}
