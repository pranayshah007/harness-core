/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import io.harness.data.structure.NullSafeImmutableMap;

import java.util.Map;

public class NgTriggerAutoLogContext extends AutoLogContext {
  public static final String ACCOUNT_KEY = "accountIdentifier";
  public static final String ORG_KEY = "orgIdentifier";
  public static final String PROJECT_KEY = "projectIdentifier";
  public static final String TRIGGER_KEY = "triggerIdentifier";
  public static final String PIPELINE_KEY = "pipelineIdentifier";
  public static final String EVENT_ID = "eventId";
  public static final String WEBHOOK_ID = "webhookId";

  private static Map<String, String> getContext(String fieldName, String fieldValue, String triggerIdentifier,
      String pipelineIdentifier, String projectIdentifier, String orgIdentifier, String accountIdentifier) {
    NullSafeImmutableMap.NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();
    nullSafeBuilder.putIfNotNull(fieldName, fieldValue);
    nullSafeBuilder.putIfNotNull(TRIGGER_KEY, triggerIdentifier);
    nullSafeBuilder.putIfNotNull(PIPELINE_KEY, pipelineIdentifier);
    nullSafeBuilder.putIfNotNull(PROJECT_KEY, projectIdentifier);
    nullSafeBuilder.putIfNotNull(ORG_KEY, orgIdentifier);
    nullSafeBuilder.putIfNotNull(ACCOUNT_KEY, accountIdentifier);
    return nullSafeBuilder.build();
  }

  private static Map<String, String> getContext(String fieldName, String fieldValue, String accountIdentifier) {
    NullSafeImmutableMap.NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();
    nullSafeBuilder.putIfNotNull(fieldName, fieldValue);
    nullSafeBuilder.putIfNotNull(ACCOUNT_KEY, accountIdentifier);
    return nullSafeBuilder.build();
  }

  public NgTriggerAutoLogContext(String fieldName, String fieldValue, String triggerIdentifier,
      String pipelineIdentifier, String projectIdentifier, String orgIdentifier, String accountIdentifier,
      OverrideBehavior behavior) {
    super(getContext(fieldName, fieldValue, triggerIdentifier, pipelineIdentifier, projectIdentifier, orgIdentifier,
              accountIdentifier),
        behavior);
  }

  public NgTriggerAutoLogContext(
      String fieldName, String fieldValue, String accountIdentifier, OverrideBehavior behavior) {
    super(getContext(fieldName, fieldValue, accountIdentifier), behavior);
  }
}
