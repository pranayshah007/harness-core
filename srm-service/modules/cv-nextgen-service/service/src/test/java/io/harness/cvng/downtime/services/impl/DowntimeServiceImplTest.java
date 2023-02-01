/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.AffectedEntity;
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
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.RecurringDowntimeSpec;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.transformer.DowntimeSpecDetailsTransformer;
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
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesServiceMock, true);
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    monitoredServiceIdentifier = monitoredServiceDTO.getIdentifier();
    recurringDowntimeDTO = builderFactory.getRecurringDowntimeDTO();
    oneTimeDurationBasedDowntimeDTO = builderFactory.getOnetimeDurationBasedDowntimeDTO();
    oneTimeEndTimeBasedDowntimeDTO = builderFactory.getOnetimeEndTimeBasedDowntimeDTO();
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
  public void testCreateDowntimeFailureForInvalidEndTime() {
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
  public void testCreateDowntimeFailureForInvalidMonitoredService() {
    recurringDowntimeDTO.setEntityRefs(
        Collections.singletonList(EntityDetails.builder().entityRef("identifier").build()));
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
    recurringDowntimeDTO.setEntityRefs(
        Collections.singletonList(EntityDetails.builder().entityRef("service1_env1").build()));
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
    recurringDowntimeDTO.setEntityRefs(
        Collections.singletonList(EntityDetails.builder().entityRef("identifier").build()));
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
  public void testListView() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
    oneTimeDurationBasedDowntimeDTO.setEnabled(false);
    downtimeService.create(projectParams, recurringDowntimeDTO);
    downtimeService.create(projectParams, oneTimeDurationBasedDowntimeDTO);
    downtimeService.create(projectParams, oneTimeEndTimeBasedDowntimeDTO);

    List<Pair<Long, Long>> futureInstancesOfRecurringDTO =
        downtimeTransformerMap.get(recurringDowntimeDTO.getSpec().getType())
            .getStartAndEndTimesForFutureInstances(recurringDowntimeDTO.getSpec().getSpec());
    clock = Clock.fixed(
        Instant.ofEpochSecond(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().getStartTime()), ZoneId.of("UTC"));
    PageResponse<DowntimeListView> downtimeListViewPageResponse = downtimeService.list(
        projectParams, PageParams.builder().page(0).size(20).build(), new DowntimeDashboardFilter());
    assertThat(downtimeListViewPageResponse.getPageItemCount()).isEqualTo(3);
    assertThat(downtimeListViewPageResponse.getContent().size()).isEqualTo(3);

    assertThat(downtimeListViewPageResponse.getContent().get(0).getName())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getName());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getIdentifier())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getIdentifier());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getCategory())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getCategory());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getSpec())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getSpec());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getDescription())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.getDescription());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationType(DowntimeDurationType.MINUTES).durationValue(30).build());
    assertThat(downtimeListViewPageResponse.getContent().get(0).isEnabled())
        .isEqualTo(oneTimeEndTimeBasedDowntimeDTO.isEnabled());
    assertThat(downtimeListViewPageResponse.getContent().get(0).getDowntimeStatusDetails())
        .isEqualTo(
            DowntimeStatusDetails.builder()
                .status(DowntimeStatus.ACTIVE)
                .startTime(oneTimeEndTimeBasedDowntimeDTO.getSpec().getSpec().getStartTime())
                .endTime(((OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec)
                                                                             oneTimeEndTimeBasedDowntimeDTO.getSpec()
                                                                                 .getSpec())
                              .getSpec())
                             .getEndTime())
                .build());

    assertThat(downtimeListViewPageResponse.getContent().get(2).getName()).isEqualTo(recurringDowntimeDTO.getName());
    assertThat(downtimeListViewPageResponse.getContent().get(2).getDowntimeStatusDetails())
        .isEqualTo(DowntimeStatusDetails.builder()
                       .status(DowntimeStatus.ACTIVE)
                       .startTime(futureInstancesOfRecurringDTO.get(0).getLeft())
                       .endTime(futureInstancesOfRecurringDTO.get(0).getRight())
                       .build());

    assertThat(downtimeListViewPageResponse.getContent().get(1).getName())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getName());
    assertThat(downtimeListViewPageResponse.getContent().get(1).getDowntimeStatusDetails()).isEqualTo(null);
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
  public void testListHistory() throws IllegalAccessException {
    FieldUtils.writeField(
        downtimeService, "entityUnavailabilityStatusesService", entityUnavailabilityStatusesService, true);
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
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getAffectedEntities())
        .isEqualTo(Collections.singletonList(AffectedEntity.builder()
                                                 .serviceRef(builderFactory.getContext().getServiceIdentifier())
                                                 .envRef(builderFactory.getContext().getEnvIdentifier())
                                                 .build()));
    assertThat(downtimeHistoryViewPageResponse.getContent().get(0).getDuration())
        .isEqualTo(DowntimeDuration.builder().durationValue(30).durationType(DowntimeDurationType.MINUTES).build());

    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getName())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getName());
    assertThat(downtimeHistoryViewPageResponse.getContent().get(1).getIdentifier())
        .isEqualTo(oneTimeDurationBasedDowntimeDTO.getIdentifier());
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
