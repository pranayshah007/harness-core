/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.STREAMING_DESTINATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class StreamingDestinationCreateEvent implements Event {
  public static final String STREAMING_DESTINATION_CREATE_EVENT = "StreamingDestinationCreated";
  String accountIdentifier;
  StreamingDestinationDTO streamingDestination;

  public StreamingDestinationCreateEvent(String accountIdentifier, StreamingDestinationDTO streamingDestination) {
    this.accountIdentifier = accountIdentifier;
    this.streamingDestination = streamingDestination;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, streamingDestination.getName());
    return Resource.builder()
        .identifier(streamingDestination.getIdentifier())
        .type(STREAMING_DESTINATION)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return STREAMING_DESTINATION_CREATE_EVENT;
  }
}
