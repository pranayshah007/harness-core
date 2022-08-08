/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo;

import java.util.List;
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public interface MetricsDataCollector<T extends MetricsDataCollectionInfo> extends DataCollector<T> {
  /**
   * Called with list of hosts. This needs to be thread safe.
   * @param hostBatch
   * @return Returns list of metric element.
   */
  List<MetricElement> fetchMetrics(List<String> hostBatch) throws DataCollectionException;
  /**
   * Fetch metrics for all the host. This needs to be thread safe.
   * @return Returns list of log elements.
   */
  List<MetricElement> fetchMetrics() throws DataCollectionException;
}
