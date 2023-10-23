/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.spot;

import static io.harness.delegate.beans.connector.spotconnector.SpotConstants.INVALID_CREDS;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotTaskParams;
import io.harness.delegate.beans.connector.spotconnector.SpotValidationParams;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.SpotInstHelperServiceDelegate;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

// Specific to Manager side ConnectorDisconnectHandler validation
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class SpotValidationHandler implements ConnectorValidationHandler {
  @Inject private SpotNgConfigMapper ngConfigMapper;
  @Inject private SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private ExceptionManager exceptionManager;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try {
      final SpotValidationParams spotValidationParams = (SpotValidationParams) connectorValidationParams;
      final SpotConnectorDTO connectorDTO = spotValidationParams.getSpotConnectorDTO();
      final List<EncryptedDataDetail> encryptedDataDetails = spotValidationParams.getEncryptedDataDetails();
      return validateInternal(connectorDTO, encryptedDataDetails);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(
      SpotTaskParams spotTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final SpotConnectorDTO spotConnector = spotTaskParams.getSpotConnector();
    return validateInternal(spotConnector, encryptionDetails);
  }

  private ConnectorValidationResult validateInternal(
      SpotConnectorDTO spotConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    SpotConfig spotConfig = ngConfigMapper.mapSpotConfigWithDecryption(spotConnectorDTO, encryptedDataDetails);
    return handleValidateTask(spotConfig);
  }

  private ConnectorValidationResult handleValidateTask(SpotConfig spotConfig) {
    ConnectorValidationResultBuilder builder = ConnectorValidationResult.builder();
    try {
      spotInstHelperServiceDelegate.listAllElstiGroups(
          spotConfig.getCredential().getAppTokenId(), spotConfig.getCredential().getSpotAccountId());
      builder.status(ConnectivityStatus.SUCCESS);
    } catch (Exception e) {
      builder.status(ConnectivityStatus.FAILURE);
      builder.errorSummary(INVALID_CREDS);
      builder.errors(Lists.newArrayList(ErrorDetail.builder()
                                            .reason(INVALID_CREDS)
                                            .message(ExceptionMessageSanitizer.sanitizeMessage(e.getMessage()))
                                            .build()));
    }

    return builder.testedAt(System.currentTimeMillis()).build();
  }
}
