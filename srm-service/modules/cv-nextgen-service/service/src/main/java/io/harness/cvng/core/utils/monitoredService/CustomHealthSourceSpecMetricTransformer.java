/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.CustomHealthMetricDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceMetricSpec;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class CustomHealthSourceSpecMetricTransformer
    implements CVConfigToHealthSourceTransformer<CustomHealthMetricCVConfig, CustomHealthSourceMetricSpec> {
  @Override
  public CustomHealthSourceMetricSpec transformToHealthSourceConfig(List<CustomHealthMetricCVConfig> cvConfigGroup) {
    Preconditions.checkNotNull(cvConfigGroup);
    CustomHealthSourceMetricSpec customHealthSourceSpec =
        CustomHealthSourceMetricSpec.builder()
            .connectorRef(cvConfigGroup.get(0).getConnectorIdentifier())
            .metricDefinitions(new ArrayList<>())
            .build();

    cvConfigGroup.forEach(customHealthCVConfig -> {
      customHealthCVConfig.getMetricInfos().forEach(cvMetricDefinition -> {
        RiskProfile riskProfile = RiskProfile.builder()
                                      .category(customHealthCVConfig.getMetricPack().getCategory())
                                      .metricType(cvMetricDefinition.getMetricType())
                                      .thresholdTypes(customHealthCVConfig.getThresholdTypeOfMetric(
                                          cvMetricDefinition.getMetricName(), customHealthCVConfig))
                                      .build();
        CustomHealthRequestDefinition requestDefinition = cvMetricDefinition.getRequestDefinition();
        CustomHealthMetricDefinition customHealthMetricDefinition =
            CustomHealthMetricDefinition.builder()
                .groupName(customHealthCVConfig.getGroupName())
                .queryType(customHealthCVConfig.getQueryType())
                .requestDefinition(CustomHealthRequestDefinition.builder()
                                       .urlPath(requestDefinition.getUrlPath())
                                       .method(requestDefinition.getMethod())
                                       .requestBody(requestDefinition.getRequestBody())
                                       .startTimeInfo(requestDefinition.getStartTimeInfo())
                                       .endTimeInfo(requestDefinition.getEndTimeInfo())
                                       .build())
                .metricResponseMapping(cvMetricDefinition.getMetricResponseMapping())
                .metricName(cvMetricDefinition.getMetricName())
                .sli(SLIDTO.builder().enabled(cvMetricDefinition.getSli().isEnabled()).build())
                .identifier(cvMetricDefinition.getIdentifier())
                .riskProfile(riskProfile)
                .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder()
                              .liveMonitoring(HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
                                                  .enabled(cvMetricDefinition.getLiveMonitoring().isEnabled())
                                                  .build())
                              .deploymentVerification(
                                  HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                      .enabled(cvMetricDefinition.getDeploymentVerification().isEnabled())
                                      .serviceInstanceMetricPath(
                                          cvMetricDefinition.getDeploymentVerification().getServiceInstanceMetricPath())
                                      .build())
                              .riskProfile(riskProfile)
                              .build())
                .build();
        customHealthSourceSpec.getMetricDefinitions().add(customHealthMetricDefinition);
      });
    });
    return customHealthSourceSpec;
  }
}
