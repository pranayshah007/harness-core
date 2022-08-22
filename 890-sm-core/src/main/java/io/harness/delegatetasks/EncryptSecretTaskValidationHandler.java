/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsValidationParams;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerValidationParams;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsValidationParams;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.SecretManagerConfigDTOMapper;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

@OwnedBy(PL)
public class EncryptSecretTaskValidationHandler implements ConnectorValidationHandler {
  @Inject KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Inject NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    long currentTime = System.currentTimeMillis();
    EncryptSecretTaskResponse encryptSecretTaskResponse;
    try {
      EncryptSecretTaskParameters encryptSecretTaskParameters =
          getTaskParams(connectorValidationParams, accountIdentifier);
      encryptSecretTaskResponse = EncryptSecretTask.run(encryptSecretTaskParameters, kmsEncryptorsRegistry);
    } catch (Exception exception) {
      String errorMessage = exception.getMessage();
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .testedAt(currentTime)
          .errorSummary(ngErrorHelper.createErrorSummary("Invalid Credentials", errorMessage))
          .errors(getErrorDetail(errorMessage))
          .build();
    }

    if (encryptSecretTaskResponse.getEncryptedRecord() != null) {
      return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).testedAt(currentTime).build();
    } else {
      return ConnectorValidationResult.builder().status(ConnectivityStatus.FAILURE).testedAt(currentTime).build();
    }
  }

  private EncryptSecretTaskParameters getTaskParams(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    if (ConnectorType.GCP_KMS.equals(connectorValidationParams.getConnectorType())) {
      GcpKmsConnectorDTO gcpKmsConnectorDTO =
          ((GcpKmsValidationParams) connectorValidationParams).getGcpKmsConnectorDTO();
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().connectorConfig(gcpKmsConnectorDTO).connectorType(ConnectorType.GCP_KMS).build();
      SecretManagerConfigDTO secretManagerConfigDTO = SecretManagerConfigDTOMapper.fromConnectorDTO(
          accountIdentifier, ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), gcpKmsConnectorDTO);
      return EncryptSecretTaskParameters.builder()
          .encryptionConfig(SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO))
          .value(UUIDGenerator.generateUuid())
          .build();
    } else if (ConnectorType.AWS_KMS.equals(connectorValidationParams.getConnectorType())) {
      AwsKmsConnectorDTO awsKmsConnectorDTO =
          ((AwsKmsValidationParams) connectorValidationParams).getAwsKmsConnectorDTO();
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder().connectorConfig(awsKmsConnectorDTO).connectorType(ConnectorType.AWS_KMS).build();
      SecretManagerConfigDTO secretManagerConfigDTO = SecretManagerConfigDTOMapper.fromConnectorDTO(
          accountIdentifier, ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), awsKmsConnectorDTO);
      return EncryptSecretTaskParameters.builder()
          .encryptionConfig(SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO))
          .value(UUIDGenerator.generateUuid())
          .build();
    } else if (ConnectorType.AWS_SECRET_MANAGER.equals(connectorValidationParams.getConnectorType())) {
      AwsSecretManagerDTO awsSecretManagerDTO =
          ((AwsSecretManagerValidationParams) connectorValidationParams).getAwsSecretManagerDTO();
      ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                              .connectorConfig(awsSecretManagerDTO)
                                              .connectorType(ConnectorType.AWS_SECRET_MANAGER)
                                              .build();
      SecretManagerConfigDTO secretManagerConfigDTO = SecretManagerConfigDTOMapper.fromConnectorDTO(
          accountIdentifier, ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), awsSecretManagerDTO);
      return EncryptSecretTaskParameters.builder()
          .encryptionConfig(SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO))
          .value(UUIDGenerator.generateUuid())
          .build();
    }
    throw new SecretManagementDelegateException(
        SECRET_MANAGEMENT_ERROR, "Secret Manager not supported for encrypt secret task type.", USER);
  }

  private List<ErrorDetail> getErrorDetail(String errorMessage) {
    return Collections.singletonList(
        ErrorDetail.builder().message(errorMessage).code(450).reason("Invalid Credentials").build());
  }
}
