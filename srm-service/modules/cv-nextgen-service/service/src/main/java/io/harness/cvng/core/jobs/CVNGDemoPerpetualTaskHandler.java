/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask;
import io.harness.cvng.core.services.api.demo.CVNGDemoPerpetualTaskService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;

@Singleton
public class CVNGDemoPerpetualTaskHandler extends SafeHandler<CVNGDemoPerpetualTask> {
  @Inject private CVNGDemoPerpetualTaskService cvngDemoPerpetualTaskService;

  @SneakyThrows
  @Override
  public void handleUnsafely(CVNGDemoPerpetualTask cvngDemoPerpetualTask) {
    cvngDemoPerpetualTaskService.execute(cvngDemoPerpetualTask);
  }
}
