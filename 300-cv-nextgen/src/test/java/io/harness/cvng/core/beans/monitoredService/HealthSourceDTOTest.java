/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HealthSourceDTOTest extends CvNextGenTestBase {
  @Inject private Map<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMap;
  BuilderFactory builderFactory;
  String healthSourceIdentifier;
  String monitoringServiceIdentifier;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    healthSourceIdentifier = generateUuid();
    monitoringServiceIdentifier = generateUuid();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToHealthSource() {
    List<CVConfig> cvConfigList = Collections.singletonList(cvConfigBuilder());
    HealthSource healthSource =
        HealthSourceDTO.toHealthSource(cvConfigList, dataSourceTypeToHealthSourceTransformerMap);
    assertThat(healthSource.getIdentifier()).isEqualTo(healthSourceIdentifier);
    assertThat(healthSource.getName()).isEqualTo(cvConfigList.get(0).getMonitoringSourceName());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToHealthSourceDTO() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    HealthSourceDTO healthSourceDTO = HealthSourceDTO.toHealthSourceDTO(healthSource);
    assertThat(healthSourceDTO.getIdentifier()).isEqualTo(healthSource.getIdentifier());
    assertThat(healthSourceDTO.getName()).isEqualTo(healthSource.getName());
    assertThat(healthSourceDTO.getVerificationType()).isEqualTo(healthSource.getSpec().getType().getVerificationType());
  }

  private CVConfig cvConfigBuilder() {
    return AppDynamicsCVConfig.builder()
        .accountId(builderFactory.getContext().getAccountId())
        .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
        .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
        .identifier(monitoringServiceIdentifier + "/" + healthSourceIdentifier)
        .monitoringSourceName(healthSourceIdentifier)
        .metricPack(
            MetricPack.builder().identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER).dataCollectionDsl("dsl").build())
        .applicationName(generateUuid())
        .tierName(generateUuid())
        .connectorIdentifier("AppDynamics Connector")
        .category(CVMonitoringCategory.PERFORMANCE)
        .productName(generateUuid())
        .build();
  }

  HealthSource createHealthSource(CVMonitoringCategory cvMonitoringCategory) {
    HealthSourceSpec healthSourceSpec =
        AppDynamicsHealthSourceSpec.builder()
            .applicationName("applicationName")
            .tierName("appTierName")
            .connectorRef("connectorIdentifier")
            .feature("feature")
            .metricPacks(new HashSet<>(Collections.singletonList(
                MetricPackDTO.builder().identifier(cvMonitoringCategory.getDisplayName()).build())))
            .metricDefinitions(Collections.EMPTY_LIST)
            .build();
    return HealthSource.builder()
        .identifier("identifier")
        .name("name")
        .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
        .spec(healthSourceSpec)
        .build();
  }
}
