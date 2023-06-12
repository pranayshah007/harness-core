/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomChangeWebhookPayload {
  String eventIdentifier;
  @NotNull Long startTime;
  @NotNull Long endTime;
  @NotNull @NotEmpty String user;
  @NotNull CustomChangeWebhookEventDetail eventDetail;
  @Value
  @Builder
  public static class CustomChangeWebhookEventDetail {
    @NotNull @NotEmpty String description;
    String changeEventDetailsLink;
    String externalLinkToEntity;
    @NotNull @NotEmpty String name;
    String channelUrl;
  }
}
