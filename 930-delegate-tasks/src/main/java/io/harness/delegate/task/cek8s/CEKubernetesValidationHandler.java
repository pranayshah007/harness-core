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
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.k8Connector.CEK8sValidationParams;
import io.harness.delegate.task.k8s.helpers.k8sbase.K8sTaskHelperBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.CE)
public class CEKubernetesValidationHandler implements ConnectorValidationHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final CEK8sValidationParams k8sValidationParams = (CEK8sValidationParams) connectorValidationParams;

    return k8sTaskHelperBase.validateCEKubernetesCluster(k8sValidationParams.getKubernetesClusterConfigDTO(),
        accountIdentifier, k8sValidationParams.getEncryptedDataDetails(), k8sValidationParams.getFeaturesEnabled());
  }
}
