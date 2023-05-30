/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.beans.DataCollectionExecutionStatus.QUEUED;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.SLIDataCollectionTask;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLOHealthIndicatorServiceImplTest extends CvNextGenTestBase {
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject SLIRecordService sliRecordService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject MonitoredServiceService monitoredServiceService;

  @Inject HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DataCollectionTaskService dataCollectionTaskService;

  @Inject Clock clock;

  private BuilderFactory builderFactory;
  private String monitoredServiceIdentifier;
  private String sliId;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    sliId = "sloIdentifier_metric1";
    createMonitoredService();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testupsert_insertSuccess() {
    ProjectParams projectParams = builderFactory.getProjectParams();
    SimpleServiceLevelObjective serviceLevelObjective = builderFactory.getSimpleServiceLevelObjectiveBuilder().build();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = builderFactory.getServiceLevelIndicatorDTOBuilder();
    createAndSaveSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjective.getIdentifier());
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    SLOHealthIndicator newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    assertThat(newSLOHealthIndicator.getServiceLevelObjectiveIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(newSLOHealthIndicator.getFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testupsert_insertSuccess_ForFailedStateWithDCfailures() {
    ProjectParams projectParams = builderFactory.getProjectParams();
    SimpleServiceLevelObjective serviceLevelObjective = builderFactory.getSimpleServiceLevelObjectiveBuilder().build();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = builderFactory.getServiceLevelIndicatorDTOBuilder();
    createAndSaveSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjective.getIdentifier());
    DataCollectionTask dataCollectionTask =
        SLIDataCollectionTask.builder()
            .verificationTaskId(serviceLevelIndicatorService.getServiceLevelIndicator(projectParams, sliId).getUuid())
            .dataCollectionWorkerId(generateUuid())
            .type(DataCollectionTask.Type.SLI)
            .accountId(projectParams.getAccountIdentifier())
            .startTime(clock.instant().minus(Duration.ofMinutes(7)))
            .endTime(clock.instant().minus(Duration.ofMinutes(2)))
            .status(QUEUED)
            .lastPickedAt(clock.instant().minus(Duration.ofMinutes(5)))
            .build();
    hPersistence.save(dataCollectionTask);
    dataCollectionTask.setUuid(generateUuid());
    hPersistence.save(dataCollectionTask);
    dataCollectionTask.setUuid(generateUuid());
    dataCollectionTask.setStatus(DataCollectionExecutionStatus.QUEUED);
    hPersistence.save(dataCollectionTask);

    sloHealthIndicatorService.upsert(serviceLevelObjective);
    SLOHealthIndicator newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    assertThat(newSLOHealthIndicator.getServiceLevelObjectiveIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    assertThat(newSLOHealthIndicator.getFailedState()).isEqualTo(false);

    dataCollectionTask.setUuid(generateUuid());
    dataCollectionTask.setStatus(DataCollectionExecutionStatus.FAILED);
    hPersistence.save(dataCollectionTask);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getFailedState()).isEqualTo(true);

    dataCollectionTask.setUuid(generateUuid());
    dataCollectionTask.setStatus(DataCollectionExecutionStatus.SUCCESS);
    hPersistence.save(dataCollectionTask);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getFailedState()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testupsert_insertSuccess_afterSLIUpdate() {
    ProjectParams projectParams = builderFactory.getProjectParams();
    SimpleServiceLevelObjective serviceLevelObjective = builderFactory.getSimpleServiceLevelObjectiveBuilder().build();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = builderFactory.getServiceLevelIndicatorDTOBuilder();
    createAndSaveSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjective.getIdentifier());
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    SLOHealthIndicator newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    assertThat(newSLOHealthIndicator.getServiceLevelObjectiveIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);

    String newMonitoredServiceIdentifier = "new_identifier";

    serviceLevelObjective.setMonitoredServiceIdentifier(newMonitoredServiceIdentifier);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    assertThat(newSLOHealthIndicator.getServiceLevelObjectiveIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getMonitoredServiceIdentifier())
        .isEqualTo(serviceLevelObjective.getMonitoredServiceIdentifier());
    assertThat(newSLOHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testupsert_insertSuccess_withDummySLIRecords() {
    ProjectParams projectParams = builderFactory.getProjectParams();
    SimpleServiceLevelObjective serviceLevelObjective = builderFactory.getSimpleServiceLevelObjectiveBuilder().build();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = builderFactory.getServiceLevelIndicatorDTOBuilder();
    serviceLevelIndicatorDTO.setIdentifier(sliId);
    createAndSaveSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjective.getIdentifier());
    String sliIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(), sliId).getUuid();
    insertDummySLIRecords(50, 50, clock.instant().minus(1, ChronoUnit.DAYS),
        clock.instant().minus(10, ChronoUnit.MINUTES), sliIndicator, sliId, 0);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    SLOHealthIndicator newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    assertThat(newSLOHealthIndicator.getServiceLevelObjectiveIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
    assertThat(newSLOHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
    // 8640 total minutes(30*24*60*0.2) - 50 bad minutes
    assertThat(newSLOHealthIndicator.getErrorBudgetRemainingMinutes()).isEqualTo(8590);
    assertThat(newSLOHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo((8590.0 * 100) / 8640.0);
  }

  private void createAndSaveSLI(ProjectParams projectParams, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO,
      String serviceLevelObjectiveIdentifier) {
    serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
        serviceLevelObjectiveIdentifier, monitoredServiceIdentifier, generateUuid());
  }

  private void insertDummySLIRecords(int numOfGoodRecords, int numOfBadReocrds, Instant startTime, Instant endTime,
      String sliId, String verificationTaskId, int sliVersion) {
    List<SLIState> sliStateList = new ArrayList<>();

    Duration increment = Duration.between(startTime, endTime);

    increment = increment.dividedBy(numOfBadReocrds + numOfGoodRecords + 1);

    for (int i = 0; i < numOfGoodRecords; i++) {
      sliStateList.add(GOOD);
    }

    for (int i = 0; i < numOfBadReocrds; i++) {
      sliStateList.add(BAD);
    }

    sliRecordService.create(
        getSLIRecordParams(startTime, sliStateList, increment), sliId, verificationTaskId, sliVersion);
  }

  private List<SLIRecordParam> getSLIRecordParams(Instant startTime, List<SLIState> sliStates, Duration increment) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (SLIState sliState : sliStates) {
      long goodCount = 0;
      long badCount = 0;
      if (sliState == GOOD) {
        goodCount++;
      } else if (sliState == BAD) {
        badCount++;
      }
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime)
                              .goodEventCount(goodCount)
                              .badEventCount(badCount)
                              .build());
      startTime = startTime.plus(increment.toMinutes(), ChronoUnit.MINUTES);
    }
    return sliRecordParams;
  }

  private MonitoredService createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    return monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .build());
  }
}
