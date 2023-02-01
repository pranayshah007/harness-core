/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.mapper.streaming;

import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;

import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;

public class StreamingDestinationMapper {
  public StreamingDestination toStreamingDestinationEntity(
      String accountIdentifier, StreamingDestinationDTO streamingDestinationDTO) {
    StreamingDestination streamingDestination = getStreamingDestination(streamingDestinationDTO.getSpec());
    streamingDestination.setAccountIdentifier(accountIdentifier);
    streamingDestination.setIdentifier(streamingDestinationDTO.getIdentifier());
    streamingDestination.setName(streamingDestinationDTO.getName());
    streamingDestination.setDescription(streamingDestinationDTO.getDescription());
    streamingDestination.setTags(convertToList(streamingDestinationDTO.getTags()));
    streamingDestination.setStatus(streamingDestinationDTO.getStatus());
    streamingDestination.setConnectorRef(streamingDestinationDTO.getConnectorRef());
    streamingDestination.setType(streamingDestinationDTO.getSpec().getType());
    streamingDestination.setLastStatusChangedAt(System.currentTimeMillis());
    return streamingDestination;
  }

  private StreamingDestinationSpecDTO getSpec(StreamingDestination streamingDestination) {
    if (AWS_S3.equals(streamingDestination.getType())) {
      return new AwsS3StreamingDestinationSpecDTO()
          .bucket(((AwsS3StreamingDestination) streamingDestination).getBucket())
          .type(AWS_S3);
    } else
      throw new UnknownEnumTypeException("Streaming Destination type", streamingDestination.getType().name());
  }

  public StreamingDestinationDTO toDTO(StreamingDestination streamingDestination) {
    return new StreamingDestinationDTO()
        .identifier(streamingDestination.getIdentifier())
        .name(streamingDestination.getName())
        .description(streamingDestination.getDescription())
        .tags(convertToMap(streamingDestination.getTags()))
        .status(streamingDestination.getStatus())
        .connectorRef(streamingDestination.getConnectorRef())
        .spec(getSpec(streamingDestination));
  }

  private StreamingDestination getStreamingDestination(StreamingDestinationSpecDTO streamingDestinationSpecDTO) {
    switch (streamingDestinationSpecDTO.getType()) {
      case AWS_S3:
        return AwsS3StreamingDestinationSpecDTOToEntityMapper.toStreamingDestination(
            (AwsS3StreamingDestinationSpecDTO) streamingDestinationSpecDTO);
      default:
        throw new UnknownEnumTypeException("Streaming Destination type", streamingDestinationSpecDTO.getType().name());
    }
  }
}
