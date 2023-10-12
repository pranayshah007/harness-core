/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.handler.monitoredService;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.Sources;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec.AppDMetricDefinitions;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MonitoredServiceSLIMetricUpdateHandlerTest extends CvNextGenTestBase {
  @Inject private MonitoredServiceSLIMetricUpdateHandler monitoredServiceSLIMetricUpdateHandler;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;

  private BuilderFactory builderFactory;
  private String monitoredServiceIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceIdentifier =
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier();
    createMonitoredService();
    /*Mockito.doReturn(MonitoredService.builder().enabled(true).build()).when(monitoredServiceService).getMonitoredService(Mockito.any());
    FieldUtils.writeField(serviceLevelIndicatorService,"monitoredServiceService",monitoredServiceService,true);*/
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testBeforeUpdate_healthSourceDeleted() {
    MonitoredServiceDTO existingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(Sources.builder()
                         .healthSources(new HashSet<>(Arrays.asList(
                             HealthSource.builder()
                                 .identifier("healthSourceIdentifier")
                                 .name("health source name")
                                 .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                                 .spec(AppDynamicsHealthSourceSpec.builder()
                                           .applicationName("appApplicationName")
                                           .tierName("tier")
                                           .connectorRef("CONNECTOR_IDENTIFIER")
                                           .feature("Application Monitoring")
                                           .metricDefinitions(Arrays.asList(AppDMetricDefinitions.builder()
                                                                                .identifier("metric1")
                                                                                .metricName("metric1")
                                                                                .build()))
                                           .build())
                                 .build())))
                         .build())
            .build();
    MonitoredServiceDTO updatingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(Sources.builder().healthSources(new HashSet<>()).build())
            .build();
    serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Arrays.asList(builderFactory.getServiceLevelIndicatorDTO()), "sloIdentifier",
        existingMonitoredService.getIdentifier(), "healthSourceIdentifier");
    assertThatThrownBy(()
                           -> monitoredServiceSLIMetricUpdateHandler.beforeUpdate(
                               builderFactory.getProjectParams(), existingMonitoredService, updatingMonitoredService))
        .hasMessage(
            "Deleting metrics are used in SLIs, Please delete the SLIs before deleting metrics. SLIs : sloIdentifier_metric1");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testBeforeUpdate_healthSourceUpdated() {
    MonitoredServiceDTO existingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(Sources.builder()
                         .healthSources(new HashSet<>(Arrays.asList(
                             HealthSource.builder()
                                 .identifier("healthSourceIdentifier")
                                 .name("health source name")
                                 .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                                 .spec(AppDynamicsHealthSourceSpec.builder()
                                           .applicationName("appApplicationName")
                                           .tierName("tier")
                                           .connectorRef("CONNECTOR_IDENTIFIER")
                                           .feature("Application Monitoring")
                                           .metricDefinitions(Arrays.asList(AppDMetricDefinitions.builder()
                                                                                .identifier("metric1")
                                                                                .metricName("metric1")
                                                                                .build()))
                                           .build())
                                 .build())))
                         .build())
            .build();
    MonitoredServiceDTO updatingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(Sources.builder()
                         .healthSources(new HashSet<>(Arrays.asList(
                             HealthSource.builder()
                                 .identifier("healthSourceIdentifier")
                                 .name("health source name")
                                 .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                                 .spec(AppDynamicsHealthSourceSpec.builder()
                                           .applicationName("appApplicationName")
                                           .tierName("tier")
                                           .connectorRef("CONNECTOR_IDENTIFIER")
                                           .feature("Application Monitoring")
                                           .metricDefinitions(Arrays.asList(AppDMetricDefinitions.builder()
                                                                                .identifier("metric2")
                                                                                .metricName("metric2")
                                                                                .build()))
                                           .build())
                                 .build())))
                         .build())
            .build();
    serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Arrays.asList(builderFactory.getServiceLevelIndicatorDTO()), "sloIdentifier",
        existingMonitoredService.getIdentifier(), "healthSourceIdentifier");
    assertThatThrownBy(()
                           -> monitoredServiceSLIMetricUpdateHandler.beforeUpdate(
                               builderFactory.getProjectParams(), existingMonitoredService, updatingMonitoredService))
        .hasMessage(
            "Deleting metrics are used in SLIs, Please delete the SLIs before deleting metrics. SLIs : sloIdentifier_metric1");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testBeforeUpdate_ForSomeOtherMetricDeletion() {
    MonitoredServiceDTO existingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(Sources.builder()
                         .healthSources(new HashSet<>(Arrays.asList(
                             HealthSource.builder()
                                 .identifier("healthSourceIdentifier")
                                 .name("health source name")
                                 .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                                 .spec(AppDynamicsHealthSourceSpec.builder()
                                           .applicationName("appApplicationName")
                                           .tierName("tier")
                                           .connectorRef("CONNECTOR_IDENTIFIER")
                                           .feature("Application Monitoring")
                                           .metricDefinitions(Arrays.asList(AppDMetricDefinitions.builder()
                                                                                .identifier("metric3")
                                                                                .metricName("metric3")
                                                                                .build()))
                                           .build())
                                 .build())))
                         .build())
            .build();
    MonitoredServiceDTO updatingMonitoredService =
        builderFactory.monitoredServiceDTOBuilder()
            .sources(Sources.builder()
                         .healthSources(new HashSet<>(Arrays.asList(
                             HealthSource.builder()
                                 .identifier("healthSourceIdentifier")
                                 .name("health source name")
                                 .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
                                 .spec(AppDynamicsHealthSourceSpec.builder()
                                           .applicationName("appApplicationName")
                                           .tierName("tier")
                                           .connectorRef("CONNECTOR_IDENTIFIER")
                                           .feature("Application Monitoring")
                                           .metricDefinitions(Arrays.asList(AppDMetricDefinitions.builder()
                                                                                .identifier("metric2")
                                                                                .metricName("metric2")
                                                                                .build()))
                                           .build())
                                 .build())))
                         .build())
            .build();
    serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Arrays.asList(builderFactory.getServiceLevelIndicatorDTO()), "sloIdentifier",
        existingMonitoredService.getIdentifier(), "healthSourceIdentifier");
    monitoredServiceSLIMetricUpdateHandler.beforeUpdate(
        builderFactory.getProjectParams(), existingMonitoredService, updatingMonitoredService);
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}
