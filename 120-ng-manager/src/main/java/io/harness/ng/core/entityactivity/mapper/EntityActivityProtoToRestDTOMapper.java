/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityactivity.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.ActivityDetail;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entityusageactivity.EntityUsageEventDetailProtoTORestMapper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class EntityActivityProtoToRestDTOMapper {
  EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  EntityUsageEventDetailProtoTORestMapper entityUsageEventDetailProtoTORestMapper;

  public NGActivityDTO toRestDTO(EntityActivityCreateDTO entityActivityProtoDTO) {
    return NGActivityDTO.builder()
        .accountIdentifier(entityActivityProtoDTO.getAccountIdentifier())
        .type(NGActivityType.valueOf(entityActivityProtoDTO.getType()))
        .activityStatus(NGActivityStatus.valueOf(entityActivityProtoDTO.getStatus()))
        .activityTime(entityActivityProtoDTO.getActivityTime())
        .description(entityActivityProtoDTO.getDescription())
        .referredEntity(entityDetailProtoToRestMapper.createEntityDetailDTO(entityActivityProtoDTO.getReferredEntity()))
        .detail(createEntityActivityDetailDTO(entityActivityProtoDTO.getType(), entityActivityProtoDTO))
        .build();
  }

  private ActivityDetail createEntityActivityDetailDTO(String type, EntityActivityCreateDTO entityActivityProtoDTO) {
    NGActivityType activityType = NGActivityType.valueOf(type);
    switch (activityType) {
      case CONNECTIVITY_CHECK:
        return ConnectivityCheckActivityDetailDTO.builder()
            .connectorValidationResult(
                createConnectivityDetailFromProtoDTOs(entityActivityProtoDTO.getConnectivityDetail()))
            .build();
      case ENTITY_USAGE:
        EntityActivityCreateDTO.EntityUsageActivityDetailProtoDTO entityUsageActivityDetailProtoDTO =
            entityActivityProtoDTO.getEntityUsageDetail();
        return EntityUsageActivityDetailDTO.builder()
            .referredByEntity(entityDetailProtoToRestMapper.createEntityDetailDTO(
                entityUsageActivityDetailProtoDTO.getReferredByEntity()))
            .activityStatusMessage(entityUsageActivityDetailProtoDTO.getActivityStatusMessage())
            .errors(getErrorsList(entityUsageActivityDetailProtoDTO.getErrorsList()))
            .errorSummary(entityUsageActivityDetailProtoDTO.getErrorSummary())
            .status(getConnecitivityStatus(entityUsageActivityDetailProtoDTO.getStatus()))
            .usageDetail(entityUsageEventDetailProtoTORestMapper.getEventDetail(
                entityUsageActivityDetailProtoDTO.getUsageDetail()))
            .build();
      default:
        return null;
    }
  }

  private ConnectorValidationResult createConnectivityDetailFromProtoDTOs(
      EntityActivityCreateDTO.ConnectivityCheckActivityDetailProtoDTO connectivityDetail) {
    if (connectivityDetail == null || !connectivityDetail.hasConnectorValidationResult()) {
      return null;
    }
    return createConnectorValidationResultFromProto(connectivityDetail.getConnectorValidationResult());
  }

  private ConnectorValidationResult createConnectorValidationResultFromProto(
      EntityActivityCreateDTO.ConnectorValidationResultProto connectorValidationResult) {
    return ConnectorValidationResult.builder()
        .delegateId(connectorValidationResult.getDelegateId())
        .errorSummary(connectorValidationResult.getErrorSummary())
        .status(getConnecitivityStatus(connectorValidationResult.getStatus()))
        .errors(getErrorsList(connectorValidationResult.getErrorsList()))
        .errorSummary(connectorValidationResult.getErrorSummary())
        .build();
  }

  private ConnectivityStatus getConnecitivityStatus(String status) {
    return ConnectivityStatus.valueOf(status);
  }

  private List<ErrorDetail> getErrorsList(List<EntityActivityCreateDTO.ErrorDetailProto> errorsProtoDTOs) {
    if (isEmpty(errorsProtoDTOs)) {
      return Collections.emptyList();
    }
    return errorsProtoDTOs.stream().map(this::createErrorDetail).collect(Collectors.toList());
  }

  private ErrorDetail createErrorDetail(EntityActivityCreateDTO.ErrorDetailProto error) {
    return ErrorDetail.builder().code(error.getCode()).reason(error.getReason()).message(error.getMessage()).build();
  }
}
