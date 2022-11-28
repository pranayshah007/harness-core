/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceMetricDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;

import java.util.List;
import java.util.Set;

public interface DynatraceService extends DataSourceConnectivityChecker {
  List<DynatraceServiceDTO> getAllServices(ProjectParams projectParams, String connectorIdentifier, String tracingId);

  List<DynatraceMetricDTO> getAllMetrics(ProjectParams projectParams, String connectorIdentifier, String tracingId);

  DynatraceServiceDTO getServiceDetails(
      ProjectParams projectParams, String connectorIdentifier, String serviceEntityId, String tracingId);

  Set<MetricPackValidationResponse> validateData(ProjectParams projectParams, String connectorIdentifier,
      List<String> serviceMethodsIds, List<MetricPackDTO> metricPacks, String tracingId);

  List<TimeSeriesSampleDTO> fetchSampleData(ProjectParams projectParams, String connectorIdentifier, String serviceId,
      String metricSelector, String tracingId);
}
