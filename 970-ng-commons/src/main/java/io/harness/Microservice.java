/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Microservice {
  @JsonProperty("CD") CD,
  @JsonProperty("CI") CI,
  @JsonProperty("CORE") CORE,
  @JsonProperty("CV") CV,
  @JsonProperty("CF") CF,
  @JsonProperty("CE") CE,
  @JsonProperty("PMS") PMS,
  @JsonProperty("IDP") IDP,
  @JsonProperty("ACCESSCONTROL") ACCESSCONTROL,
  @JsonProperty("TEMPLATESERVICE") TEMPLATESERVICE,
  @JsonProperty("RESOURCEGROUP") RESOURCEGROUP;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static Microservice fromString(String microservice) {
    for (Microservice msvc : Microservice.values()) {
      if (msvc.name().equalsIgnoreCase(microservice)) {
        return msvc;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + microservice);
  }
}
