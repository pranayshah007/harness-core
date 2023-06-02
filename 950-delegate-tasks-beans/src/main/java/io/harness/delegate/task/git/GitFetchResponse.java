/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.git.model.FetchFilesResult;

import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
public class GitFetchResponse implements DelegateTaskNotifyResponseData {
  Map<String, FetchFilesResult> filesFromMultipleRepo;
  TaskStatus taskStatus;
  String errorMessage;
  UnitProgressData unitProgressData;
  Map<String, String> fetchedCommitIdsMap;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
}
