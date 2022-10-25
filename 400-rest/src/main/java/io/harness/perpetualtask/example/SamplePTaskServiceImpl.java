/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.example;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.perpetualtask.example.SamplePerpetualTaskServiceClient.COUNTRY_NAME;

import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;

import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SamplePTaskServiceImpl implements SamplePTaskService {
  @Inject private AccountService accountService;
  @Inject private PerpetualTaskService perpetualTaskService;

  private final ConcurrentMap<String, Integer> countryMap = new ConcurrentHashMap<>();

  @Override
  public String create(String accountId, String countryName, int population) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Creating a Sample Perpetual Task.");
      countryMap.putIfAbsent(countryName, population);
      Map<String, String> clientParamMap = new HashMap<>();
      clientParamMap.put(COUNTRY_NAME, countryName);

      PerpetualTaskClientContext clientContext =
          PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

      PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                           .setInterval(Durations.fromMinutes(1))
                                           .setTimeout(Durations.fromSeconds(30))
                                           .build();
      return perpetualTaskService.createTask(
          PerpetualTaskType.SAMPLE, accountId, clientContext, schedule, false, "taskDescription");
    }
  }

  @Override
  public boolean update(String accountId, String taskId, String countryName, int population) {
    countryMap.put(countryName, population);
    perpetualTaskService.resetTask(accountId, taskId, null);
    return true;
  }

  @Override
  public int getPopulation(String countryName) {
    return countryMap.getOrDefault(countryName, ThreadLocalRandom.current().nextInt(10, 1000));
  }
}
