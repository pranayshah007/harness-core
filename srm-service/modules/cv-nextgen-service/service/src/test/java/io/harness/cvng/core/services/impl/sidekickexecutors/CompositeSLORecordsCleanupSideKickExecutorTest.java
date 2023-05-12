/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.sidekick.CompositeSLORecordsCleanupSideKickData;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CompositeSLORecordsCleanupSideKickExecutorTest extends CvNextGenTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private CompositeSLORecordsCleanupSideKickExecutor sideKickExecutor;
  @Inject private Clock clock;
  @Mock private CompositeSLORecordsCleanupSideKickExecutor mockedSideKickExecutor;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private CompositeSLORecordService sloRecordService;
  private BuilderFactory builderFactory;
  private String verificationTaskId;
  private ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;
  private CompositeServiceLevelObjective compositeServiceLevelObjective;
  private Instant startTime;
  private Instant endTime;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    builderFactory.getContext().setProjectIdentifier("project");

    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    SimpleServiceLevelObjective simpleServiceLevelObjective1 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(MonitoredServiceDTO.Sources.builder().build())
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    SimpleServiceLevelObjective simpleServiceLevelObjective2 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

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
    verificationTaskId = compositeServiceLevelObjective.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
    createSLORecords(startTime, endTime, runningGoodCount, runningBadCount);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecute() throws IllegalAccessException {
    HPersistence hPersistenceSpy = spy(hPersistence);
    FieldUtils.writeField(sideKickExecutor, "hPersistence", hPersistenceSpy, true);
    String sloId = serviceLevelObjectiveV2Service
                       .getEntity(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier())
                       .getUuid();
    CompositeSLORecordsCleanupSideKickData sideKickData =
        builderFactory
            .getCompositeSLORecordsCleanupSideKickDataBuilder(sloId, TIME_FOR_TESTS.minus(15, ChronoUnit.MINUTES))
            .build();
    sideKickExecutor.execute(sideKickData);
    verify(hPersistenceSpy, times(1)).delete(any(Query.class));
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithLesserBatchSize() throws IllegalAccessException {
    HPersistence hPersistenceSpy = spy(hPersistence);
    doReturn(5L).when(mockedSideKickExecutor).getBatchSizeForDeletion();
    doCallRealMethod().when(mockedSideKickExecutor).execute(any());
    FieldUtils.writeField(mockedSideKickExecutor, "hPersistence", hPersistenceSpy, true);
    FieldUtils.writeField(mockedSideKickExecutor, "clock", clock, true);
    String sloId = serviceLevelObjectiveV2Service
                       .getEntity(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier())
                       .getUuid();
    CompositeSLORecordsCleanupSideKickData sideKickData =
        builderFactory
            .getCompositeSLORecordsCleanupSideKickDataBuilder(sloId, TIME_FOR_TESTS.minus(15, ChronoUnit.MINUTES))
            .build();
    mockedSideKickExecutor.execute(sideKickData);
    verify(hPersistenceSpy, times(3)).delete(any(Query.class));
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testShouldRetry() {
    SideKickExecutor.RetryData retryData = sideKickExecutor.shouldRetry(5);
    assertThat(retryData.isShouldRetry()).isTrue();
  }

  private void createSLORecords(
      Instant start, Instant end, List<Double> runningGoodCount, List<Double> runningBadCount) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(verificationTaskId)
                                         .sloId(compositeServiceLevelObjective.getUuid())
                                         .version(0)
                                         .runningBadCount(runningBadCount.get(index))
                                         .runningGoodCount(runningGoodCount.get(index))
                                         .sloVersion(0)
                                         .timestamp(instant)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
  }
}
