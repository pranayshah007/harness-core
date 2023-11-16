/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDP)
public class TrafficRoutingRule {
  Rule rule;

  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Rule {
    RuleType type;
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    TrafficRoutingRuleSpec spec;

    @AllArgsConstructor
    public enum RuleType {
      URI(TrafficRoutingConst.URI),
      SCHEME(TrafficRoutingConst.SCHEME),
      METHOD(TrafficRoutingConst.METHOD),
      AUTHORITY(TrafficRoutingConst.AUTHORITY),
      HEADER(TrafficRoutingConst.HEADER),
      PORT(TrafficRoutingConst.PORT);

      @JsonValue @Getter private final String displayName;
    }
  }
}
