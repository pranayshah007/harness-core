/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.commons.entities.*;

import java.io.IOException;
import java.util.List;

public interface BigQueryOrchestratorService {
    Double getTotalCost();
    Double getBytesScanned();
    Double getSuccessfulQueries();
    Double getFailedQueries();
    List<BQOrchestratorVisibilityDataPoint> getVisibilityTimeSeries();
    List<BQOrchestratorExpensiveQueryPoint> getExpensiveQueries();
    List<BQOrchestratorSlotsDataPoint> getSlotData();
    BQOrchestratorSlotUsageStats getSlotUsageStats(BQOrchestratorOptimizationType optimizationType,
                                                   BQOrchestratorCommitmentDuration commitmentDuration, Double slotCount);
    boolean releaseSlots(long slotsCount);
    boolean buySlots(long slotsCount);
}
