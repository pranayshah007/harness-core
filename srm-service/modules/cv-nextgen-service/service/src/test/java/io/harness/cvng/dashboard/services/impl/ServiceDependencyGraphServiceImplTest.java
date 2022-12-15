/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIVE_MIN;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.BuilderFactory.Context;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.Sources;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.dashboard.beans.ServiceDependencyGraphDTO;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.dashboard.services.api.ServiceDependencyGraphService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ServiceDependencyGraphServiceImplTest extends CvNextGenTestBase {
  @Inject private MetricPackService metricPackService;
  @Inject private ServiceDependencyGraphService serviceDependencyGraphService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private HeatMapService heatMapService;
  @Inject private HPersistence hPersistence;
  @Mock private NextGenService nextGenService;

  private BuilderFactory builderFactory;
  private Context context;
  private Clock clock;
  String environmentIdentifier;
  String serviceIdentifier;
  Map<String, String> tags;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  ServiceEnvironmentParams environmentParams;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    context = builderFactory.getContext();
    clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    environmentIdentifier = builderFactory.getContext().getEnvIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    tags = new HashMap<String, String>() {
      {
        put("tag1", "value1");
        put("tag2", "");
      }
    };
    environmentParams = ServiceEnvironmentParams.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .serviceIdentifier(serviceIdentifier)
                            .environmentIdentifier(environmentIdentifier)
                            .build();

    FieldUtils.writeField(heatMapService, "clock", clock, true);
    FieldUtils.writeField(serviceDependencyGraphService, "heatMapService", heatMapService, true);
    FieldUtils.writeField(serviceDependencyGraphService, "nextGenService", nextGenService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetDependencyGraph() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    HeatMap heatMap = builderFactory.heatMapBuilder()
                          .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                          .heatMapResolution(FIVE_MIN)
                          .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap = builderFactory.heatMapBuilder()
                  .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                  .heatMapResolution(FIVE_MIN)
                  .category(CVMonitoringCategory.PERFORMANCE)
                  .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO = serviceDependencyGraphService.getDependencyGraph(
        context.getProjectParams(), context.getServiceIdentifier(), context.getEnvIdentifier(), false, null);

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(1);
    assertThat(graphDTO.getNodes().get(0).getIdentifierRef()).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(graphDTO.getNodes().get(0).getServiceRef()).isEqualTo(context.getServiceIdentifier());
    assertThat(graphDTO.getNodes().get(0).getEnvironmentRef()).isEqualTo(context.getEnvIdentifier());
    assertThat(graphDTO.getNodes().get(0).getRiskLevel()).isEqualTo(Risk.HEALTHY);
    assertThat(graphDTO.getNodes().get(0).getRiskData())
        .isEqualTo(RiskData.builder().riskStatus(Risk.HEALTHY).healthScore(75).build());
    assertThat(graphDTO.getNodes().get(0).getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(graphDTO.getEdges().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_forMonitoredService() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    edgeDTO.setIdentifier(monitoredServiceDTO.getDependencies().iterator().next().getMonitoredServiceIdentifier());
    edgeDTO.setServiceRef(monitoredServiceDTO.getDependencies().iterator().next().getMonitoredServiceIdentifier());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO = serviceDependencyGraphService.getDependencyGraph(
        context.getProjectParams(), context.getServiceIdentifier(), context.getEnvIdentifier(), false, null);

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(2);
    assertThat(graphDTO.getEdges().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_withServicesAtRiskFilter() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTOBuilder monitoredServiceDTOBuilder = builderFactory.monitoredServiceDTOBuilder()
                                                                .identifier("monitoredServiceIdentifier")
                                                                .serviceRef(serviceIdentifier)
                                                                .environmentRef(environmentIdentifier)
                                                                .name("monitoredServiceName")
                                                                .tags(tags);
    MonitoredServiceDTO monitoredServiceOneDTO =
        createMonitoredServiceDTOWithCustomDependencies(monitoredServiceDTOBuilder, "service_1_local",
            environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceDTOBuilder, "service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceDTOBuilder, "service_3_local", "service_3", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);
    msOneHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msTwoHeatMap);
    msTwoHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msTwoHeatMap);

    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.70);
    hPersistence.save(msThreeHeatMap);
    msThreeHeatMap = builderFactory.heatMapBuilder()
                         .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                         .heatMapResolution(FIVE_MIN)
                         .category(CVMonitoringCategory.PERFORMANCE)
                         .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.70);
    hPersistence.save(msThreeHeatMap);

    ServiceDependencyGraphDTO graphDTO =
        serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(), null, null, true, null);
    graphDTO.getNodes().sort(Comparator.comparing(ServiceDependencyGraphDTO.ServiceSummaryDetails::getIdentifierRef));

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(2);
    assertThat(graphDTO.getNodes().get(0).getIdentifierRef()).isEqualTo(monitoredServiceOneDTO.getIdentifier());
    assertThat(graphDTO.getNodes().get(0).getServiceRef()).isEqualTo(monitoredServiceOneDTO.getServiceRef());
    assertThat(graphDTO.getNodes().get(0).getEnvironmentRef()).isEqualTo(monitoredServiceOneDTO.getEnvironmentRef());
    assertThat(graphDTO.getNodes().get(0).getRiskData())
        .isEqualTo(RiskData.builder().riskStatus(Risk.NEED_ATTENTION).healthScore(25).build());
    assertThat(graphDTO.getNodes().get(0).getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(graphDTO.getNodes().get(0).getIdentifierRef()).isNotEqualTo("service_3_local");
    assertThat(graphDTO.getNodes().get(1).getIdentifierRef()).isNotEqualTo("service_3_local");
    assertThat(graphDTO.getEdges().size()).isEqualTo(1);
    assertThat(graphDTO.getEdges().contains(new ServiceDependencyGraphDTO.Edge("service_2_local", "service_1_local")))
        .isEqualTo(true);
    assertThat(graphDTO.getEdges().contains(new ServiceDependencyGraphDTO.Edge("service_1_local", "service_3_local")))
        .isEqualTo(false);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetDependencyGraphWithMonitoredServiceIdentifier() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    HeatMap heatMap = builderFactory.heatMapBuilder()
                          .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                          .heatMapResolution(FIVE_MIN)
                          .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap = builderFactory.heatMapBuilder()
                  .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                  .heatMapResolution(FIVE_MIN)
                  .category(CVMonitoringCategory.PERFORMANCE)
                  .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO = serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(),
        Arrays.asList(context.getMonitoredServiceParams().getMonitoredServiceIdentifier()), false, null);

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(1);
    assertThat(graphDTO.getNodes().get(0).getIdentifierRef()).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(graphDTO.getNodes().get(0).getServiceRef()).isEqualTo(context.getServiceIdentifier());
    assertThat(graphDTO.getNodes().get(0).getEnvironmentRef()).isEqualTo(context.getEnvIdentifier());
    assertThat(graphDTO.getNodes().get(0).getRiskLevel()).isEqualTo(Risk.HEALTHY);
    assertThat(graphDTO.getNodes().get(0).getRiskData())
        .isEqualTo(RiskData.builder().riskStatus(Risk.HEALTHY).healthScore(75).build());
    assertThat(graphDTO.getNodes().get(0).getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(graphDTO.getEdges().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetDependencyGraphWithMonitoredServiceIdentifier_forMonitoredService() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    edgeDTO.setIdentifier(monitoredServiceDTO.getDependencies().iterator().next().getMonitoredServiceIdentifier());
    edgeDTO.setServiceRef(monitoredServiceDTO.getDependencies().iterator().next().getMonitoredServiceIdentifier());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO = serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(),
        Arrays.asList(context.getMonitoredServiceParams().getMonitoredServiceIdentifier()), false, null);

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(2);
    assertThat(graphDTO.getEdges().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_withFilterNoMatch() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).name("base service").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO = builderFactory.monitoredServiceDTOBuilder()
                                      .sources(Sources.builder().build())
                                      .dependencies(new HashSet<>())
                                      .build();
    edgeDTO.setIdentifier("service2");
    edgeDTO.setServiceRef("service2");
    edgeDTO.setName("service2");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    edgeDTO.setIdentifier("service1");
    edgeDTO.setServiceRef("service1");
    edgeDTO.setName("service1");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(Sources.builder().build())
                                                   .dependencies(new HashSet<>())
                                                   .serviceRef("service3")
                                                   .identifier("service3")
                                                   .name("service3")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO =
        serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(), null, false, "vice4");

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(0);
    assertThat(graphDTO.getEdges().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_withFilterPartialMatch_withDependencies() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).name("base service").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO = builderFactory.monitoredServiceDTOBuilder()
                                      .sources(Sources.builder().build())
                                      .dependencies(new HashSet<>())
                                      .build();
    edgeDTO.setIdentifier("service2");
    edgeDTO.setServiceRef("service2");
    edgeDTO.setName("service2");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    edgeDTO.setIdentifier("service1");
    edgeDTO.setServiceRef("service1");
    edgeDTO.setName("service1");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(Sources.builder().build())
                                                   .dependencies(new HashSet<>())
                                                   .serviceRef("service3")
                                                   .identifier("service3")
                                                   .name("service3")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO =
        serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(), null, false, "service");

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(4);
    assertThat(graphDTO.getEdges().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_withFilterPartialMatch_withNoDependencies() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).name("base service").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO = builderFactory.monitoredServiceDTOBuilder()
                                      .sources(Sources.builder().build())
                                      .dependencies(new HashSet<>())
                                      .build();
    edgeDTO.setIdentifier("service2");
    edgeDTO.setServiceRef("service2");
    edgeDTO.setName("service2");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    edgeDTO.setIdentifier("service1");
    edgeDTO.setServiceRef("service1");
    edgeDTO.setName("service1");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(Sources.builder().build())
                                                   .dependencies(new HashSet<>())
                                                   .serviceRef("service3")
                                                   .identifier("service3")
                                                   .name("service3")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO = serviceDependencyGraphService.getDependencyGraph(
        context.getProjectParams(), null, false, "Non Matching search");

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(0);
    assertThat(graphDTO.getEdges().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_withFilterFullMatch() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).name("base service").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO = builderFactory.monitoredServiceDTOBuilder()
                                      .sources(Sources.builder().build())
                                      .dependencies(new HashSet<>())
                                      .build();
    edgeDTO.setIdentifier("service2");
    edgeDTO.setServiceRef("service2");
    edgeDTO.setName("service2");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    edgeDTO.setIdentifier("service1");
    edgeDTO.setServiceRef("service1");
    edgeDTO.setName("service1");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(Sources.builder().build())
                                                   .dependencies(new HashSet<>())
                                                   .serviceRef("service3")
                                                   .identifier("service3")
                                                   .name("service3")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO =
        serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(), null, false, "vice");

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(4);
    assertThat(graphDTO.getEdges().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDependencyGraph_withFilterEnv() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).name("base service").build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    MonitoredServiceDTO edgeDTO = builderFactory.monitoredServiceDTOBuilder()
                                      .sources(Sources.builder().build())
                                      .dependencies(new HashSet<>())
                                      .build();
    edgeDTO.setIdentifier("service2");
    edgeDTO.setServiceRef("service2");
    edgeDTO.setEnvironmentRef("env");
    edgeDTO.setName("service2");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    edgeDTO.setIdentifier("service1");
    edgeDTO.setServiceRef("service1");
    edgeDTO.setEnvironmentRef("test");
    edgeDTO.setName("service1");
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), edgeDTO);

    MonitoredServiceDTO monitoredServiceDTO1 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(Sources.builder().build())
                                                   .dependencies(new HashSet<>())
                                                   .serviceRef("service3")
                                                   .identifier("service3")
                                                   .environmentRef("prod")
                                                   .name("service3")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);

    HeatMap heatMap = builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);
    heatMap =
        builderFactory.heatMapBuilder().heatMapResolution(FIVE_MIN).category(CVMonitoringCategory.PERFORMANCE).build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(heatMap, endTime, 0.15, 0.25);
    hPersistence.save(heatMap);

    ServiceDependencyGraphDTO graphDTO =
        serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(), null, false, "test");

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(1);
    assertThat(graphDTO.getEdges().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetDependencyGraphWithMonitoredServiceIdentifier_withServicesAtRiskFilter() {
    Instant endTime = roundDownTo5MinBoundary(clock.instant());
    MonitoredServiceDTOBuilder monitoredServiceDTOBuilder = builderFactory.monitoredServiceDTOBuilder()
                                                                .identifier("monitoredServiceIdentifier")
                                                                .serviceRef(serviceIdentifier)
                                                                .environmentRef(environmentIdentifier)
                                                                .name("monitoredServiceName")
                                                                .tags(tags);
    MonitoredServiceDTO monitoredServiceOneDTO =
        createMonitoredServiceDTOWithCustomDependencies(monitoredServiceDTOBuilder, "service_1_local",
            environmentParams.getServiceIdentifier(), Sets.newHashSet("service_2_local"));
    MonitoredServiceDTO monitoredServiceTwoDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceDTOBuilder, "service_2_local", "service_2", Sets.newHashSet());
    MonitoredServiceDTO monitoredServiceThreeDTO = createMonitoredServiceDTOWithCustomDependencies(
        monitoredServiceDTOBuilder, "service_3_local", "service_3", Sets.newHashSet("service_1_local"));
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceOneDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceTwoDTO);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceThreeDTO);

    HeatMap msOneHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);
    msOneHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceOneDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msOneHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msOneHeatMap);

    HeatMap msTwoHeatMap = builderFactory.heatMapBuilder()
                               .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                               .heatMapResolution(FIVE_MIN)
                               .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msTwoHeatMap);
    msTwoHeatMap = builderFactory.heatMapBuilder()
                       .monitoredServiceIdentifier(monitoredServiceTwoDTO.getIdentifier())
                       .heatMapResolution(FIVE_MIN)
                       .category(CVMonitoringCategory.PERFORMANCE)
                       .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msTwoHeatMap, endTime, 0.85, 0.75);
    hPersistence.save(msTwoHeatMap);

    HeatMap msThreeHeatMap = builderFactory.heatMapBuilder()
                                 .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                                 .heatMapResolution(FIVE_MIN)
                                 .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.70);
    hPersistence.save(msThreeHeatMap);
    msThreeHeatMap = builderFactory.heatMapBuilder()
                         .monitoredServiceIdentifier(monitoredServiceThreeDTO.getIdentifier())
                         .heatMapResolution(FIVE_MIN)
                         .category(CVMonitoringCategory.PERFORMANCE)
                         .build();
    setStartTimeEndTimeAndRiskScoreWith5MinBucket(msThreeHeatMap, endTime, 0.50, 0.70);
    hPersistence.save(msThreeHeatMap);

    ServiceDependencyGraphDTO graphDTO =
        serviceDependencyGraphService.getDependencyGraph(context.getProjectParams(), null, true, null);
    graphDTO.getNodes().sort(Comparator.comparing(ServiceDependencyGraphDTO.ServiceSummaryDetails::getIdentifierRef));

    assertThat(graphDTO).isNotNull();
    assertThat(graphDTO.getNodes().size()).isEqualTo(2);
    assertThat(graphDTO.getNodes().get(0).getIdentifierRef()).isEqualTo(monitoredServiceOneDTO.getIdentifier());
    assertThat(graphDTO.getNodes().get(0).getServiceRef()).isEqualTo(monitoredServiceOneDTO.getServiceRef());
    assertThat(graphDTO.getNodes().get(0).getEnvironmentRef()).isEqualTo(monitoredServiceOneDTO.getEnvironmentRef());
    assertThat(graphDTO.getNodes().get(0).getRiskData())
        .isEqualTo(RiskData.builder().riskStatus(Risk.NEED_ATTENTION).healthScore(25).build());
    assertThat(graphDTO.getNodes().get(0).getType()).isEqualTo(MonitoredServiceType.APPLICATION);
    assertThat(graphDTO.getNodes().get(0).getIdentifierRef()).isNotEqualTo("service_3_local");
    assertThat(graphDTO.getNodes().get(1).getIdentifierRef()).isNotEqualTo("service_3_local");
    assertThat(graphDTO.getEdges().size()).isEqualTo(1);
    assertThat(graphDTO.getEdges().contains(new ServiceDependencyGraphDTO.Edge("service_2_local", "service_1_local")))
        .isEqualTo(true);
    assertThat(graphDTO.getEdges().contains(new ServiceDependencyGraphDTO.Edge("service_1_local", "service_3_local")))
        .isEqualTo(false);
  }

  private void setStartTimeEndTimeAndRiskScoreWith5MinBucket(
      HeatMap heatMap, Instant endTime, double firstHalfRiskScore, double secondHalfRiskScore) {
    Instant startTime = endTime.minus(4, ChronoUnit.HOURS);
    heatMap.setHeatMapBucketStartTime(startTime);
    heatMap.setHeatMapBucketEndTime(endTime);
    List<HeatMapRisk> heatMapRisks = new ArrayList<>();

    for (Instant time = startTime; time.isBefore(startTime.plus(2, ChronoUnit.HOURS));
         time = time.plus(5, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(firstHalfRiskScore)
                           .startTime(time)
                           .endTime(time.plus(5, ChronoUnit.MINUTES))
                           .anomalousMetricsCount(1)
                           .anomalousLogsCount(2)
                           .build());
    }
    for (Instant time = startTime.plus(2, ChronoUnit.HOURS); time.isBefore(endTime);
         time = time.plus(5, ChronoUnit.MINUTES)) {
      heatMapRisks.add(HeatMapRisk.builder()
                           .riskScore(secondHalfRiskScore)
                           .startTime(time)
                           .endTime(time.plus(5, ChronoUnit.MINUTES))
                           .anomalousMetricsCount(1)
                           .anomalousLogsCount(2)
                           .build());
    }
    heatMap.setHeatMapRisks(heatMapRisks);
  }

  private MonitoredServiceDTO createMonitoredServiceDTOWithCustomDependencies(
      MonitoredServiceDTOBuilder monitoredServiceDTOBuilder, String identifier, String serviceIdentifier,
      Set<String> dependentServiceIdentifiers) {
    return monitoredServiceDTOBuilder.identifier(identifier)
        .name(identifier)
        .serviceRef(serviceIdentifier)
        .sources(MonitoredServiceDTO.Sources.builder()
                     .healthSources(Arrays.asList(builderFactory.createHealthSource(CVMonitoringCategory.ERRORS))
                                        .stream()
                                        .collect(Collectors.toSet()))
                     .build())
        .dependencies(Sets.newHashSet(
            dependentServiceIdentifiers.stream()
                .map(id -> MonitoredServiceDTO.ServiceDependencyDTO.builder().monitoredServiceIdentifier(id).build())
                .collect(Collectors.toSet())))
        .build();
  }
}
