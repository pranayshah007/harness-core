/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.mapper.streaming;

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
    streamingDestination.setIdentifier(streamingDestinationDTO.getSlug());
    streamingDestination.setName(streamingDestinationDTO.getName());
    streamingDestination.setStatus(streamingDestinationDTO.getStatus());
    streamingDestination.setConnectorRef(streamingDestinationDTO.getConnectorRef());
    streamingDestination.setType(streamingDestinationDTO.getSpec().getType());
    return streamingDestination;
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
