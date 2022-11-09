/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task;

import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class SimpleHDelegateTask implements HDelegateTask {
  @NonNull String accountId;
  TaskData data;
  TaskDataV2 taskDataV2;
  @Singular Map<String, String> setupAbstractions;
  String uuid;
  LinkedHashMap<String, String> logStreamingAbstractions;
}
