/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.isSuccessResponse;

import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TriggerEventStatusHelper {
  public TriggerEventStatus toStatus(TriggerEventResponse.FinalStatus finalStatus) {
    if (!isSuccessResponse(finalStatus)) {
      if (finalStatus != null) {
        return TriggerEventStatus.builder()
            .status(TriggerEventStatus.FinalResponse.FAILURE)
            .message(finalStatus.name())
            .build();
      } else {
        return TriggerEventStatus.builder()
            .status(TriggerEventStatus.FinalResponse.FAILURE)
            .message("UNKNOWN_STATUS")
            .build();
      }
    }
    return TriggerEventStatus.builder()
        .status(TriggerEventStatus.FinalResponse.SUCCESS)
        .message(finalStatus.name())
        .build();
  }
}
