/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class DelegateServiceApplication extends Application<io.harness.delegate.app.DelegateServiceConfig> {
  public static void main(String... args) throws Exception {
    new DelegateServiceApplication().run(args);
  }

  @Override
  public void run(io.harness.delegate.app.DelegateServiceConfig delegateServiceConfig, Environment environment) throws Exception {
    log.info("Starting Delegate Service App");
  }
}
