/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.AffectedEntity;
import io.harness.cvng.downtime.beans.AllEntitiesRule;
import io.harness.cvng.downtime.beans.DowntimeCategory;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeDashboardFilter;
import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.DowntimeDurationType;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.beans.DowntimeSpec;
import io.harness.cvng.downtime.beans.DowntimeStatus;
import io.harness.cvng.downtime.beans.DowntimeStatusDetails;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.beans.EntitiesRule;
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityIdentifiersRule;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.RecurringDowntimeSpec;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.transformer.DowntimeSpecDetailsTransformer;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DowntimeServiceImplTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;

  @Inject HPersistence hPersistence;
  @Inject private DowntimeService downtimeService;

  @Inject private MonitoredServiceService monitoredServiceService;

  @Inject private Clock clock;

  @Inject private Map<DowntimeType, DowntimeSpecDetailsTransformer> downtimeTransformerMap;

  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;
  @Mock private EntityUnavailabilityStatusesService entityUnavailabilityStatusesServiceMock;

  private ProjectParams projectParams;
  private String monitoredServiceIdentifier;

  private DowntimeDTO recurringDowntimeDTO;

  private DowntimeDTO oneTimeEndTimeBasedDowntimeDTO;

  private DowntimeDTO oneTimeDurationBasedDowntimeDTO;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    projectParams = builderFactory.getProjectParams();
    clock = CVNGTestConstants.FIXED_TIME_FOR_TESTS;
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.ONE_TIME), "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.RECURRING), "clock", clock, true);
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesServiceMock, true);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceIdentifier = monitoredServiceDTO.getIdentifier();
    recurringDowntimeDTO = builderFactory.getRecurringDowntimeDTO();
    oneTimeDurationBasedDowntimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    oneTimeEndTimeBasedDowntimeDTO = builderFactory.getOnetimeEndTimeBasedDowntimeDTO();
    testUserProvider.setActiveUser(EmbeddedUser.builder().name("user1").email("user1@harness.io").build());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateRecurringDowntimeSuccess() {
    DowntimeResponse response = downtimeService.create(projectParams, recurringDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateAllMonitoredServicesDowntimeSuccess() {
    recurringDowntimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    DowntimeResponse response = downtimeService.create(projectParams, recurringDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateOneTimeDurationBasedDowntimeSuccess() {
    DowntimeResponse response = downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(oneTimeDurationBasedDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(oneTimeDurationBasedDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(oneTimeDurationBasedDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, oneTimeDurationBasedDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, oneTimeDurationBasedDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateOneTimeEndTimeBasedDowntimeSuccess() {
    DowntimeResponse response = downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(oneTimeEndTimeBasedDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(oneTimeEndTimeBasedDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, oneTimeEndTimeBasedDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, oneTimeEndTimeBasedDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateRecurringDowntimeForStartTimeBeforeNowSuccess() {
    recurringDowntimeDTO.getSpec().getSpec().setStartTime(
        clock.instant().minus(10, ChronoUnit.MINUTES).getEpochSecond());
    DowntimeResponse response = downtimeService.create(projectParams, recurringDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateOneTimeDurationBasedDowntimeForStartTimeBeforeNowSuccess() {
    oneTimeDurationBasedDowntimeDTO.getSpec().getSpec().setStartTime(
        clock.instant().minus(10, ChronoUnit.MINUTES).getEpochSecond());
    DowntimeResponse response = downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(oneTimeDurationBasedDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(oneTimeDurationBasedDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(oneTimeDurationBasedDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, oneTimeDurationBasedDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, oneTimeDurationBasedDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateOneTimeEndTimeBasedDowntimeForStartTimeBeforeNowSuccess() {
    oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().setStartTime(
        clock.instant().minus(10, ChronoUnit.MINUTES).getEpochSecond());
    DowntimeResponse response = downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(oneTimeEndTimeBasedDowntimeDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(oneTimeEndTimeBasedDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, oneTimeEndTimeBasedDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, oneTimeEndTimeBasedDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).create(any(), any());
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateDowntimeFailureForDuplicateEntity() {
    DowntimeResponse response = downtimeService.create(projectParams, recurringDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    assertThatThrownBy(() -> downtimeService.create(projectParams, recurringDowntimeDTO))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format(
            "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s  is already present.",
            recurringDowntimeDTO.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateRecurringDowntimeFailureForInvalidEndTime() {
    DowntimeSpec recurringDowntimeDTOSpec = recurringDowntimeDTO.getSpec().getSpec();
    long endTime = recurringDowntimeDTOSpec.getStartTime() + Duration.ofDays(3 * 365 + 1).toSeconds();
    ((RecurringDowntimeSpec) recurringDowntimeDTOSpec).setRecurrenceEndTime(endTime);
    recurringDowntimeDTO.getSpec().setSpec(recurringDowntimeDTOSpec);
    assertThatThrownBy(() -> downtimeService.create(projectParams, recurringDowntimeDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("EndTime can't be more than 3 years from now.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateOnetimeEndtimeDowntimeFailureForInvalidEndTime() {
    DowntimeSpec onetimeSpec = oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec();
    long endTime = onetimeSpec.getStartTime() + Duration.ofDays(3 * 365 + 1).toSeconds();
    ((OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec) onetimeSpec).getSpec()).setEndTime(endTime);
    oneTimeEndTimeBasedDowntimeDTO.getSpec().setSpec(onetimeSpec);
    assertThatThrownBy(() -> downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("EndTime can't be more than 3 years from now.");
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateOnetimeDurationsDowntimeFailureForInvalidEndTime() {
    DowntimeSpec onetimeSpec = oneTimeDurationBasedDowntimeDTO.getSpec().getSpec();
    ((OnetimeDowntimeSpec.OnetimeDurationBasedSpec) ((OnetimeDowntimeSpec) onetimeSpec).getSpec())
        .setDowntimeDuration(
            DowntimeDuration.builder().durationType(DowntimeDurationType.DAYS).durationValue(3 * 365 + 1).build());
    oneTimeDurationBasedDowntimeDTO.getSpec().setSpec(onetimeSpec);
    assertThatThrownBy(() -> downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("EndTime can't be more than 3 years from now.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateDowntimeFailureForInvalidMonitoredService() {
    recurringDowntimeDTO.setEntitiesRule(EntityIdentifiersRule.builder()
                                             .entityIdentifiers(Collections.singletonList(
                                                 EntityDetails.builder().enabled(true).entityRef("identifier").build()))
                                             .build());
    assertThatThrownBy(() -> downtimeService.create(projectParams, recurringDowntimeDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Monitored Service identifier for account %s, org %s, and project %s are not present.",
                projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateDowntimeFailureForNoMonitoredService() {
    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder().entityIdentifiers(Collections.emptyList()).build());
    assertThatThrownBy(() -> downtimeService.create(projectParams, recurringDowntimeDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No Monitored services added");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateDowntimeFailureForDuplicateMonitoredService() {
    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(List.of(EntityDetails.builder()
                                           .entityRef(builderFactory.getContext().getMonitoredServiceIdentifier())
                                           .enabled(true)
                                           .build(),
                EntityDetails.builder()
                    .entityRef(builderFactory.getContext().getMonitoredServiceIdentifier())
                    .enabled(true)
                    .build()))
            .build());
    assertThatThrownBy(() -> downtimeService.create(projectParams, recurringDowntimeDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Duplicate Monitored services added");
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteSuccess() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    boolean response = downtimeService.delete(projectParams, recurringDowntimeDTO.getIdentifier());
    assertThat(response).isEqualTo(true);
    verify(entityUnavailabilityStatusesServiceMock)
        .deleteFutureDowntimeInstances(projectParams, recurringDowntimeDTO.getIdentifier());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteOnetimeSuccess() {
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    boolean response = downtimeService.delete(projectParams, oneTimeEndTimeBasedDowntimeDTO.getIdentifier());
    assertThat(response).isEqualTo(true);
    verify(entityUnavailabilityStatusesServiceMock)
        .deleteFutureDowntimeInstances(projectParams, oneTimeEndTimeBasedDowntimeDTO.getIdentifier());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteSuccessForOnlyFutureInstances() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(
            projectParams, EntityType.MAINTENANCE_WINDOW, recurringDowntimeDTO.getIdentifier());
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    clock = Clock.fixed(
        Instant.ofEpochSecond(recurringDowntimeDTO.getSpec().getSpec().getStartTime()).minus(1, ChronoUnit.DAYS),
        ZoneId.of("UTC"));
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    boolean response = downtimeService.delete(projectParams, recurringDowntimeDTO.getIdentifier());
    assertThat(response).isEqualTo(true);
    entityUnavailabilityStatusesDTOS = entityUnavailabilityStatusesService.getAllInstances(
        projectParams, EntityType.MAINTENANCE_WINDOW, recurringDowntimeDTO.getIdentifier());
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteFailureForActiveInstances() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(
            projectParams, EntityType.MAINTENANCE_WINDOW, oneTimeEndTimeBasedDowntimeDTO.getIdentifier());
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0).getStartTime())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().getStartTime());
    assertThat(entityUnavailabilityStatusesDTOS.get(0).getEndTime())
        .isEqualTo((
            (OnetimeDowntimeSpec
                    .OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec) oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec())
                .getSpec())
                       .getEndTime());
    clock = Clock.fixed(Instant.ofEpochSecond(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().getStartTime())
                            .plus(10, ChronoUnit.MINUTES),
        ZoneId.of("UTC"));
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    assertThatThrownBy(() -> downtimeService.delete(projectParams, oneTimeEndTimeBasedDowntimeDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can't be deleted, as it has a a past/current instance of downtime, where deleting it can impact SLO adversely.",
            oneTimeEndTimeBasedDowntimeDTO.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteFailureForActiveRecurringInstances() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(
            projectParams, EntityType.MAINTENANCE_WINDOW, recurringDowntimeDTO.getIdentifier());
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    clock = Clock.fixed(
        Instant.ofEpochSecond(entityUnavailabilityStatusesDTOS.get(1).getStartTime()).plus(10, ChronoUnit.MINUTES),
        ZoneId.of("UTC"));
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    assertThatThrownBy(() -> downtimeService.delete(projectParams, recurringDowntimeDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can't be deleted, as it has a a past/current instance of downtime, where deleting it can impact SLO adversely.",
            recurringDowntimeDTO.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteFailure() {
    assertThatThrownBy(() -> downtimeService.delete(projectParams, "identifier"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Downtime with identifier identifier, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
            projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeSuccess() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    recurringDowntimeDTO.setName("New Downtime");
    recurringDowntimeDTO.setDescription("New description");
    recurringDowntimeDTO.setCategory(DowntimeCategory.DEPLOYMENT);
    recurringDowntimeDTO.setSpec(oneTimeDurationBasedDowntimeDTO.getSpec());
    recurringDowntimeDTO.setTags(new HashMap<>());
    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(
                Collections.singletonList(EntityDetails.builder().enabled(true).entityRef("service1_env1").build()))
            .build());
    DowntimeResponse response =
        downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO);
    futureInstances = downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
                          .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).update(any(), any(), any());
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateFailureForInvalidEndtimeDowntimeFailure() {
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    ((RecurringDowntimeSpec) recurringDowntimeDTO.getSpec().getSpec())
        .setRecurrenceEndTime(clock.instant().plus(3 * 365 + 1, ChronoUnit.DAYS).getEpochSecond());
    assertThatThrownBy(
        () -> downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("EndTime can't be more than 3 years from now.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateFailureForNoMonitoredServiceDowntimeFailure() {
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder().entityIdentifiers(Collections.emptyList()).build());
    assertThatThrownBy(
        () -> downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("No Monitored services added"));
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateEntitiesRuleInDowntimeHistoryWithActiveInstanceReturnsPreviousSuccess()
      throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    EntitiesRule prevRule = AllEntitiesRule.builder().build();
    recurringDowntimeDTO.setEntitiesRule(prevRule);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(
            projectParams, EntityType.MAINTENANCE_WINDOW, recurringDowntimeDTO.getIdentifier());
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    clock = Clock.fixed(
        Instant.ofEpochSecond(entityUnavailabilityStatusesDTOS.get(1).getStartTime()).plus(10, ChronoUnit.MINUTES),
        ZoneId.of("UTC"));
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.ONE_TIME), "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.RECURRING), "clock", clock, true);

    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(
                Collections.singletonList(EntityDetails.builder().enabled(true).entityRef("service1_env1").build()))
            .build());
    DowntimeResponse response =
        downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    PageResponse<DowntimeHistoryView> downtimeHistoryViewPageResponse = downtimeService.history(
        projectParams, PageParams.builder().page(0).size(20).build(), new DowntimeDashboardFilter());
    assertThat(downtimeHistoryViewPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().size()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getIdentifier())
        .isEqualTo(recurringDowntimeDTO.getIdentifier());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getCategory())
        .isEqualTo(recurringDowntimeDTO.getCategory());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getStartTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getEndTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime() + Duration.ofMinutes(30).toSeconds());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getSpec()).isEqualTo(recurringDowntimeDTO.getSpec());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getAffectedEntities().get(0))
        .isEqualTo(prevRule.getAffectedEntity().get());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationValue(30).durationType(DowntimeDurationType.MINUTES).build());

    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getStartTime())
        .isEqualTo(entityUnavailabilityStatusesDTOS.get(1).getStartTime());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getEndTime()).isEqualTo(clock.millis() / 1000);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getAffectedEntities().get(0))
        .isEqualTo(prevRule.getAffectedEntity().get());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDisabledDowntimeHistoryWithActiveInstanceReturnsPreviousSuccess() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    EntitiesRule prevRule = AllEntitiesRule.builder().build();
    recurringDowntimeDTO.setEntitiesRule(prevRule);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(
            projectParams, EntityType.MAINTENANCE_WINDOW, recurringDowntimeDTO.getIdentifier());
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    clock = Clock.fixed(
        Instant.ofEpochSecond(entityUnavailabilityStatusesDTOS.get(1).getStartTime()).plus(10, ChronoUnit.MINUTES),
        ZoneId.of("UTC"));
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.ONE_TIME), "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.RECURRING), "clock", clock, true);

    DowntimeResponse response =
        downtimeService.enableOrDisable(projectParams, recurringDowntimeDTO.getIdentifier(), false);
    recurringDowntimeDTO.setEnabled(false);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    PageResponse<DowntimeHistoryView> downtimeHistoryViewPageResponse = downtimeService.history(
        projectParams, PageParams.builder().page(0).size(20).build(), new DowntimeDashboardFilter());
    assertThat(downtimeHistoryViewPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().size()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getIdentifier())
        .isEqualTo(recurringDowntimeDTO.getIdentifier());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getCategory())
        .isEqualTo(recurringDowntimeDTO.getCategory());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getStartTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getEndTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime() + Duration.ofMinutes(30).toSeconds());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getSpec()).isEqualTo(recurringDowntimeDTO.getSpec());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getAffectedEntities().get(0))
        .isEqualTo(prevRule.getAffectedEntity().get());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationValue(30).durationType(DowntimeDurationType.MINUTES).build());

    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getStartTime())
        .isEqualTo(entityUnavailabilityStatusesDTOS.get(1).getStartTime());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getEndTime()).isEqualTo(clock.millis() / 1000);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getAffectedEntities().get(0))
        .isEqualTo(prevRule.getAffectedEntity().get());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationValue(10).durationType(DowntimeDurationType.MINUTES).build());
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateEntitiesRuleInDowntimeHistoryReturnsPreviousSuccess() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    EntitiesRule prevRule = AllEntitiesRule.builder().build();
    recurringDowntimeDTO.setEntitiesRule(prevRule);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    clock = Clock.fixed(
        Instant.ofEpochSecond(recurringDowntimeDTO.getSpec().getSpec().getStartTime()).plus(7, ChronoUnit.DAYS),
        ZoneId.of("UTC"));
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.ONE_TIME), "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.RECURRING), "clock", clock, true);

    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(
                Collections.singletonList(EntityDetails.builder().enabled(true).entityRef("service1_env1").build()))
            .build());
    DowntimeResponse response =
        downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
    PageResponse<DowntimeHistoryView> downtimeHistoryViewPageResponse = downtimeService.history(
        projectParams, PageParams.builder().page(0).size(20).build(), new DowntimeDashboardFilter());
    assertThat(downtimeHistoryViewPageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(downtimeHistoryViewPageResponse.getContent().size()).isEqualTo(1);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getIdentifier())
        .isEqualTo(recurringDowntimeDTO.getIdentifier());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getCategory())
        .isEqualTo(recurringDowntimeDTO.getCategory());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getStartTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getEndTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime() + Duration.ofMinutes(30).toSeconds());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getSpec()).isEqualTo(recurringDowntimeDTO.getSpec());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getAffectedEntities().get(0))
        .isEqualTo(prevRule.getAffectedEntity().get());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationValue(30).durationType(DowntimeDurationType.MINUTES).build());
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeWithALLEntitiesSuccess() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    recurringDowntimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    recurringDowntimeDTO.setName("New Downtime");
    recurringDowntimeDTO.setDescription("New description");
    recurringDowntimeDTO.setCategory(DowntimeCategory.DEPLOYMENT);
    recurringDowntimeDTO.setSpec(oneTimeDurationBasedDowntimeDTO.getSpec());
    recurringDowntimeDTO.setTags(new HashMap<>());
    recurringDowntimeDTO.setEntitiesRule(
        EntityIdentifiersRule.builder()
            .entityIdentifiers(
                Collections.singletonList(EntityDetails.builder().enabled(true).entityRef("service1_env1").build()))
            .build());
    DowntimeResponse response =
        downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO);
    futureInstances = downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
                          .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).update(any(), any(), any());
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testEnableDowntimeSuccess() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    recurringDowntimeDTO.setEnabled(false);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    DowntimeResponse response =
        downtimeService.enableOrDisable(projectParams, recurringDowntimeDTO.getIdentifier(), true);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    recurringDowntimeDTO.setEnabled(true);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock).update(any(), any(), any());
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDisableDowntimeSuccess() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder()
                                                  .serviceRef("service1")
                                                  .environmentRef("env1")
                                                  .identifier("service1_env1")
                                                  .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    recurringDowntimeDTO.setEnabled(true);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    DowntimeResponse response =
        downtimeService.enableOrDisable(projectParams, recurringDowntimeDTO.getIdentifier(), false);
    List<Pair<Long, Long>> futureInstances =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
            projectParams, recurringDowntimeDTO, futureInstances);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(53);
    verify(entityUnavailabilityStatusesServiceMock)
        .getEntityUnavaialabilityStatusesDTOs(projectParams, recurringDowntimeDTO, futureInstances);
    verify(entityUnavailabilityStatusesServiceMock)
        .deleteFutureDowntimeInstances(projectParams, recurringDowntimeDTO.getIdentifier());
    recurringDowntimeDTO.setEnabled(false);
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeFailureForAbsentIdentifier() {
    assertThatThrownBy(
        () -> downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
            recurringDowntimeDTO.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeFailureForInvalidEndTime() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    DowntimeSpec recurringDowntimeDTOSpec = recurringDowntimeDTO.getSpec().getSpec();
    long endTime = recurringDowntimeDTOSpec.getStartTime() + Duration.ofDays(3 * 365 + 1).toSeconds();
    ((RecurringDowntimeSpec) recurringDowntimeDTOSpec).setRecurrenceEndTime(endTime);
    recurringDowntimeDTO.getSpec().setSpec(recurringDowntimeDTOSpec);
    assertThatThrownBy(
        () -> downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("EndTime can't be more than 3 years from now.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeFailureForInvalidMonitoredService() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    recurringDowntimeDTO.setEntitiesRule(EntityIdentifiersRule.builder()
                                             .entityIdentifiers(Collections.singletonList(
                                                 EntityDetails.builder().enabled(true).entityRef("identifier").build()))
                                             .build());
    assertThatThrownBy(
        () -> downtimeService.update(projectParams, recurringDowntimeDTO.getIdentifier(), recurringDowntimeDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Monitored Service identifier for account %s, org %s, and project %s are not present.",
                projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testListView() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    oneTimeEndTimeBasedDowntimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().setStartTime(
        clock.instant().plus(5, ChronoUnit.MINUTES).getEpochSecond());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);

    List<Pair<Long, Long>> futureInstancesOfRecurringDTO =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    clock =
        Clock.fixed(Instant.ofEpochSecond(recurringDowntimeDTO.getSpec().getSpec().getStartTime()), ZoneId.of("UTC"));
    PageResponse<DowntimeListView> downtimeListViewPageResponse = downtimeService.list(
        projectParams, PageParams.builder().page(0).size(20).build(), new DowntimeDashboardFilter());
    assertThat(downtimeListViewPageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(downtimeListViewPageResponse.getContent().size()).isEqualTo(3);

    assertThat(downtimeListViewPageResponse.getContent().get(0).getName())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getName());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getLastModified().getLastModifiedBy())
        .isEqualTo(testUserProvider.activeUser().getEmail());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getIdentifier())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getIdentifier());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getCategory())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getCategory());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getSpec())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getSpec());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getDescription())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getDescription());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationType(DowntimeDurationType.MINUTES).durationValue(25).build());
    assertThat(downtimeListViewPageResponse.getContent().get(0).isEnabled())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.isEnabled());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getDowntimeStatusDetails())
        .isEqualTo(
            DowntimeStatusDetails.builder()
                .status(DowntimeStatus.SCHEDULED)
                .startTime(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().getStartTime())
                .endTime(((OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec)
                                                                             oneTimeEndTimeBasedDowntimeDTO.getSpec()
                                                                                 .getSpec())
                              .getSpec())
                             .getEndTime())
                .build());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getPastOrActiveInstancesCount()).isEqualTo(0);

    assertThat(downtimeListViewPageResponse.getContent().get(2).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeListViewPageResponse.getContent().get(2).getDowntimeStatusDetails())
        .isEqualTo(DowntimeStatusDetails.builder()
                       .status(DowntimeStatus.ACTIVE)
                       .startTime(futureInstancesOfRecurringDTO.get(0).getLeft())
                       .endTime(futureInstancesOfRecurringDTO.get(0).getRight())
                       .build());

    assertThat(downtimeListViewPageResponse.getContent().get(1).getName())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getName());
    assertThat(downtimeListViewPageResponse.getContent().get(1).getPastOrActiveInstancesCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testListViewFilters() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    DowntimeDashboardFilter filter1 = new DowntimeDashboardFilter();
    filter1.setSearchFilter(recurringDowntimeDTO.getName());
    PageResponse<DowntimeListView> downtimeListViewPageResponse =
        downtimeService.list(projectParams, PageParams.builder().page(0).size(20).build(), filter1);
    assertThat(downtimeListViewPageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(downtimeListViewPageResponse.getContent().size()).isEqualTo(1);
    assertThat(downtimeListViewPageResponse.getContent().get(0).getName()).isEqualTo(recurringDowntimeDTO.getName());

    DowntimeDashboardFilter filter2 = new DowntimeDashboardFilter();
    filter2.setMonitoredServiceIdentifier(monitoredServiceIdentifier);
    downtimeListViewPageResponse =
        downtimeService.list(projectParams, PageParams.builder().page(0).size(20).build(), filter2);
    assertThat(downtimeListViewPageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(downtimeListViewPageResponse.getContent().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGet() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    DowntimeResponse response = downtimeService.get(projectParams, recurringDowntimeDTO.getIdentifier());
    assertThat(response.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetAssociatedMonitoredServices() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    List<MonitoredServiceDetail> response =
        downtimeService.getAssociatedMonitoredServices(projectParams, recurringDowntimeDTO.getIdentifier());
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0).getServiceName()).isEqualTo("Mocked service name");
    assertThat(response.get(0).getEnvironmentName()).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetAssociatedMonitoredServicesForAllMSDowntime() {
    recurringDowntimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    List<MonitoredServiceDetail> response =
        downtimeService.getAssociatedMonitoredServices(projectParams, recurringDowntimeDTO.getIdentifier());
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0).getServiceName()).isEqualTo("Mocked service name");
    assertThat(response.get(0).getEnvironmentName()).isEqualTo("Mocked env name");
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testListHistory() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    recurringDowntimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);

    clock = Clock.fixed(clock.instant().plus(7, ChronoUnit.DAYS), clock.getZone());
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    PageResponse<DowntimeHistoryView> downtimeHistoryViewPageResponse = downtimeService.history(
        projectParams, PageParams.builder().page(0).size(20).build(), new DowntimeDashboardFilter());
    assertThat(downtimeHistoryViewPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().size()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getIdentifier())
        .isEqualTo(recurringDowntimeDTO.getIdentifier());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getCategory())
        .isEqualTo(recurringDowntimeDTO.getCategory());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getStartTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getEndTime())
        .isEqualTo(recurringDowntimeDTO.getSpec().getSpec().getStartTime() + Duration.ofMinutes(30).toSeconds());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getSpec()).isEqualTo(recurringDowntimeDTO.getSpec());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getAffectedEntities().get(0))
        .isEqualTo(recurringDowntimeDTO.getEntitiesRule().getAffectedEntity().get());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationValue(30).durationType(DowntimeDurationType.MINUTES).build());

    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getName())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getIdentifier())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getIdentifier());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getAffectedEntities())
        .isEqualTo(Collections.singletonList(
            AffectedEntity.builder()
                .serviceName("Mocked service name")
                .envName("Mocked env name")
                .monitoredServiceIdentifier(builderFactory.getContext().getMonitoredServiceIdentifier())
                .build()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testListHistoryWithFilters() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);

    clock = Clock.fixed(clock.instant().plus(7, ChronoUnit.DAYS), clock.getZone());
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);

    DowntimeDashboardFilter filter1 = new DowntimeDashboardFilter();
    filter1.setSearchFilter(recurringDowntimeDTO.getName());
    PageResponse<DowntimeHistoryView> downtimeHistoryViewPageResponse =
        downtimeService.history(projectParams, PageParams.builder().page(0).size(20).build(), filter1);
    assertThat(downtimeHistoryViewPageResponse.getPageItemCount()).isEqualTo(1);
    assertThat(downtimeHistoryViewPageResponse.getContent().size()).isEqualTo(1);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getName()).isEqualTo(recurringDowntimeDTO.getName());

    DowntimeDashboardFilter filter2 = new DowntimeDashboardFilter();
    filter2.setMonitoredServiceIdentifier(monitoredServiceIdentifier);
    downtimeHistoryViewPageResponse =
        downtimeService.history(projectParams, PageParams.builder().page(0).size(20).build(), filter2);
    assertThat(downtimeHistoryViewPageResponse.getPageItemCount()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().size()).isEqualTo(2);
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getName())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getName());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByProjectIdentifier_Success() {
    DowntimeService mockDowntimeService = spy(downtimeService);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    mockDowntimeService.deleteByProjectIdentifier(Downtime.class, projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    verify(mockDowntimeService, times(2)).delete(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByOrgIdentifier_Success() {
    DowntimeService mockDowntimeService = spy(downtimeService);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    mockDowntimeService.deleteByOrgIdentifier(
        Downtime.class, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier());
    verify(mockDowntimeService, times(2)).delete(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByAccountIdentifier_Success() {
    DowntimeService mockDowntimeService = spy(downtimeService);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);
    mockDowntimeService.deleteByAccountIdentifier(Downtime.class, projectParams.getAccountIdentifier());
    verify(mockDowntimeService, times(2)).delete(any(), any());
  }
}
