package io.harness.cvng.servicelevelobjective.services.impl;

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.cvng.core.services.CVNextGenConstants.SLI_RECORD_BUCKET_SIZE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slispec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecordBucket;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CompositeSLORecordBucketServiceImplTest extends CvNextGenTestBase {
  @Spy @Inject private CompositeSLORecordBucketServiceImpl sloRecordService;

  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  BuilderFactory builderFactory;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  private String leastPerformantVerificationTaskId;
  private String requestVerificationTaskId;

  ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;
  CompositeServiceLevelObjective compositeServiceLevelObjective;
  CompositeServiceLevelObjective compositeServiceLevelObjective2;
  CompositeServiceLevelObjective leastPerformantCompositeServiceLevelObjective;
  CompositeServiceLevelObjective leastPerformantCompositeServiceLevelObjective2;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO3;
  SimpleServiceLevelObjective simpleServiceLevelObjective1;
  SimpleServiceLevelObjective simpleServiceLevelObjective2;
  SimpleServiceLevelObjective simpleServiceLevelObjective3;

  ServiceLevelObjectiveV2DTO requestServiceLevelObjectiveV2DTO;
  CompositeServiceLevelObjective requestCompositeServiceLevelObjective;
  ServiceLevelObjectiveV2DTO requestSimpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO requestSimpleServiceLevelObjectiveDTO2;
  SimpleServiceLevelObjective requestSimpleServiceLevelObjective1;
  SimpleServiceLevelObjective requestSimpleServiceLevelObjective2;

  List<SLIState> sliStateList3 =
      Arrays.asList(SLIState.NO_DATA, SLIState.NO_DATA, SLIState.NO_DATA, SLIState.NO_DATA, SLIState.NO_DATA);

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    builderFactory.getContext().setProjectIdentifier("project");
    MockitoAnnotations.initMocks(this);

    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    simpleServiceLevelObjectiveDTO1 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(MonitoredServiceDTO.Sources.builder().build())
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    simpleServiceLevelObjectiveDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO3.getSpec();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO3 = builderFactory.getServiceLevelIndicatorDTO();
    WindowBasedServiceLevelIndicatorSpec windowBasedServiceLevelIndicatorSpec =
        (WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO3.getSpec();
    windowBasedServiceLevelIndicatorSpec.setSliMissingDataType(SLIMissingDataType.IGNORE);
    serviceLevelIndicatorDTO3.setSpec(windowBasedServiceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec3.setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO3));
    simpleServiceLevelObjectiveDTO3.setSpec(simpleServiceLevelObjectiveSpec3);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO3);
    simpleServiceLevelObjective3 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO3.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier2")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective3.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective3.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective2 = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("leastPerformantCompositeServiceLevelObjectiveIdentifier")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .sloFormulaType(CompositeSLOFormulaType.LEAST_PERFORMANCE)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(100.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(30.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    leastPerformantCompositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("leastPerformantCompositeServiceLevelObjectiveIdentifier2")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .sloFormulaType(CompositeSLOFormulaType.LEAST_PERFORMANCE)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(100.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective3.getIdentifier())
                                  .weightagePercentage(30.0)
                                  .accountId(simpleServiceLevelObjective3.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    leastPerformantCompositeServiceLevelObjective2 =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    requestSimpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("requestSloIdentifier").build();
    simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) requestSimpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    requestSimpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO1);
    requestSimpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO1.getIdentifier());

    requestSimpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("requestSloIdentifier2").build();
    simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) requestSimpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    requestSimpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO2);
    requestSimpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO2.getIdentifier());

    requestServiceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("requestCompositeSLOIdentifier")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .evaluationType(SLIEvaluationType.REQUEST)
                      .serviceLevelObjectivesDetails(Arrays.asList(
                          ServiceLevelObjectiveDetailsDTO.builder()
                              .serviceLevelObjectiveRef(requestSimpleServiceLevelObjective1.getIdentifier())
                              .weightagePercentage(75.0)
                              .accountId(requestSimpleServiceLevelObjective1.getAccountId())
                              .orgIdentifier(requestSimpleServiceLevelObjective1.getOrgIdentifier())
                              .projectIdentifier(requestSimpleServiceLevelObjective1.getProjectIdentifier())
                              .build(),
                          ServiceLevelObjectiveDetailsDTO.builder()
                              .serviceLevelObjectiveRef(requestSimpleServiceLevelObjective2.getIdentifier())
                              .weightagePercentage(25.0)
                              .accountId(requestSimpleServiceLevelObjective2.getAccountId())
                              .orgIdentifier(requestSimpleServiceLevelObjective2.getOrgIdentifier())
                              .projectIdentifier(requestSimpleServiceLevelObjective2.getProjectIdentifier())
                              .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), requestServiceLevelObjectiveV2DTO);
    requestCompositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), requestServiceLevelObjectiveV2DTO.getIdentifier());

    verificationTaskId = compositeServiceLevelObjective.getUuid();
    leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective.getUuid();
    requestVerificationTaskId = requestCompositeServiceLevelObjective.getUuid();
    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(3.0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves_withIgnoreMissingDataType() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId3, sliStateList3);
    String verificationTaskId = compositeServiceLevelObjective2.getUuid();
    sloRecordService.create(compositeServiceLevelObjective2, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(0.0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreateLeastPerformant_multipleSaves() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(2.6, offset(0.001)); // 1
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(2.4, offset(0.001)); // 0 why ?
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreateLeastPerformant_multipleSaves_withIgnoreMissingDataType() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId3, sliStateList3);
    String leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective2.getUuid();
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective2, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(0.0, offset(0.001));
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(0.0, offset(0.001));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves_request() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100L, 200L, 300L, 0L, 100L);
    List<Long> badCounts1 = Arrays.asList(10L, 20L, 30L, 0L, 10L);

    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts2 = Arrays.asList(100L, 200L, 300L, 0L, 100L);
    List<Long> badCounts2 = Arrays.asList(0L, 0L, 10L, 0L, 0L);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecordBuckets(sliId2, sliStateList2, goodCounts2, badCounts2);
    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);

    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isZero();
    assertThat(sloRecords.get(0).getRunningGoodCount()).isZero();
    assertThat(sloRecords.get(0).getScopedIdentifierSLIRecordBucketMap().size()).isEqualTo(2);
    assertThat(sloRecords.get(0)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecords.get(0)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(700);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreate_SkipData() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.SKIP_DATA);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.SKIP_DATA, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(1.75);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreate_SkipData_request() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100L, 200L, 0L, 0L, 100L);
    List<Long> badCounts1 = Arrays.asList(10L, 20L, 0L, 0L, 10L);

    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.SKIP_DATA);
    List<Long> goodCounts2 = Arrays.asList(100L, 200L, 300L, 0L, 0L);
    List<Long> badCounts2 = Arrays.asList(0L, 0L, 10L, 0L, 0L);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecordBuckets(sliId2, sliStateList2, goodCounts2, badCounts2);
    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(0);
    assertThat(sloRecords.get(0).getScopedIdentifierSLIRecordBucketMap().size()).isEqualTo(2);
    assertThat(sloRecords.get(0)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    /*assertThat(sloRecords.get(0)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(300); TODO fix*/
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecordBuckets(startTime, endTime, runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(3.0);
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap_withIgnoreMissingDataType() {
    String verificationTaskId = compositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecordBuckets(startTime, endTime, runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId3, sliStateList3);
    sloRecordService.create(compositeServiceLevelObjective2, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_completeOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecordBuckets(startTime, endTime, runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(2.6, offset(0.01));
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(2.4, offset(0.01));
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_completeOverlap_withIgnoreMissingDataType() {
    String leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecordBuckets(startTime, endTime, runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId3, sliStateList3);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective2, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(0.0, offset(0.001));
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(0.0, offset(0.001));
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  @Ignore("Enable the test case, once request issue is fixed")
  public void testUpdate_completeOverlap_request() {
    Map<String, SLIRecordBucket> scopedIdentifierToSLIRecordMap = new HashMap<>();
    createSLORecordBuckets(startTime, endTime, scopedIdentifierToSLIRecordMap);
    List<CompositeSLORecordBucket> sloRecordBuckets =
        sloRecordService.getSLORecordBuckets(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecordBuckets.size()).isEqualTo(1);
    assertThat(sloRecordBuckets.get(0).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecordBuckets.get(0).getRunningGoodCount()).isEqualTo(0);
    assertThat(Objects.isNull(sloRecordBuckets.get(0).getScopedIdentifierSLIRecordBucketMap())).isEqualTo(true);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100L, 200L, 0L, 0L, 100L);
    List<Long> badCounts1 = Arrays.asList(10L, 20L, 0L, 0L, 10L);

    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts2 = Arrays.asList(100L, 200L, 300L, 0L, 0L);
    List<Long> badCounts2 = Arrays.asList(0L, 0L, 10L, 0L, 0L);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecordBuckets(sliId2, sliStateList2, goodCounts2, badCounts2);

    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecordBuckets1 =
        sloRecordService.getSLORecordBuckets(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecordBuckets1.size()).isEqualTo(1);
    assertThat(sloRecordBuckets1.get(0).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecordBuckets1.get(0).getRunningGoodCount()).isEqualTo(0);
    assertThat(sloRecordBuckets1.get(0).getScopedIdentifierSLIRecordBucketMap().size()).isEqualTo(2);
    assertThat(sloRecordBuckets1.get(0)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecordBuckets1.get(0)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(300);
  }

  // TODO we need tests with 2-3 windows
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.75, 3.75, 4.75, 5.75, 6.75, 7.75, 8.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25);
    createSLORecordBuckets(startTime.minusSeconds(300), endTime, runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(8.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(3.0);
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(7.0);
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap_withIgnoreMissingDataType() {
    String verificationTaskId = compositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.75, 3.75, 4.75, 5.75, 6.75, 7.75, 8.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25);
    createSLORecordBuckets(startTime.minusSeconds(300), endTime, runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(8.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId3, sliStateList3);
    sloRecordService.create(compositeServiceLevelObjective2, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(3.75);
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_partialOverlap() { // TODO verify this
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.75, 3.75, 4.75, 5.75, 6.75, 7.75, 8.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25);
    createSLORecordBuckets(startTime.minusSeconds(600), endTime.minusSeconds(300), runningGoodCount, runningBadCount,
        leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords = sloRecordService.getSLORecordBuckets(
        leastPerformantVerificationTaskId, startTime.minusSeconds(600), endTime.minusSeconds(300));
    assertThat(sloRecords.size()).isEqualTo(2);
    assertThat(sloRecords.get(1).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(1).getRunningGoodCount()).isEqualTo(8.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(3.55, offset(0.01)); // TODO recheck
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(10.45, offset(0.01));
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_partialOverlap_withIgnoreMissingDataType() {
    String leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.75, 3.75, 4.75, 5.75, 6.75, 7.75, 8.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25, 1.25);
    createSLORecordBuckets(
        startTime.minusSeconds(300), endTime, runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(8.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId3, sliStateList3);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective2, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(1.25, offset(0.01));
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(3.75, offset(0.01));
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  @Ignore("Enable the test case, once request issue is fixed")
  public void testUpdate_partialOverlap_request() {
    Map<String, SLIRecordBucket> scopedIdentifierToSLIRecordMap = new HashMap<>();
    createSLORecordBuckets(startTime.minusSeconds(300), endTime, scopedIdentifierToSLIRecordMap);
    List<CompositeSLORecordBucket> sloRecords =
        sloRecordService.getSLORecordBuckets(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(1);
    assertThat(sloRecords.get(0).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords.get(0).getRunningGoodCount()).isEqualTo(0);
    assertThat(Objects.isNull(sloRecords.get(0).getScopedIdentifierSLIRecordBucketMap())).isEqualTo(true);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100L, 200L, 0L, 0L, 100L);
    List<Long> badCounts1 = Arrays.asList(10L, 20L, 0L, 0L, 10L);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.BAD);
    List<Long> goodCounts2 = Arrays.asList(100L, 200L, 300L, 0L, 0L);
    List<Long> badCounts2 = Arrays.asList(0L, 0L, 10L, 0L, 0L);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecordBuckets(sliId2, sliStateList2, goodCounts2, badCounts2);
    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecords1 =
        sloRecordService.getSLORecordBuckets(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(1);
    assertThat(sloRecords1.get(0).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(0).getRunningGoodCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(0).getSloVersion()).isEqualTo(0);
    assertThat(sloRecords1.get(2)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecords1.get(2)
                   .getScopedIdentifierSLIRecordBucketMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(400);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testUpdate_duplicateRecords() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecordBuckets(sliId1, sliStateList1);
    createSLIRecordBuckets(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecordBucket> sloRecordBuckets =
        sloRecordService.getSLORecordBuckets(compositeServiceLevelObjective.getUuid(), startTime, endTime);
    assertThat(sloRecordBuckets.size()).isEqualTo(1);
    assertThat(sloRecordBuckets.get(0).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecordBuckets.get(0).getRunningGoodCount()).isEqualTo(3.0);

    // Insert duplicates
    sloRecordBuckets = sloRecordService.getLatestCountSLORecords(compositeServiceLevelObjective.getUuid(), 2);
    assertThat(sloRecordBuckets.size()).isEqualTo(1);
    int count = 0;
    for (CompositeSLORecordBucket sloRecordBucket : sloRecordBuckets) {
      sloRecordBucket.setUuid(generateUuid());
      sloRecordBucket.setRunningBadCount(0);
      sloRecordBucket.setRunningGoodCount(count);
      count += 1;
    }
    hPersistence.saveBatch(sloRecordBuckets);

    // Create again
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    sloRecordBuckets = sloRecordService.getSLORecordBuckets(
        compositeServiceLevelObjective.getUuid(), endTime.minus(5, ChronoUnit.MINUTES), endTime);
    assertThat(sloRecordBuckets.size()).isEqualTo(2);
    sloRecordBuckets = sloRecordBuckets.stream()
                           .sorted(Comparator.comparingLong(CompositeSLORecordBucket::getLastUpdatedAt).reversed())
                           .collect(Collectors.toList());
    CompositeSLORecordBucket updatedLastRecord = sloRecordBuckets.get(0);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(2.0);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3.0);
  }

  private List<SLIRecordBucket> createSLIRecordBuckets(String sliId, List<SLIState> states) {
    if (states.size() % SLI_RECORD_BUCKET_SIZE != 0) {
      throw new RuntimeException("The SLI Records are of incorrect Length");
    }
    int index = 0;
    List<SLIRecordBucket> sliRecords = new ArrayList<>();
    for (Instant instant = startTime; instant.isBefore(endTime);
         instant = instant.plus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES)) {
      SLIRecordBucket sliRecordBucket = SLIRecordBucket.builder()
                                            .sliId(sliId)
                                            .sliVersion(0)
                                            .sliStates(states.subList(index, index + SLI_RECORD_BUCKET_SIZE))
                                            .runningBadCount(0)
                                            .runningGoodCount(1)
                                            .sliVersion(0)
                                            .bucketStartTime(instant)
                                            .build();
      sliRecords.add(sliRecordBucket);
      index += SLI_RECORD_BUCKET_SIZE;
    }
    hPersistence.saveBatch(sliRecords);
    return sliRecords;
  }

  private List<SLIRecordBucket> createSLIRecordBuckets(
      String sliId, List<SLIState> states, List<Long> goodCounts, List<Long> badCounts) {
    int index = 0;
    List<SLIRecordBucket> sliRecords = new ArrayList<>();
    long runningGoodCount = 0;
    long runningBadCount = 0;
    for (Instant instant = startTime; instant.isBefore(endTime);
         instant = instant.plus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES)) {
      runningGoodCount += goodCounts.subList(index, index + SLI_RECORD_BUCKET_SIZE).stream().mapToLong(x -> x).sum();
      runningBadCount += badCounts.subList(index, index + SLI_RECORD_BUCKET_SIZE).stream().mapToLong(x -> x).sum();
      SLIRecordBucket sliRecord = SLIRecordBucket.builder()
                                      .sliId(sliId)
                                      .sliVersion(0)
                                      .sliStates(states.subList(index, index + SLI_RECORD_BUCKET_SIZE))
                                      .runningBadCount(runningBadCount)
                                      .runningGoodCount(runningGoodCount)
                                      .sliVersion(0)
                                      .bucketStartTime(instant)
                                      .build();
      sliRecords.add(sliRecord);
      index += SLI_RECORD_BUCKET_SIZE;
    }
    hPersistence.saveBatch(sliRecords);
    return sliRecords;
  }

  private List<CompositeSLORecordBucket> createSLORecordBuckets(Instant start, Instant end,
      List<Double> runningGoodCount, List<Double> runningBadCount, String verificationTaskId) {
    int index = -1;
    List<CompositeSLORecordBucket> sloRecords = new ArrayList<>();
    for (Instant instant = start; !instant.isAfter(end);
         instant = instant.plus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES)) {
      if (instant != start) {
        CompositeSLORecordBucket sloRecord =
            CompositeSLORecordBucket.builder()
                .verificationTaskId(verificationTaskId)
                .version(0)
                .runningBadCount(runningBadCount.get(index))
                .runningGoodCount(runningGoodCount.get(index))
                .sloVersion(0)
                .bucketStartTime(instant.minus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES))
                .build();
        sloRecords.add(sloRecord);
      }
      index += SLI_RECORD_BUCKET_SIZE;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }

  private List<CompositeSLORecordBucket> createSLORecordBuckets(
      Instant start, Instant end, Map<String, SLIRecordBucket> test) {
    List<CompositeSLORecordBucket> sloRecordBuckets = new ArrayList<>();
    for (Instant instant = start; !instant.isAfter(end); instant = instant.plus(5, ChronoUnit.MINUTES)) {
      if (instant != start) {
        CompositeSLORecordBucket sloRecord =
            CompositeSLORecordBucket.builder()
                .verificationTaskId(requestVerificationTaskId)
                .version(0)
                .runningBadCount(0)
                .runningGoodCount(0)
                .sloVersion(0)
                .scopedIdentifierSLIRecordBucketMap(test)
                .bucketStartTime(instant.minus(SLI_RECORD_BUCKET_SIZE, ChronoUnit.MINUTES))
                .build();
        sloRecordBuckets.add(sloRecord);
      }
    }
    hPersistence.save(sloRecordBuckets);
    return sloRecordBuckets;
  }
}
