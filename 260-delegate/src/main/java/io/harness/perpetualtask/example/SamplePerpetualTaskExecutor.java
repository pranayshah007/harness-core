/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.example;

import static java.lang.Thread.sleep;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.grpc.utils.AnyUtils;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class SamplePerpetualTaskExecutor implements PerpetualTaskExecutor {
  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    SamplePerpetualTaskParams sampleParams =
        AnyUtils.unpack(params.getCustomizedParams(), SamplePerpetualTaskParams.class);
    try {
      sleep(10000);
    } catch (InterruptedException e) {
      log.error("The Sample PTask is interrupted", e);
      Thread.currentThread().interrupt();
    }
    log.info("The country {} has a population of {}", sampleParams.getCountry(), sampleParams.getPopulation());
    return PerpetualTaskResponse.builder()
        .responseCode(ThreadLocalRandom.current().nextInt(1, 200))
        .responseMessage("success")
        .build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return true;
  }
}
