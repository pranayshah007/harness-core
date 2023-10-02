/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "AwsPrometheusHealthSource",
    description = "This is the AwsPrometheusHealthSource Metric Health Source spec entity defined in Harness")
public class AwsPrometheusHealthSourceSpec extends PrometheusHealthSourceSpec {
  @NotNull @NotBlank String region;
  @NotNull @NotBlank String workspaceId;

  @Override
  public DataSourceType getType() {
    return DataSourceType.AWS_PROMETHEUS;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String monitoredServiceIdentifier,
      String identifier, String name, List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    HealthSource.CVConfigUpdateResult prometheusCvConfigUpdateResult =
        super.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef,
            monitoredServiceIdentifier, identifier, name, existingCVConfigs, metricPackService);

    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(getAwsPrometheusCVConfigs(prometheusCvConfigUpdateResult.getDeleted()))
        .updated(getAwsPrometheusCVConfigs(prometheusCvConfigUpdateResult.getUpdated()))
        .added(getAwsPrometheusCVConfigs(prometheusCvConfigUpdateResult.getAdded()))
        .build();
  }

  private List<CVConfig> getAwsPrometheusCVConfigs(List<CVConfig> prometheusCVConfigs) {
    return prometheusCVConfigs.stream()
        .map(cv -> this.fromPrometheusCVConfig((PrometheusCVConfig) cv))
        .collect(Collectors.toList());
  }

  private CVConfig fromPrometheusCVConfig(PrometheusCVConfig prometheusCVConfig) {
    return AwsPrometheusCVConfig.builder()
        .region(region)
        .workspaceId(workspaceId)
        .accountId(prometheusCVConfig.getAccountId())
        .orgIdentifier(prometheusCVConfig.getOrgIdentifier())
        .projectIdentifier(prometheusCVConfig.getProjectIdentifier())
        .identifier(prometheusCVConfig.getIdentifier())
        .connectorIdentifier(prometheusCVConfig.getConnectorIdentifier())
        .monitoringSourceName(prometheusCVConfig.getMonitoringSourceName())
        .groupName(prometheusCVConfig.getGroupName())
        .category(prometheusCVConfig.getCategory())
        .monitoredServiceIdentifier(prometheusCVConfig.getMonitoredServiceIdentifier())
        .metricPack(prometheusCVConfig.getMetricPack())
        .metricInfoList(prometheusCVConfig.getMetricInfos())
        .productName(prometheusCVConfig.getProductName())
        .verificationType(prometheusCVConfig.getVerificationType())
        .createdAt(prometheusCVConfig.getCreatedAt())
        .createNextTaskIteration(prometheusCVConfig.getCreateNextTaskIteration())
        .lastUpdatedAt(prometheusCVConfig.getLastUpdatedAt())
        .enabled(prometheusCVConfig.isEnabled())
        .isDemo(prometheusCVConfig.isDemo())
        .uuid(prometheusCVConfig.getUuid())
        .build();
  }
}
