/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
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
public class Route {
  @NotNull RouteSpec route;

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class RouteSpec {
    @NotNull RouteSpec.RouteType type;
    @NotEmpty List<TrafficRoutingRule> rules;

    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    enum RouteType {
      HTTP(TrafficRoutingConst.HTTP);
      @JsonValue @Getter final String displayName;
    }
  }
}
