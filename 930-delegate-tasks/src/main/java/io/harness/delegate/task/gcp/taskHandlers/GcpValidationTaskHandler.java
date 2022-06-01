/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskHandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.gcp.GcpValidationParams;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.GcpRequestMapper;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.response.GcpResponse;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.gcp.client.GcpClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class GcpValidationTaskHandler implements TaskHandler, ConnectorValidationHandler {
  @Inject private GcpClient gcpClient;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private GcpRequestMapper gcpRequestMapper;

  @Override
  public GcpResponse executeRequest(GcpRequest gcpRequest) {
    return validateInternal(gcpRequest, gcpRequest.getEncryptionDetails());
  }
  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final GcpValidationParams gcpValidationParams = (GcpValidationParams) connectorValidationParams;

    final GcpRequest gcpRequest = gcpRequestMapper.toGcpRequest(gcpValidationParams);

    GcpValidationTaskResponse gcpValidationTaskResponse =
        validateInternal(gcpRequest, gcpValidationParams.getEncryptionDetails());

    return gcpValidationTaskResponse.getConnectorValidationResult();
  }

  @VisibleForTesting
  GcpValidationTaskResponse validateInternal(GcpRequest gcpRequest, List<EncryptedDataDetail> encryptionDetails) {
    if (hasManualCredentials(gcpRequest)) {
      return (GcpValidationTaskResponse) validateGcpServiceAccountKeyCredential(gcpRequest);
    } else {
      gcpClient.validateDefaultCredentials();
      ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                                .status(ConnectivityStatus.SUCCESS)
                                                                .testedAt(System.currentTimeMillis())
                                                                .build();
      return GcpValidationTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
    }
  }

  private boolean hasManualCredentials(GcpRequest gcpRequest) {
    return gcpRequest.getGcpManualDetailsDTO() != null && gcpRequest.getGcpManualDetailsDTO().getSecretKeyRef() != null;
  }

  private GcpResponse validateGcpServiceAccountKeyCredential(GcpRequest gcpRequest) {
    if (!hasManualCredentials(gcpRequest)) {
      throw new InvalidRequestException("Authentication details not found");
    }

    GcpManualDetailsDTO gcpManualDetailsDTO = gcpRequest.getGcpManualDetailsDTO();
    secretDecryptionService.decrypt(gcpManualDetailsDTO, gcpRequest.getEncryptionDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(gcpManualDetailsDTO, gcpRequest.getEncryptionDetails());
    gcpClient.getGkeContainerService(gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue());
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();
    return GcpValidationTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }

  private GcpValidationTaskResponse getFailedGcpResponse(Exception ex) {
    ConnectorValidationResult connectorValidationResult =
        ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .testedAt(System.currentTimeMillis())
            .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(ex.getMessage())))
            .errorSummary(ngErrorHelper.getErrorSummary(ex.getMessage()))
            .build();
    return GcpValidationTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }

  private String getRootCauseMessage(Throwable t) {
    return Optional.ofNullable(getRootCause(t)).map(Throwable::getMessage).orElse(StringUtils.EMPTY);
  }
}
