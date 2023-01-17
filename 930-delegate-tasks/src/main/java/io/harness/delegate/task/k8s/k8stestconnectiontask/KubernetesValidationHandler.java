/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.k8stestconnectiontask;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.delegate.task.k8s.helpers.k8sbase.K8sTaskHelperBase;

@Singleton
@OwnedBy(CDP)
public class KubernetesValidationHandler implements ConnectorValidationHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final K8sValidationParams k8sValidationParams = (K8sValidationParams) connectorValidationParams;
    return k8sTaskHelperBase.validate(
        k8sValidationParams.getKubernetesClusterConfigDTO(), k8sValidationParams.getEncryptedDataDetails());
  }
}
