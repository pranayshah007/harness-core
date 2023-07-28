/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import com.amazonaws.services.ecs.model.LaunchType;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ECSRecommendationDTO implements RecommendationDetailsDTO {
  String id;
  String clusterName;
  String serviceArn;
  String serviceName;
  LaunchType launchType;
  Map<String, String> current;
  Map<String, Map<String, String>> percentileBased;
  Cost lastDayCost;

  ContainerHistogramDTO.HistogramExp cpuHistogram;
  ContainerHistogramDTO.HistogramExp memoryHistogram;

  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
