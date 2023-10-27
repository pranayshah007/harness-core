/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.beans.activity.ActivityVerificationStatus;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
public class VerificationAbortDTO {
  @NotNull private VerificationStatus verificationStatus;

  @Getter
  @AllArgsConstructor
  public enum VerificationStatus {
    SUCCESS(ActivityVerificationStatus.ABORTED_AS_SUCCESS),
    FAILURE(ActivityVerificationStatus.ABORTED_AS_FAILURE);

    private ActivityVerificationStatus activityVerificationStatus;
  }
}
