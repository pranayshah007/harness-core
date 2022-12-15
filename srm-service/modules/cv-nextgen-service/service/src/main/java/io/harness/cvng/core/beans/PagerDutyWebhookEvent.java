/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagerDutyWebhookEvent {
  PagerDutyWebhookEventDTO event;

  @Value
  @Builder
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PagerDutyWebhookEventDTO {
    @JsonProperty("event_type") String eventType;
    @JsonProperty("occurred_at") Instant triggeredAt;
    PagerDutyIncidentDTO data;
  }

  @Value
  @Builder
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PagerDutyIncidentDTO {
    String id;
    String self;
    String title;
    String status;
    String urgency;
    @JsonProperty("html_url") String htmlUrl;
    PagerDutyObject priority;
    List<PagerDutyObject> assignees;
    @JsonProperty("escalation_policy") PagerDutyObject escalationPolicy;
  }

  @Value
  @Builder
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PagerDutyObject {
    String id;
    String summary;
    @JsonProperty("html_url") String htmlUrl;
  }
}
