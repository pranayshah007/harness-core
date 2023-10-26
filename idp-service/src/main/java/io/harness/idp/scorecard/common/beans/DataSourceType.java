/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum DataSourceType {
  @JsonProperty("Http") HTTP("Http"),
  @JsonProperty("Noop") NO_OP("Noop");

  @Getter private final String type;

  public static DataSourceType fromString(String stringValue) {
    for (DataSourceType dsType : DataSourceType.values()) {
      if (dsType.type.equalsIgnoreCase(stringValue)) {
        return dsType;
      }
    }
    return null;
  }
}
