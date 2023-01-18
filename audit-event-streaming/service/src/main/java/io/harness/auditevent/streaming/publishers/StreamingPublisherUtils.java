/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.publishers;

import static io.harness.auditevent.streaming.AuditEventStreamingConstants.AWS_S3_STREAMING_PUBLISHER;

import io.harness.exception.UnknownEnumTypeException;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StreamingPublisherUtils {
  public static StreamingPublisher getStreamingPublisher(
      TypeEnum type, Map<String, StreamingPublisher> streamingPublishers) {
    switch (type) {
      case AWS_S3:
        return streamingPublishers.get(AWS_S3_STREAMING_PUBLISHER);
      default:
        throw new UnknownEnumTypeException("No publisher", type.name());
    }
  }
}
