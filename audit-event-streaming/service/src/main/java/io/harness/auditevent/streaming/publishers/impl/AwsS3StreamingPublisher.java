/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.publishers.impl;

import static io.harness.auditevent.streaming.AuditEventStreamingConstants.AWS_S3_STREAMING_PUBLISHER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.entities.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.publishers.StreamingPublisher;
import io.harness.beans.DelegateTaskRequest;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.Scope;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

@Component(AWS_S3_STREAMING_PUBLISHER)
public class AwsS3StreamingPublisher implements StreamingPublisher {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @VisibleForTesting static final int timeoutInSecs = 30;

  public AwsS3StreamingPublisher(ConnectorResourceClient connectorResourceClient,
      SecretManagerClientService secretManagerClientService, TaskSetupAbstractionHelper taskSetupAbstractionHelper,
      DelegateGrpcClientWrapper delegateGrpcClientWrapper) {
    this.connectorResourceClient = connectorResourceClient;
    this.secretManagerClientService = secretManagerClientService;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
  }

  private boolean isAAwsConnector(@Valid @NotNull ConnectorDTO connectorResponseDTO) {
    return ConnectorType.AWS.equals(connectorResponseDTO.getConnectorInfo().getConnectorType());
  }

  public AwsConnectorDTO getAwsConnector(String accountIdentifier, String identifier) {
    Optional<ConnectorDTO> connectorDTO =
        NGRestUtils.getResponse(connectorResourceClient.get(identifier, accountIdentifier, null, null));
    if (!connectorDTO.isPresent() || !isAAwsConnector(connectorDTO.get())) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier : [%s] with scope: [%s]", identifier, Scope.ACCOUNT),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnectorInfo();
    return (AwsConnectorDTO) connectors.getConnectorConfig();
  }

  public List<EncryptedDataDetail> getAwsEncryptionDetails(
      @Nonnull AwsConnectorDTO awsConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig());
    }
    return Collections.emptyList();
  }

  public Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = null;
    // Verify if its a Task from NG
    owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }

  @Override
  public boolean publish(StreamingDestination streamingDestination, List<OutgoingAuditMessage> outgoingAuditMessages) {
    AwsConnectorDTO connector =
        getAwsConnector(streamingDestination.getAccountIdentifier(), streamingDestination.getConnectorRef());

    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(streamingDestination.getAccountIdentifier())
                                    .identifier(streamingDestination.getConnectorRef())
                                    .build();

    List<EncryptedDataDetail> encryptionDetails = getAwsEncryptionDetails(connector, baseNGAccess);

    if (AWS_S3.equals(streamingDestination.getType())) {
      AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                        .awsTaskType(AwsTaskType.PUT_AUDIT_BATCH_TO_BUCKET)
                                        .awsConnector(connector)
                                        .encryptionDetails(encryptionDetails)
                                        .region(AWS_DEFAULT_REGION)
                                        .build();

      final DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(streamingDestination.getAccountIdentifier())
              .taskType(TaskType.NG_AWS_TASK.name())
              .taskParameters(awsTaskParams)
              .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
              .taskSetupAbstractions(buildAbstractions(streamingDestination.getAccountIdentifier(), null, null))
              .taskSelectors(((AwsTaskParams) awsTaskParams).getAwsConnector().getDelegateSelectors())
              .build();
      try {
        delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      } catch (DelegateServiceDriverException ex) {
        // Handle Exception
      }
    } else {
      return false;
    }

    return true;
  }
}
