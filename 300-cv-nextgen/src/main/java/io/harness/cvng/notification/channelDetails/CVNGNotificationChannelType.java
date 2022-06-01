/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.channelDetails;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

@AllArgsConstructor
public enum CVNGNotificationChannelType {
  @JsonProperty("Email") EMAIL("Email"),
  @JsonProperty("Slack") SLACK("Slack"),
  @JsonProperty("Pagerduty") PAGERDUTY("Pagerduty"),
  @JsonProperty("Msteams") MSTEAMS("Msteams");

  @Getter private String templateSuffixIdentifier;

  private static Map<String, CVNGNotificationChannelType> STRING_TO_TYPE_MAP;

  public static CVNGNotificationChannelType fromString(String stringValue) {
    if (MapUtils.isEmpty(STRING_TO_TYPE_MAP)) {
      STRING_TO_TYPE_MAP =
          Arrays.stream(CVNGNotificationChannelType.values())
              .collect(Collectors.toMap(CVNGNotificationChannelType::getTemplateSuffixIdentifier, Function.identity()));
    }
    if (!STRING_TO_TYPE_MAP.containsKey(stringValue)) {
      throw new IllegalArgumentException("CVNGNotificationChannelType should be in : " + STRING_TO_TYPE_MAP.keySet());
    }
    return STRING_TO_TYPE_MAP.get(stringValue);
  }
}
