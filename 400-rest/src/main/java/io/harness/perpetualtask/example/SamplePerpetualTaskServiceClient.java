/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.example;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskServiceClient;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SamplePerpetualTaskServiceClient implements PerpetualTaskServiceClient {
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private SamplePTaskService samplePTaskService;
  static final String COUNTRY_NAME = "countryName";

  @Override
  public SamplePerpetualTaskParams getTaskParams(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    final String countryName = clientParams.get(COUNTRY_NAME);
    int population = samplePTaskService.getPopulation(countryName);
    log.info("Country = [{}], population = [{}]", countryName, population);
    return SamplePerpetualTaskParams.newBuilder().setCountry(countryName).setPopulation(population).build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext context, String accountId) {
    return DelegateTask.builder()
        .accountId(accountId)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HTTP.name())
                  .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                  .timeout(TimeUnit.MINUTES.toMillis(1))
                  .build())
        .build();
  }
}
