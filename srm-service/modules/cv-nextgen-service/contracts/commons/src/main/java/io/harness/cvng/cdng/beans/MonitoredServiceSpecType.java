/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import java.util.Arrays;
import lombok.Getter;

public enum MonitoredServiceSpecType {
  DEFAULT("Default"),
  CONFIGURED("Configured"),
  TEMPLATE("Template");

  @Getter public final String name;

  MonitoredServiceSpecType(String name) {
    this.name = name;
  }

  public static MonitoredServiceSpecType getByName(String name) {
    return Arrays.stream(values())
        .filter(spec -> spec.name.equals(name))
        .findFirst()
        .orElseThrow(
            ()
                -> new IllegalArgumentException(
                    "No enum constant of type io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType for name "
                    + name));
  }
}
