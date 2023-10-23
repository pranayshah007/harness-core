/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.connector;

import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO.ConnectorValidationResultProto.newBuilder;

import static software.wings.utils.Utils.emptyIfNull;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.dto.ErrorDetail;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(DX)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class ConnectorHearbeatPublisher {
  Producer eventProducer;
  IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  @Inject
  public ConnectorHearbeatPublisher(@Named(EventsFrameworkConstants.ENTITY_ACTIVITY) Producer eventProducer,
      IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper) {
    this.eventProducer = eventProducer;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
  }

  private static final String CONNECTIVITY_CHECK_DESCRIPTION = "Connectivity Check";

  public void pushConnectivityCheckActivity(
      String accountId, ConnectorHeartbeatDelegateResponse heartbeatDelegateResponse) {
    if (heartbeatDelegateResponse == null) {
      log.error("{} got null delegate heartbeat response in the ConnectorDisconnectHandler heartbeat for the account {}",
          CONNECTOR_HEARTBEAT_LOG_PREFIX, accountId);
      return;
    }
    String connectorMessage = String.format(CONNECTOR_STRING, heartbeatDelegateResponse.getIdentifier(), accountId,
        heartbeatDelegateResponse.getOrgIdentifier(), heartbeatDelegateResponse.getProjectIdentifier());
    log.info("Got validation task response for the ConnectorDisconnectHandler {}", connectorMessage);
    EntityActivityCreateDTO ngActivityDTO = createConnectivityCheckActivityDTO(heartbeatDelegateResponse);
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                  EventsFrameworkConstants.ENTITY_ACTIVITY, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.CREATE_ACTION))
              .setData(ngActivityDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("{} Exception while pushing the heartbeat result {}", CONNECTOR_HEARTBEAT_LOG_PREFIX,
          String.format(CONNECTOR_STRING, heartbeatDelegateResponse.getIdentifier(),
              heartbeatDelegateResponse.getAccountIdentifier(), heartbeatDelegateResponse.getOrgIdentifier(),
              heartbeatDelegateResponse.getProjectIdentifier()));
    }
    log.info("Sent validation task response to the ng manager for the ConnectorDisconnectHandler {}", connectorMessage);
  }

  private EntityActivityCreateDTO createConnectivityCheckActivityDTO(
      @NotNull ConnectorHeartbeatDelegateResponse heartbeatDelegateResponse) {
    IdentifierRefProtoDTO identifierRefProtoDTO = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        heartbeatDelegateResponse.getAccountIdentifier(), heartbeatDelegateResponse.getOrgIdentifier(),
        heartbeatDelegateResponse.getProjectIdentifier(), heartbeatDelegateResponse.getIdentifier());
    EntityDetailProtoDTO referredEntity = EntityDetailProtoDTO.newBuilder()
                                              .setType(CONNECTORS)
                                              .setIdentifierRef(identifierRefProtoDTO)
                                              .setName(emptyIfNull(heartbeatDelegateResponse.getName()))
                                              .build();
    NGActivityStatus activityStatus = NGActivityStatus.FAILED;
    if (heartbeatDelegateResponse.getConnectorValidationResult() != null
        && heartbeatDelegateResponse.getConnectorValidationResult().getStatus() == ConnectivityStatus.SUCCESS) {
      activityStatus = NGActivityStatus.SUCCESS;
    }
    String errorMessage = null;
    if (heartbeatDelegateResponse.getConnectorValidationResult() != null) {
      errorMessage = heartbeatDelegateResponse.getConnectorValidationResult().getErrorSummary();
    }
    EntityActivityCreateDTO.Builder builder =
        EntityActivityCreateDTO.newBuilder()
            .setType(NGActivityType.CONNECTIVITY_CHECK.toString())
            .setStatus(activityStatus.toString())
            .setActivityTime(heartbeatDelegateResponse.getConnectorValidationResult().getTestedAt())
            .setAccountIdentifier(heartbeatDelegateResponse.getAccountIdentifier())
            .setDescription(CONNECTIVITY_CHECK_DESCRIPTION)
            .setConnectivityDetail(
                getConnectivityCheckActivityDetailProtoDTO(heartbeatDelegateResponse.getConnectorValidationResult()))
            .setReferredEntity(referredEntity);
    if (errorMessage != null) {
      builder.setErrorMessage(errorMessage);
    }
    return builder.build();
  }

  private EntityActivityCreateDTO.ConnectivityCheckActivityDetailProtoDTO getConnectivityCheckActivityDetailProtoDTO(
      ConnectorValidationResult connectorValidationResult) {
    return EntityActivityCreateDTO.ConnectivityCheckActivityDetailProtoDTO.newBuilder()
        .setConnectorValidationResult(createConnectorValidationResult(connectorValidationResult))
        .build();
  }

  private EntityActivityCreateDTO.ConnectorValidationResultProto createConnectorValidationResult(
      ConnectorValidationResult connectorValidationResult) {
    EntityActivityCreateDTO.ConnectorValidationResultProto.Builder connectorValidationResultProto =
        newBuilder()
            .setStatus(connectorValidationResult.getStatus().toString())
            .setTestedAt(connectorValidationResult.getTestedAt());
    if (isNotEmpty(connectorValidationResult.getDelegateId())) {
      connectorValidationResultProto.setDelegateId(connectorValidationResult.getDelegateId());
    }
    if (isNotEmpty(connectorValidationResult.getErrorSummary())) {
      connectorValidationResultProto.setErrorSummary(connectorValidationResult.getErrorSummary());
    }
    if (connectorValidationResult.getErrors() != null) {
      connectorValidationResultProto.addAllErrors(getErrorDetailsProto(connectorValidationResult.getErrors()));
    }
    return connectorValidationResultProto.build();
  }

  private List<EntityActivityCreateDTO.ErrorDetailProto> getErrorDetailsProto(List<ErrorDetail> errors) {
    if (isEmpty(errors)) {
      return Collections.emptyList();
    }
    return errors.stream().map(this::createErrorProtoDetail).collect(Collectors.toList());
  }

  private EntityActivityCreateDTO.ErrorDetailProto createErrorProtoDetail(ErrorDetail error) {
    if (error == null) {
      return null;
    }
    EntityActivityCreateDTO.ErrorDetailProto.Builder builder =
        EntityActivityCreateDTO.ErrorDetailProto.newBuilder().setCode(error.getCode());
    if (isNotEmpty(error.getMessage())) {
      builder.setMessage(error.getMessage());
    }
    if (isNotEmpty(error.getReason())) {
      builder.setReason(error.getReason());
    }
    return builder.build();
  }
}
