/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.mappers;

import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;

import org.springframework.stereotype.Service;

@Service
public class StreamingDestinationMapper {
  public StreamingDestinationDTO toStreamingDestinationDTO(StreamingDestination streamingDestination) {
    return new StreamingDestinationDTO()
        .identifier(streamingDestination.getIdentifier())
        .name(streamingDestination.getName())
        .status(streamingDestination.getStatus())
        .connectorRef(streamingDestination.getConnectorRef())
        .spec(getSpec(streamingDestination));
  }

  private StreamingDestinationSpecDTO getSpec(StreamingDestination streamingDestination) {
    switch (streamingDestination.getType()) {
      case AWS_S3:
        return new AwsS3StreamingDestinationSpecDTO()
            .bucket(((AwsS3StreamingDestination) streamingDestination).getBucket())
            .type(streamingDestination.getType());
      default:
        throw new UnknownEnumTypeException("Not supported", streamingDestination.getType().name());
    }
  }
}
