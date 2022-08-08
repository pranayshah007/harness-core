/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.cloudwatch;

import com.amazonaws.services.cloudwatch.model.StandardUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by rsingh on 3/30/18.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudWatchMetric {
  private String metricName;
  private String displayName;
  private String dimension;
  private String dimensionDisplay;
  private String metricType;
  private boolean enabledDefault;
  private String statistics;
  private StandardUnit unit;
}
