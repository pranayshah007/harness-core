/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfcli;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public enum CfCliCommandType {
  VERSION(StringUtils.EMPTY),
  LOGIN("login"),
  API("api"),
  AUTH("auth"),
  TARGET("target"),
  LOGS("logs"),
  UNMAP_ROUTE("unmap-route"),
  MAP_ROUTE("map-route"),
  SET_ENV("set-env"),
  UNSET_ENV("unset-env"),
  CONFIGURE_AUTOSCALING("configure-autoscaling"),
  DISABLE_AUTOSCALING("disable-autoscaling"),
  ENABLE_AUTOSCALING("enable-autoscaling"),
  AUTOSCALING_APPS("autoscaling-apps"),
  PUSH("push"),
  START("start"),
  PLUGINS("plugins");

  CfCliCommandType(String value) {
    this.value = value;
  }

  private final String value;

  public static CfCliCommandType fromString(final String value) {
    for (CfCliCommandType type : CfCliCommandType.values()) {
      if (type.toString().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format("Unrecognized CF CLI command, value: %s,", value));
  }

  @Override
  public String toString() {
    return this.value;
  }
}
