/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import software.wings.service.impl.ThirdPartyApiCallLog;

import com.google.api.services.monitoring.v3.Monitoring;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StackdriverDataFetchParameters {
  private String nameSpace;
  private String metric;
  private String dimensionValue;
  private String groupName;
  private String filter;
  private String projectId;
  private Monitoring monitoring;
  private Optional<List<String>> groupByFields;
  private Optional<String> perSeriesAligner;
  private Optional<String> crossSeriesReducer;
  private long startTime;
  private long endTime;
  private int dataCollectionMinute;
  private ThirdPartyApiCallLog apiCallLog;
  private boolean is247Task;
}
