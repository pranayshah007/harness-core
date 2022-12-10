/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.freeze.beans;

import io.harness.notification.channelDetails.PmsNotificationChannel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FreezeNotificationChannelWrapper {
  String type;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
  @JsonProperty("spec")
  PmsNotificationChannel notificationChannel;

  @Builder
  public FreezeNotificationChannelWrapper(String type, PmsNotificationChannel notificationChannel) {
    this.type = type;
    this.notificationChannel = notificationChannel;
  }
}
