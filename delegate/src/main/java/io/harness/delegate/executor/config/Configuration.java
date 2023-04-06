/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.config;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Configuration {
  @Builder.Default private String taskInputPath = "/etc/config/taskfile";
  @Builder.Default
  private String delegateToken = "2f6b0988b6fb3370073c3d0505baee58" /*System.getenv(Env.DELEGATE_TOKEN.name())*/;
  /**
   * set to false when testing locally without delegate core
   */
  @Builder.Default private boolean shouldSendResponse = false;
}
