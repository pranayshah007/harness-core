/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk.beans;

import lombok.Builder;
import lombok.Data;

@Data
public class ContainerResourceConfig {
  Limits requests;
  Limits limits;

  @Builder
  public ContainerResourceConfig(Limits requests, Limits limits) {
    this.requests = requests;
    this.limits = limits;
  }

  @Data
  public static class Limits {
    String memory;
    String cpu;

    @Builder
    public Limits(String memory, String cpu) {
      this.memory = memory;
      this.cpu = cpu;
    }
  }
}
