/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.core.beans.TimeRange;
import io.harness.ng.beans.PageResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TransactionMetricInfoSummaryPageDTO {
  PageResponse<TransactionMetricInfo> pageResponse;
  @Deprecated TimeRange deploymentTimeRange; // TODO: need to remove it in next release.
  Long deploymentStartTime;
  Long deploymentEndTime;
}
