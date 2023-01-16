/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.moduleversioninfo.runnable;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class ModuleVersionsMaintenanceTask implements Runnable {
  @Inject private UpdateVersionInfoTask updateVersionInfoTask;

  @Override
  public void run() {
    try {
      updateVersionInfoTask.run();
    } catch (RuntimeException e) {
      log.error("Update VersionInfo Task Sync Job unexpectedly stopped", e);
    } catch (InterruptedException e) {
      log.error("Update VersionInfo Task Sync job interrupted", e);
      Thread.currentThread().interrupt();
    } finally {
      log.info("Update VersionInfo Task sync job has stopped");
    }
  }
}
