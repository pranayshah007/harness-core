/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(PL)
public class NotificationProcessingResponse {
  public static final NotificationProcessingResponse trivialResponseWithRetries =
      new NotificationProcessingResponse(Collections.emptyList(), true);
  public static final NotificationProcessingResponse trivialResponseWithNoRetries =
      new NotificationProcessingResponse(Collections.emptyList(), false);
  public static NotificationProcessingResponse allSent(int nrecipients) {
    return NotificationProcessingResponse.builder()
        .result(new ArrayList<>(Collections.nCopies(nrecipients, true)))
        .build();
  }
  public static NotificationProcessingResponse nonSent(int nrecipients) {
    return NotificationProcessingResponse.builder()
        .result(new ArrayList<>(Collections.nCopies(nrecipients, false)))
        .shouldRetry(true)
        .build();
  }

  public static boolean isNotificationRequestFailed(NotificationProcessingResponse response) {
    if (Objects.isNull(response)) {
      return true;
    }
    return !response.getResult().stream().reduce(false, (e1, e2) -> e1 || e2);
  }

  public static boolean isNotificationRequestFailed(List<Boolean> response) {
    if (response.isEmpty()) {
      return true;
    }
    return !response.stream().reduce(false, (e1, e2) -> e1 || e2);
  }

  List<Boolean> result;
  boolean shouldRetry;
}
