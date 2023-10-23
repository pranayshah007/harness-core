/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.publishers.impl;

import static io.harness.delegate.beans.connector.awsconnector.AwsTaskType.PUT_AUDIT_BATCH_TO_BUCKET;
import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.beans.BatchFailureInfo;
import io.harness.auditevent.streaming.beans.PublishResponse;
import io.harness.auditevent.streaming.beans.PublishResponse.PublishResponseBuilder;
import io.harness.auditevent.streaming.beans.PublishResponseStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class AwsS3StreamingPublisherTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  public static final String SCOPED_CONNECTOR_IDENTIFIER_REF = "account." + randomAlphabetic(10);
  public static final String BUCKET = randomAlphabetic(10);
  @Mock(answer = RETURNS_DEEP_STUBS) private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private Call call;
  private AwsS3StreamingPublisher awsS3StreamingPublisher;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.awsS3StreamingPublisher = new AwsS3StreamingPublisher(
        connectorResourceClient, secretManagerClientService, taskSetupAbstractionHelper, delegateGrpcClientWrapper);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetAwsConnector() throws IOException {
    ConnectorDTO connectorDTO = getConnector();
    mockGetConnectorApiCall(connectorDTO);
    AwsConnectorDTO connector =
        awsS3StreamingPublisher.getAwsConnector(ACCOUNT_IDENTIFIER, SCOPED_CONNECTOR_IDENTIFIER_REF);
    assertThat(connector).isNotNull().isEqualTo(connectorDTO.getConnectorInfo().getConnectorConfig());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetAwsConnectorWhenNotFound() throws IOException {
    mockGetConnectorApiCall(null);
    assertThatThrownBy(
        () -> awsS3StreamingPublisher.getAwsConnector(ACCOUNT_IDENTIFIER, SCOPED_CONNECTOR_IDENTIFIER_REF))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("AWS ConnectorDisconnectHandler not found for identifier : [%s] with scope: [%s]",
            SCOPED_CONNECTOR_IDENTIFIER_REF.split("\\.")[1], Scope.ACCOUNT));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetAwsEncryptionDetails() {
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().build()));
    AwsConnectorDTO connectorDTO = getAwsConnectorDTO();
    BaseNGAccess ngAccess = BaseNGAccess.builder().build();
    List<EncryptedDataDetail> encryptionDetails =
        awsS3StreamingPublisher.getAwsEncryptionDetails(connectorDTO, ngAccess);
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(ngAccess, connectorDTO.getCredential().getConfig());
    assertThat(encryptionDetails).isNotEmpty();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testPublishWhenOutgoingAuditMessagesListIsEmpty() {
    PublishResponse response = awsS3StreamingPublisher.publish(
        AwsS3StreamingDestination.builder().build(), StreamingBatch.builder().build(), List.of());
    verify(delegateGrpcClientWrapper, times(0)).executeSyncTaskV2(any());
    assertThat(response).isEqualToComparingFieldByField(
        PublishResponse.builder().status(PublishResponseStatus.SUCCESS).build());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testPublishWhenConnectorIsDelegateLess() throws IOException {
    ConnectorDTO connectorDTO = getConnector();
    ((AwsConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig()).setExecuteOnDelegate(false);
    mockGetConnectorApiCall(connectorDTO);
    PublishResponse response = awsS3StreamingPublisher.publish(getAwsS3StreamingDestination(),
        StreamingBatch.builder().build(), List.of(OutgoingAuditMessage.builder().build()));
    verify(delegateGrpcClientWrapper, times(0)).executeSyncTaskV2(any());
    assertThat(response).isEqualToComparingFieldByField(getPublishResponse(PublishResponseStatus.FAILED,
        String.format(
            "Ensure that the connectivity mode for the ConnectorDisconnectHandler [%s] should be: Connect through harness delegate.",
            SCOPED_CONNECTOR_IDENTIFIER_REF)));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testPublish() throws IOException {
    ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<BaseNGAccess> ngAccessArgumentCaptor = ArgumentCaptor.forClass(BaseNGAccess.class);
    ConnectorDTO connectorDTO = getConnector();
    mockGetConnectorApiCall(connectorDTO);
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().build()));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(AwsPutAuditBatchToBucketTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .build());
    AwsS3StreamingDestination streamingDestination = getAwsS3StreamingDestination();
    PublishResponse publishResponse = awsS3StreamingPublisher.publish(
        streamingDestination, StreamingBatch.builder().build(), List.of(OutgoingAuditMessage.builder().build()));
    verifyMethodCallsForPublish(taskRequestArgumentCaptor, ngAccessArgumentCaptor, connectorDTO);
    assertThat(publishResponse).isEqualToComparingFieldByField(getPublishResponse(PublishResponseStatus.SUCCESS, null));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testPublishForFailureTaskResponse() throws IOException {
    String errorMessage = "Invalid credentials";
    ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<BaseNGAccess> ngAccessArgumentCaptor = ArgumentCaptor.forClass(BaseNGAccess.class);
    ConnectorDTO connectorDTO = getConnector();
    mockGetConnectorApiCall(connectorDTO);
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().build()));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(AwsPutAuditBatchToBucketTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                        .errorMessage(errorMessage)
                        .build());
    AwsS3StreamingDestination streamingDestination = getAwsS3StreamingDestination();
    PublishResponse publishResponse = awsS3StreamingPublisher.publish(
        streamingDestination, StreamingBatch.builder().build(), List.of(OutgoingAuditMessage.builder().build()));
    verifyMethodCallsForPublish(taskRequestArgumentCaptor, ngAccessArgumentCaptor, connectorDTO);
    assertThat(publishResponse)
        .isEqualToComparingFieldByField(getPublishResponse(PublishResponseStatus.FAILED, errorMessage));
  }

  private void verifyMethodCallsForPublish(ArgumentCaptor<DelegateTaskRequest> taskRequestArgumentCaptor,
      ArgumentCaptor<BaseNGAccess> ngAccessArgumentCaptor, ConnectorDTO connectorDTO) {
    verify(connectorResourceClient, times(1))
        .get(SCOPED_CONNECTOR_IDENTIFIER_REF.split("\\.")[1], ACCOUNT_IDENTIFIER, null, null);

    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(ngAccessArgumentCaptor.capture(),
            eq(((AwsConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig()).getCredential().getConfig()));
    assertThat(ngAccessArgumentCaptor.getValue())
        .isEqualToComparingFieldByField(
            BaseNGAccess.builder().accountIdentifier(ACCOUNT_IDENTIFIER).identifier(SCOPED_CONNECTOR_IDENTIFIER_REF));

    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(taskRequestArgumentCaptor.capture());
    DelegateTaskRequest capturedTaskRequest = taskRequestArgumentCaptor.getValue();
    assertThat(capturedTaskRequest.getTaskParameters()).isInstanceOf(AwsPutAuditBatchToBucketTaskParamsRequest.class);
    AwsPutAuditBatchToBucketTaskParamsRequest bucketTaskParamsRequest =
        (AwsPutAuditBatchToBucketTaskParamsRequest) capturedTaskRequest.getTaskParameters();
    assertThat(bucketTaskParamsRequest.getAwsTaskType()).isEqualTo(PUT_AUDIT_BATCH_TO_BUCKET);
    assertThat(bucketTaskParamsRequest.getAwsConnector())
        .isEqualTo(connectorDTO.getConnectorInfo().getConnectorConfig());
    assertThat(bucketTaskParamsRequest.getBucketName()).isEqualTo(BUCKET);
    assertThat(bucketTaskParamsRequest.getAuditBatch()).isNotNull();
    assertThat(bucketTaskParamsRequest.getAuditBatch().getOutgoingAuditMessages()).isNotEmpty();
  }

  private AwsS3StreamingDestination getAwsS3StreamingDestination() {
    return AwsS3StreamingDestination.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .connectorRef(SCOPED_CONNECTOR_IDENTIFIER_REF)
        .bucket(BUCKET)
        .build();
  }

  private PublishResponse getPublishResponse(PublishResponseStatus status, String errorMessage) {
    PublishResponseBuilder responseBuilder = PublishResponse.builder().status(status);
    if (status == PublishResponseStatus.FAILED && null != errorMessage) {
      responseBuilder.failureInfo(BatchFailureInfo.builder().message(errorMessage).build());
    }
    return responseBuilder.build();
  }

  private void mockGetConnectorApiCall(ConnectorDTO connectorDTO) throws IOException {
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Optional.ofNullable(connectorDTO))));
    when(connectorResourceClient.get(any(), any(), any(), any())).thenReturn(call);
  }

  private ConnectorDTO getConnector() {
    ConnectorInfoDTO connectorInfo =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.AWS).connectorConfig(getAwsConnectorDTO()).build();
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  private AwsConnectorDTO getAwsConnectorDTO() {
    return AwsConnectorDTO.builder()
        .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
        .delegateSelectors(Set.of(randomAlphabetic(10)))
        .executeOnDelegate(true)
        .build();
  }
}
