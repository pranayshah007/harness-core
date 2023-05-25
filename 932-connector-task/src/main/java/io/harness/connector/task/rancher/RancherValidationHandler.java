/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.rancher;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.delegate.beans.connector.rancher.RancherTestConnectionTaskParams;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.rancher.RancherHelperService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

// Specific to Manager side connector validation
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class RancherValidationHandler implements ConnectorValidationHandler {
  @Inject private RancherNgConfigMapper rancherNgConfigMapper;
  @Inject private RancherHelperService rancherHelperService;
  @Inject private ExceptionManager exceptionManager;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try {
      final RancherTestConnectionTaskParams rancherTestConnectionTaskParams =
          (RancherTestConnectionTaskParams) connectorValidationParams;
      final RancherConnectorDTO connectorDTO = rancherTestConnectionTaskParams.getRancherConnectorDTO();
      final List<EncryptedDataDetail> encryptedDataDetails = rancherTestConnectionTaskParams.getEncryptedDataDetails();
      return validateInternal(connectorDTO, encryptedDataDetails);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(
      RancherTaskParams rancherTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final RancherConnectorDTO rancherConnectorDTO = rancherTaskParams.getRancherConnectorDTO();
    return validateInternal(rancherConnectorDTO, encryptionDetails);
  }

  private ConnectorValidationResult validateInternal(
      RancherConnectorDTO rancherConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    RancherConfig rancherConfig =
        rancherNgConfigMapper.rancherConnectorDTOToConfig(rancherConnectorDTO, encryptedDataDetails);
    return handleValidateTask(rancherConfig);
  }

  private ConnectorValidationResult handleValidateTask(RancherConfig rancherConfig) {
    return rancherHelperService.testRancherConnection(rancherConfig.getCredential().getRancherUrl(),
        rancherConfig.getCredential().getPassword().getRancherPassword());
  }
}
