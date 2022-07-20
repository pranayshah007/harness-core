/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.async;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.protobuf.Message;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.PIPELINE)
@SuperBuilder
public abstract class AsyncResponseCallback<T extends Message> implements OldNotifyCallback {
  private static final int MAX_DEPTH = 10;
  private static final Duration MAX_TIME = Duration.ofMinutes(2);

  public Integer depth;

  @Inject WaitNotifyEngine waitNotifyEngine;
  public T finalResponse;

  /**
   * We allow only Max_depth iteration to resolve the dependency if it goes beyond that then we should throw an
   * exception. This can be overridden for different different handling.
   */
  public abstract void handleMaxDepthExceeded();

  /**
   * Used to handle the responses of various services result which we have got from them
   */
  public abstract T handleResponseData(Map<String, ResponseData> responseData);

  /**
   * This should publish an event to handle the unresolved dependency if any
   * @return waitIds
   */
  public abstract List<String> handleUnresolvedDependencies();

  /**
   * Used to merge two responses
   */
  public abstract T mergeResponses(T finalResponse, T interimResponse);

  @Override
  public void notify(Map<String, ResponseData> responseDataMap) {
    if (depth > MAX_DEPTH) {
      handleMaxDepthExceeded();
      return;
    }

    T response = handleResponseData(responseDataMap);
    finalResponse = mergeResponses(finalResponse, response);
    if (hasErrorResponse(finalResponse)) {
      handleError(finalResponse);
      return;
    }

    if (hasUnresolvedDependency()) {
      List<String> waitIds = handleUnresolvedDependencies();
      if (!waitIds.isEmpty()) {
        waitNotifyEngine.waitForAllOnInList(getPublisherName(), clone(), waitIds, MAX_TIME);
      }
    } else {
      finalizeCreation();
    }
  }

  public abstract void finalizeCreation();
  public abstract boolean hasUnresolvedDependency();
  public abstract boolean hasErrorResponse(T finalResponse);
  public abstract boolean handleError(T finalResponse);
  public abstract String getPublisherName();
  public abstract OldNotifyCallback clone();
}
