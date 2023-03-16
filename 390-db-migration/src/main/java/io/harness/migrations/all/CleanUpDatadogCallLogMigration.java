/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.migrations.Migration;
import io.harness.time.Timestamp;

import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.ThirdPartyApiCallLog;
import software.wings.service.impl.ThirdPartyApiCallLog.ThirdPartyApiCallLogKeys;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class CleanUpDatadogCallLogMigration implements Migration {
  private static final String API_KEY = "api_key";

  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try {
      List<StateExecutionInstance> stateExecutionInstances =
          wingsPersistence.createQuery(StateExecutionInstance.class)
              .filter(StateExecutionInstanceKeys.stateType, "DATA_DOG")
              .field(StateExecutionInstanceKeys.createdAt)
              .greaterThanOrEq(Timestamp.currentMinuteBoundary() - TimeUnit.DAYS.toMillis(30))
              .asList();

      log.info("Fixing the 3P calllogs for {} datadog executions", stateExecutionInstances.size());
      for (StateExecutionInstance instance : stateExecutionInstances) {
        List<ThirdPartyApiCallLog> callLogList = wingsPersistence.createQuery(ThirdPartyApiCallLog.class)
                                                     .field("stateExecutionId")
                                                     .equal(instance.getUuid())
                                                     .asList();

        for (ThirdPartyApiCallLog logObject : callLogList) {
          if (logObject.getTitle().contains(API_KEY)) {
            logObject.setTitle(replaceApiAndAppKey(logObject.getTitle()));
          }
          for (ThirdPartyApiCallField callLogFields : logObject.getRequest()) {
            if (callLogFields.getValue().contains(API_KEY)) {
              callLogFields.setValue(replaceApiAndAppKey(callLogFields.getValue()));
            }
          }
          UpdateOperations<ThirdPartyApiCallLog> op =
              wingsPersistence.createUpdateOperations(ThirdPartyApiCallLog.class);
          setUnset(op, "request", logObject.getRequest());
          setUnset(op, "title", logObject.getTitle());
          wingsPersistence.update(
              wingsPersistence.createQuery(ThirdPartyApiCallLog.class)
                  .filter(ThirdPartyApiCallLogKeys.stateExecutionId, logObject.getStateExecutionId()),
              op);
        }
      }

    } catch (RuntimeException ex) {
      log.error("Exception while migrating thirdpartycallLogs for Datadog", ex);
    }
  }

  private String replaceApiAndAppKey(String badUrl) {
    String returnValue = badUrl;
    final String datadog_api_mask = "api_key=([^&]*)&application_key=([^&]*)&";
    Pattern batchPattern = Pattern.compile(datadog_api_mask);
    Matcher matcher = batchPattern.matcher(badUrl);
    while (matcher.find()) {
      final String apiKey = matcher.group(1);
      final String appKey = matcher.group(2);
      returnValue = badUrl.replace(apiKey, "<apiKeyPlaceholder>");
      returnValue = returnValue.replace(appKey, "<appKeyPlaceholder>");
    }
    return returnValue;
  }
}
