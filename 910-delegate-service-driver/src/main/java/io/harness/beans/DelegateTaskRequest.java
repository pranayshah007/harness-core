/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.delegate.task.TaskParameters;

import software.wings.beans.SerializationFormat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class DelegateTaskRequest {
  boolean parked;
  String taskType;

  TaskParameters taskParameters;
  String accountId;
  @Singular Map<String, String> taskSetupAbstractions;
  @Singular List<String> taskSelectors;
  Duration executionTimeout;
  String taskDescription;
  LinkedHashMap<String, String> logStreamingAbstractions;
  boolean forceExecute;
  int expressionFunctorToken;
  List<String> eligibleToExecuteDelegateIds;
  @Builder.Default SerializationFormat serializationFormat = SerializationFormat.KRYO;

  // Used for harness hosted delegates
  private boolean executeOnHarnessHostedDelegates;

  private boolean emitEvent;
  private String stageId;
  private String baseLogKey;
  private boolean shouldSkipOpenStream;
}
