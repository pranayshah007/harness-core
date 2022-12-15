/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.beans.SortOrder;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.beans.PageRequest;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;
import io.harness.spec.server.audit.v1.model.StreamingDestinationResponse;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;
import io.harness.utils.PageUtils;

import java.util.List;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.PL)
public class StreamingDestinationsApiUtils {
  public StreamingDestinationFilterProperties getFilterProperties(String searchTerm, String status) {
    return StreamingDestinationFilterProperties.builder().searchTerm(searchTerm).status(getStatusEnum(status)).build();
  }

  private StatusEnum getStatusEnum(String status) {
    if (isEmpty(status)) {
      return null;
    }
    try {
      return StatusEnum.valueOf(status);
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("Streaming Destination status", status);
    }
  }

  public Pageable getPageRequest(int page, int limit, String sort, String order) {
    List<SortOrder> sortOrders;
    String fieldName = getFieldName(sort);
    if (fieldName != null) {
      SortOrder.OrderType orderType = EnumUtils.getEnum(SortOrder.OrderType.class, order, DESC);
      sortOrders = List.of(aSortOrder().withField(fieldName, orderType).build());
    } else {
      sortOrders = List.of(aSortOrder().withField(StreamingDestinationKeys.lastModifiedDate, DESC).build());
    }
    return PageUtils.getPageRequest(new PageRequest(page, limit, sortOrders));
  }

  public StreamingDestinationResponse getStreamingDestinationResponse(StreamingDestination streamingDestination) {
    StreamingDestinationResponse streamingDestinationResponse = new StreamingDestinationResponse();
    StreamingDestinationDTO streamingDestinationDTO = new StreamingDestinationDTO();
    streamingDestinationDTO.slug(streamingDestination.getIdentifier())
        .name(streamingDestination.getName())
        .status(streamingDestination.getStatus())
        .connectorRef(streamingDestination.getConnectorRef())
        .spec(getStreamingDestinationSpecDTO(streamingDestination));
    return streamingDestinationResponse.streamingDestination(streamingDestinationDTO)
        .created(streamingDestination.getCreatedAt())
        .updated(streamingDestination.getLastModifiedDate());
  }

  private StreamingDestinationSpecDTO getStreamingDestinationSpecDTO(StreamingDestination streamingDestination) {
    switch (streamingDestination.getType()) {
      case AWS_S3:
        return getAwsS3StreamingDestinationSpecDTO(streamingDestination).type(AWS_S3);
      default:
        throw new UnknownEnumTypeException("Streaming Destination type", streamingDestination.getType().name());
    }
  }

  private AwsS3StreamingDestinationSpecDTO getAwsS3StreamingDestinationSpecDTO(
      StreamingDestination streamingDestination) {
    AwsS3StreamingDestination awsS3StreamingDestination = (AwsS3StreamingDestination) streamingDestination;
    return new AwsS3StreamingDestinationSpecDTO().bucket(awsS3StreamingDestination.getBucket());
  }

  private String getFieldName(String sort) {
    String fieldName;
    PageUtils.SortFields sortField = PageUtils.SortFields.fromValue(sort);
    if (sortField == null) {
      sortField = PageUtils.SortFields.UNSUPPORTED;
    }
    switch (sortField) {
      case SLUG:
        fieldName = StreamingDestinationKeys.identifier;
        break;
      case NAME:
        fieldName = StreamingDestinationKeys.name;
        break;
      case CREATED:
        fieldName = StreamingDestinationKeys.createdAt;
        break;
      case UPDATED:
        fieldName = StreamingDestinationKeys.lastModifiedDate;
        break;
      case UNSUPPORTED:
      default:
        fieldName = null;
    }
    return fieldName;
  }
}
