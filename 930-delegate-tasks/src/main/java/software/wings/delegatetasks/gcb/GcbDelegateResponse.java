/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.beans.ExecutionStatus.DISCONTINUING;
import static io.harness.beans.ExecutionStatus.FAILED;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.exception.FailureType;

import software.wings.beans.command.GcbTaskParams;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;

import java.util.EnumSet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class GcbDelegateResponse implements DelegateTaskNotifyResponseData {
  @NotNull private final ExecutionStatus status;
  @Nullable private final GcbBuildDetails build;
  @NotNull private final GcbTaskParams params;
  @Nullable private DelegateMetaInfo delegateMetaInfo;
  @Nullable private final String errorMsg;
  @Nullable private List<String> triggers;
  private final boolean interrupted;
  private boolean isTimeoutError;
  private EnumSet<FailureType> failureTypes;

  @NotNull
  public static GcbDelegateResponse gcbDelegateResponseOf(
      @NotNull final GcbTaskParams params, @NotNull final GcbBuildDetails build) {
    return new GcbDelegateResponse(build.getStatus().getExecutionStatus(), build, params, null, false);
  }

  public static GcbDelegateResponse failedGcbTaskResponse(@NotNull final GcbTaskParams params, String errorMsg) {
    return new GcbDelegateResponse(FAILED, null, params, errorMsg, false);
  }

  public static GcbDelegateResponse timeoutGcbTaskResponse(@NotNull final GcbTaskParams params, String errorMsg) {
    return new GcbDelegateResponse(FAILED, null, params, null, errorMsg, null, false, true, FailureType.TIMEOUT);
  }

  public static GcbDelegateResponse interruptedGcbTask(@NotNull final GcbTaskParams params) {
    return new GcbDelegateResponse(DISCONTINUING, null, params, null, true);
  }

  public boolean isWorking() {
    return build != null && build.isWorking();
  }
}
