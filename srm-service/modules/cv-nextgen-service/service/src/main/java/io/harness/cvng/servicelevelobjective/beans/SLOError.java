/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "SLOErrorKeys")
@EqualsAndHashCode
public class SLOError {
  @NotNull private boolean failedState;

  private String errorMessage;

  private SLOErrorType sloErrorType;

  public static SLOError getNoError() {
    return SLOError.builder().failedState(false).build();
  }

  public static SLOError getError(boolean failedState, SLOErrorType sloErrorType, String errorMessage) {
    return SLOError.builder().failedState(failedState).sloErrorType(sloErrorType).errorMessage(errorMessage).build();
  }

  public static SLOError getErrorForDataCollectionFailureInSimpleSLOInListView() {
    return getError(
        true, SLOErrorType.DATA_COLLECTION_FAILURE, "The SLO is experiencing issues and is unable to collect data.");
  }

  public static SLOError getErrorForDataCollectionFailureInSimpleSLOWidgetDetailsView() {
    return getError(true, SLOErrorType.DATA_COLLECTION_FAILURE,
        "The SLO is experiencing issues and requires manual intervention to resolve. Investigate the issues in Execution Logs and External API Calls.");
  }

  public static SLOError getErrorForDataCollectionFailureInCompositeSLOWidgetDetailsView() {
    return getError(true, SLOErrorType.DATA_COLLECTION_FAILURE,
        "Contributing SLO contains errors, which needs to be addressed manually. As a result, the Composite SLO will be put on hold until the issue has been resolved.");
  }

  public static SLOError getErrorForDataCollectionFailureInCompositeSLOInListView() {
    return getError(true, SLOErrorType.DATA_COLLECTION_FAILURE,
        "Contributing SLO contain errors and needs to be addressed manually.");
  }

  public static SLOError getErrorForDeletionOfSimpleSLOInWidgetDetailsView() {
    return getError(true, SLOErrorType.SIMPLE_SLO_DELETION,
        "As one of the contributing SLOs has been deleted, please remove the corresponding SLO from the Composite SLO.");
  }

  public static SLOError getErrorForDeletionOfSimpleSLOInConsumptionView() {
    return getError(true, SLOErrorType.SIMPLE_SLO_DELETION, "The SLO has been deleted and is unable to collect data.");
  }

  public static SLOError getErrorForDeletionOfSimpleSLOInConfigurationListView() {
    return getError(true, SLOErrorType.SIMPLE_SLO_DELETION, "The SLO has been deleted.");
  }

  public static SLOError getErrorForDeletionOfSimpleSLOInListView() {
    return getError(
        true, SLOErrorType.SIMPLE_SLO_DELETION, "A contributing SLO has been deleted and needs to be reconfigured.");
  }
}
