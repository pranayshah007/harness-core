/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.managerConfiguration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.beans.ManagerConfiguration.MATCH_ALL_VERSION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.queue.QueueController;

import com.google.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PL)
public class ConfigurationController implements QueueController {
  private final AtomicBoolean primary = new AtomicBoolean(true);
  private final AtomicReference<String> primaryVersion = new AtomicReference<>(MATCH_ALL_VERSION);

  public ConfigurationController() {}

  @Override
  public boolean isPrimary() {
    return primary.get();
  }

  @Override
  public boolean isNotPrimary() {
    return !primary.get();
  }

  public String getPrimaryVersion() {
    return primaryVersion.get();
  }
}
