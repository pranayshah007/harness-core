/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.request;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.cloudwatch.model.MetricDataQuery;
import java.util.Collection;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
public class AwsCloudWatchMetricDataRequest {
  private AwsCrossAccountAttributes awsCrossAccountAttributes;
  private String region;
  private Date startTime;
  private Date endTime;
  private Collection<MetricDataQuery> metricDataQueries;

  @Builder
  private AwsCloudWatchMetricDataRequest(AwsCrossAccountAttributes awsCrossAccountAttributes, String region,
      Date startTime, Date endTime, Collection<MetricDataQuery> metricDataQueries) {
    this.awsCrossAccountAttributes = awsCrossAccountAttributes;
    this.region = region;
    this.startTime = startTime;
    this.endTime = endTime;
    this.metricDataQueries = metricDataQueries;
  }
}
