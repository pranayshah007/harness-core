/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineTelemetryHelper {
  @Inject TelemetryReporter telemetryReporter;
  @Inject AccountService accountService;
  @Inject @Named("TelemetrySenderExecutor") Executor executor;

  public void sendTelemetryEventWithAccountName(
      String eventName, String accountId, HashMap<String, Object> properties) {
    executor.execute(() -> sendTelemetryEventInternal(eventName, accountId, properties));
  }

  protected void sendTelemetryEventInternal(String eventName, String accountId, HashMap<String, Object> properties) {
    AccountDTO accountDTO = accountService.getAccount(accountId);
    String accountName = accountDTO.getName();
    properties.put(ACCOUNT_NAME, accountName);
    telemetryReporter.sendTrackEvent(eventName, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true),
        Category.GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
  }
}
