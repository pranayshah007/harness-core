/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.utils.ScopedInformation.getScopedInformation;

import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.downtime.beans.DowntimeStatusDetails;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLOConsumptionBreakdown;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLODashboardWidgetBuilder;
import io.harness.cvng.servicelevelobjective.beans.SLOError;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorType;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsRefDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyDTO;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.entities.UserJourney;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.SecondaryEventDetailsService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.services.api.UserJourneyService;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SLODashboardServiceImpl implements SLODashboardService {
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private GraphDataService graphDataService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private Clock clock;
  @Inject private NextGenService nextGenService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;
  @Inject private UserJourneyService userJourneyService;

  @Inject private AnnotationService annotationService;
  @Inject private DowntimeService downtimeService;
  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;
  @Inject private SRMAnalysisStepService srmAnalysisStepService;

  @Inject private Map<SecondaryEventsType, SecondaryEventDetailsService> secondaryEventsTypeToDetailsMapBinder;

  @Override
  public PageResponse<SLOHealthListView> getSloHealthListView(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    PageResponse<AbstractServiceLevelObjective> sloPageResponse =
        serviceLevelObjectiveV2Service.getSLOForListView(projectParams, filter, pageParams);

    Set<String> monitoredServiceIdentifiers =
        sloPageResponse.getContent()
            .stream()
            .filter(slo -> slo.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .map(slo -> ((SimpleServiceLevelObjective) slo).getMonitoredServiceIdentifier())
            .collect(Collectors.toSet());
    Map<String, EntityUnavailabilityStatusesDTO> monitoredServiceIdentifierToUnavailabilityStatusesDTOMap =
        downtimeService.getMonitoredServicesAssociatedUnavailabilityInstanceMap(
            projectParams, monitoredServiceIdentifiers);
    Map<String, AbstractServiceLevelObjective> scopedIdentifierToAllSLOsIncUnderlyingSLOsMap =
        getScopedIdentifierToAllSLOsIncUnderlyingSLOsMap(sloPageResponse.getContent(), projectParams);
    Map<String, MonitoredServiceDTO> scopedMonitoredServiceIdentifierToDTOMap =
        getScopedMonitoredServiceIdentifierToDTOMap(sloPageResponse.getContent(), projectParams);
    Map<String, SLOHealthIndicator> scopedSloIdentifierToHealthIndicatorMap =
        getScopedSloIdentifierToHealthIndicatorMap(
            scopedIdentifierToAllSLOsIncUnderlyingSLOsMap.values().stream().collect(Collectors.toList()),
            projectParams);
    Map<String, SLOError> scopedIdentifierToSLOErrorMap = getScopedIdentifierToSLOErrorMap(sloPageResponse.getContent(),
        scopedIdentifierToAllSLOsIncUnderlyingSLOsMap, scopedSloIdentifierToHealthIndicatorMap);
    List<UserJourney> userJourneyList = userJourneyService.get(projectParams);
    Map<String, String> userJourneyIdentifierToNameMap =
        userJourneyList.stream().collect(Collectors.toMap(UserJourney::getIdentifier, UserJourney::getName));

    List<SLOHealthListView> sloWidgets =
        sloPageResponse.getContent()
            .stream()
            .map(sloResponse
                -> getSLOListView(projectParams, sloResponse, scopedMonitoredServiceIdentifierToDTOMap,
                    scopedSloIdentifierToHealthIndicatorMap, userJourneyIdentifierToNameMap,
                    monitoredServiceIdentifierToUnavailabilityStatusesDTOMap, scopedIdentifierToSLOErrorMap))
            .collect(Collectors.toList());

    return PageResponse.<SLOHealthListView>builder()
        .pageSize(sloPageResponse.getPageSize())
        .pageIndex(sloPageResponse.getPageIndex())
        .totalPages(sloPageResponse.getTotalPages())
        .totalItems(sloPageResponse.getTotalItems())
        .pageItemCount(sloPageResponse.getPageItemCount())
        .content(sloWidgets)
        .build();
  }

  private Map<String, MonitoredServiceDTO> getScopedMonitoredServiceIdentifierToDTOMap(
      List<AbstractServiceLevelObjective> serviceLevelObjectives, ProjectParams projectParams) {
    Set<String> monitoredServiceIdentifiers =
        serviceLevelObjectives.stream()
            .filter(slo -> slo.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .map(slo -> ((SimpleServiceLevelObjective) slo).getMonitoredServiceIdentifier())
            .collect(Collectors.toSet());
    Set<String> scopedMonitoredServices =
        serviceLevelObjectives.stream()
            .filter(slo -> slo.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .map(slo
                -> getScopedIdentifier(slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
                    ((SimpleServiceLevelObjective) slo).getMonitoredServiceIdentifier()))
            .collect(Collectors.toSet());
    List<MonitoredServiceResponse> monitoredServicesFromIdentifiers =
        monitoredServiceService.get(projectParams.getAccountIdentifier(), monitoredServiceIdentifiers);
    List<MonitoredServiceResponse> monitoredServicesFromScopedIdentifiers =
        monitoredServicesFromIdentifiers.stream()
            .filter(monitoredService
                -> scopedMonitoredServices.contains(getScopedIdentifier(projectParams.getAccountIdentifier(),
                    monitoredService.getMonitoredServiceDTO().getOrgIdentifier(),
                    monitoredService.getMonitoredServiceDTO().getProjectIdentifier(),
                    monitoredService.getMonitoredServiceDTO().getIdentifier())))
            .collect(Collectors.toList());
    return monitoredServicesFromScopedIdentifiers.stream()
        .map(MonitoredServiceResponse::getMonitoredServiceDTO)
        .collect(Collectors.toMap(monitoredServiceDTO
            -> getScopedIdentifier(projectParams.getAccountIdentifier(), monitoredServiceDTO.getOrgIdentifier(),
                monitoredServiceDTO.getProjectIdentifier(), monitoredServiceDTO.getIdentifier()),
            monitoredServiceDTO -> monitoredServiceDTO));
  }

  private Map<String, SLOHealthIndicator> getScopedSloIdentifierToHealthIndicatorMap(
      List<AbstractServiceLevelObjective> serviceLevelObjectives, ProjectParams projectParams) {
    List<String> sloIdentifiers =
        serviceLevelObjectives.stream().map(AbstractServiceLevelObjective::getIdentifier).collect(Collectors.toList());
    List<String> scopedSLOIdentifiers = serviceLevelObjectives.stream()
                                            .map(slo -> serviceLevelObjectiveV2Service.getScopedIdentifier(slo))
                                            .collect(Collectors.toList());
    List<SLOHealthIndicator> sloHealthIndicatorsFromIdentifiers =
        sloHealthIndicatorService.getBySLOIdentifiers(projectParams.getAccountIdentifier(), sloIdentifiers);
    List<SLOHealthIndicator> sloHealthIndicatorsFromScopedIdentifiers =
        sloHealthIndicatorsFromIdentifiers.stream()
            .filter(sloHealthIndicator
                -> scopedSLOIdentifiers.contains(sloHealthIndicatorService.getScopedIdentifier(sloHealthIndicator)))
            .collect(Collectors.toList());
    return sloHealthIndicatorsFromScopedIdentifiers.stream().collect(Collectors.toMap(sloHealthIndicator
        -> sloHealthIndicatorService.getScopedIdentifier(sloHealthIndicator),
        sloHealthIndicator -> sloHealthIndicator));
  }

  private Map<String, AbstractServiceLevelObjective> getScopedIdentifierToAllSLOsIncUnderlyingSLOsMap(
      List<AbstractServiceLevelObjective> serviceLevelObjectives, ProjectParams projectParams) {
    Map<String, AbstractServiceLevelObjective> scopedIdentifierToAllSLOsIncUnderlyingSLOsMap =
        serviceLevelObjectives.stream().collect(
            Collectors.toMap(slo -> serviceLevelObjectiveV2Service.getScopedIdentifier(slo), slo -> slo));
    List<CompositeServiceLevelObjective> compositeServiceLevelObjectives =
        serviceLevelObjectives.stream()
            .filter(
                serviceLevelObjective -> serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE))
            .map(slo -> (CompositeServiceLevelObjective) slo)
            .collect(Collectors.toList());
    List<AbstractServiceLevelObjective> referredSimpleServiceLevelObjectiveList =
        serviceLevelObjectiveV2Service.getAllReferredSLOs(projectParams, compositeServiceLevelObjectives);
    scopedIdentifierToAllSLOsIncUnderlyingSLOsMap.putAll(
        referredSimpleServiceLevelObjectiveList.stream()
            .filter(serviceLevelObjective -> serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .collect(Collectors.toMap(slo -> serviceLevelObjectiveV2Service.getScopedIdentifier(slo), slo -> slo)));
    return scopedIdentifierToAllSLOsIncUnderlyingSLOsMap;
  }

  private Map<String, SLOError> getScopedIdentifierToSLOErrorMap(
      List<AbstractServiceLevelObjective> serviceLevelObjectives,
      Map<String, AbstractServiceLevelObjective> scopedIdentifierToAllSLOsIncUnderlyingSLOsMap,
      Map<String, SLOHealthIndicator> scopedSloIdentifierToHealthIndicatorMap) {
    Map<String, SLOError> scopedIdentifierToSLOErrorMap =
        serviceLevelObjectives.stream()
            .filter(serviceLevelObjective -> serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .collect(Collectors.toMap(serviceLevelObjective
                -> serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjective),
                serviceLevelObjective
                -> getErrorForSimpleSLO(Optional.ofNullable(scopedSloIdentifierToHealthIndicatorMap.get(
                    serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjective))))));
    List<CompositeServiceLevelObjective> compositeServiceLevelObjectives =
        serviceLevelObjectives.stream()
            .filter(
                serviceLevelObjective -> serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE))
            .map(slo -> (CompositeServiceLevelObjective) slo)
            .collect(Collectors.toList());
    scopedIdentifierToSLOErrorMap.putAll(
        compositeServiceLevelObjectives.stream().collect(Collectors.toMap(serviceLevelObjective
            -> serviceLevelObjectiveV2Service.getScopedIdentifier(serviceLevelObjective),
            serviceLevelObjective
            -> getErrorForCompositeSLO(serviceLevelObjective, scopedIdentifierToAllSLOsIncUnderlyingSLOsMap,
                scopedSloIdentifierToHealthIndicatorMap))));
    return scopedIdentifierToSLOErrorMap;
  }

  private SLOError getErrorForCompositeSLO(CompositeServiceLevelObjective serviceLevelObjective,
      Map<String, AbstractServiceLevelObjective> scopedIdentifierToAllSLOsIncUnderlyingSLOsMap,
      Map<String, SLOHealthIndicator> scopedSloIdentifierToHealthIndicatorMap) {
    for (ServiceLevelObjectivesDetail sloDetail : serviceLevelObjective.getServiceLevelObjectivesDetails()) {
      if (!scopedIdentifierToAllSLOsIncUnderlyingSLOsMap.containsKey(
              serviceLevelObjectiveV2Service.getScopedIdentifier(sloDetail))) {
        return SLOError.getErrorForDeletionOfSimpleSLOInListView();
      }
    }
    for (ServiceLevelObjectivesDetail sloDetail : serviceLevelObjective.getServiceLevelObjectivesDetails()) {
      if (scopedSloIdentifierToHealthIndicatorMap.get(serviceLevelObjectiveV2Service.getScopedIdentifier(sloDetail))
              .getFailedState()) {
        return SLOError.getErrorForDataCollectionFailureInCompositeSLOInListView();
      }
    }
    return SLOError.getNoError();
  }
  private SLOError getErrorForSimpleSLO(Optional<SLOHealthIndicator> sloHealthIndicator) {
    if (!sloHealthIndicator.isPresent()) {
      return SLOError.getErrorForDeletionOfSimpleSLOInConfigurationListView();
    }
    if (sloHealthIndicator.get().getFailedState()) {
      return SLOError.getErrorForDataCollectionFailureInSimpleSLOInListView();
    }
    return SLOError.getNoError();
  }

  private SLOError getErrorForSimpleSLOForDetailsView(SimpleServiceLevelObjective serviceLevelObjective) {
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOEntity(serviceLevelObjective);
    if (sloHealthIndicator.getFailedState()) {
      return SLOError.getErrorForDataCollectionFailureInSimpleSLOWidgetDetailsView();
    }
    return SLOError.getNoError();
  }

  private SLOError getErrorForCompositeSLOForDetailsView(List<SLOHealthListView> simpleServiceLevelObjectives) {
    boolean isFailed = false;
    for (SLOHealthListView sloHealthListView : simpleServiceLevelObjectives) {
      if (sloHealthListView.getSloError().isFailedState()
          && sloHealthListView.getSloError().getSloErrorType().equals(SLOErrorType.SIMPLE_SLO_DELETION)) {
        return SLOError.getErrorForDeletionOfSimpleSLOInWidgetDetailsView();
      }
      if (sloHealthListView.getSloError().isFailedState()) {
        isFailed = true;
      }
    }
    if (isFailed) {
      return SLOError.getErrorForDataCollectionFailureInCompositeSLOWidgetDetailsView();
    }
    return SLOError.getNoError();
  }

  @Override
  public PageResponse<SLOConsumptionBreakdown> getSLOConsumptionBreakdownView(
      ProjectParams projectParams, String compositeSLOIdentifier, Long startTime, Long endTime) {
    PageResponse<SLOHealthListView> sloHealthListViewPageResponse = getSloHealthListView(projectParams,
        SLODashboardApiFilter.builder().compositeSLOIdentifier(compositeSLOIdentifier).childResource(true).build(),
        PageParams.builder().page(0).size(20).build());

    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            projectParams, compositeSLOIdentifier);
    Map<ServiceLevelObjectiveDetailsRefDTO, Double> sloDetailsToWeightPercentageMap =
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().stream().collect(
            Collectors.toMap(ServiceLevelObjectivesDetail::getServiceLevelObjectiveDetailsRefDTO,
                ServiceLevelObjectivesDetail::getWeightagePercentage));

    List<SLOConsumptionBreakdown> sloConsumptionBreakdownList =
        sloHealthListViewPageResponse.getContent()
            .stream()
            .map(sloDetail
                -> getSLOConsumptionBreakdown(sloDetail, compositeServiceLevelObjective,
                    sloDetailsToWeightPercentageMap, projectParams, startTime, endTime))
            .collect(Collectors.toList());

    return PageResponse.<SLOConsumptionBreakdown>builder()
        .content(sloConsumptionBreakdownList)
        .pageSize(sloHealthListViewPageResponse.getPageSize())
        .pageIndex(sloHealthListViewPageResponse.getPageIndex())
        .totalPages(sloHealthListViewPageResponse.getTotalPages())
        .totalItems(sloHealthListViewPageResponse.getTotalItems())
        .pageItemCount(sloHealthListViewPageResponse.getPageItemCount())
        .build();
  }

  private SLOConsumptionBreakdown getSLOConsumptionBreakdown(SLOHealthListView sloDetail,
      CompositeServiceLevelObjective compositeServiceLevelObjective,
      Map<ServiceLevelObjectiveDetailsRefDTO, Double> sloDetailsToWeightPercentageMap, ProjectParams projectParams,
      Long startTime, Long endTime) {
    ProjectParams simpleSLOProjectParams =
        ProjectParams.builder()
            .accountIdentifier(projectParams.getAccountIdentifier())
            .orgIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getOrgIdentifier()
                    : "")
            .projectIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getProjectIdentifier()
                    : "")
            .build();
    Double weightPercentage = sloDetailsToWeightPercentageMap.get(
        ServiceLevelObjectiveDetailsRefDTO.builder()
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getOrgIdentifier()
                    : "")
            .projectIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getProjectIdentifier()
                    : "")
            .serviceLevelObjectiveRef(sloDetail.getSloIdentifier())
            .build());

    TimeRangeParams filter = null;
    Instant compositeSloStartedAtTime = Instant.ofEpochMilli(compositeServiceLevelObjective.getStartedAt());
    Instant startTimeForCurrentRange = Instant.ofEpochMilli(startTime);
    Instant endTimeForCurrentRange = Instant.ofEpochMilli(endTime);
    startTimeForCurrentRange = (startTimeForCurrentRange.isAfter(compositeSloStartedAtTime))
        ? startTimeForCurrentRange
        : compositeSloStartedAtTime;
    if (Objects.nonNull(startTimeForCurrentRange) && Objects.nonNull(endTimeForCurrentRange)) {
      filter = TimeRangeParams.builder().startTime(startTimeForCurrentRange).endTime(endTimeForCurrentRange).build();
    }

    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(simpleSLOProjectParams, sloDetail.getSloIdentifier());
    if (serviceLevelObjective == null) {
      return SLOConsumptionBreakdown.builder()
          .sloIdentifier(sloDetail.getSloIdentifier())
          .sloName(sloDetail.getName())
          .projectParams(simpleSLOProjectParams)
          .weightagePercentage(weightPercentage)
          .sloError(SLOError.getErrorForDeletionOfSimpleSLOInConsumptionView())
          .build();
    }
    SLODashboardWidget.SLOGraphData sloGraphData = graphDataService.getGraphData(serviceLevelObjective,
        startTimeForCurrentRange, endTimeForCurrentRange, sloDetail.getTotalErrorBudget(), filter);
    String projectName = getProjectName(projectParams.getAccountIdentifier(), simpleSLOProjectParams.getOrgIdentifier(),
        simpleSLOProjectParams.getProjectIdentifier());
    String orgName = getOrgName(projectParams.getAccountIdentifier(), simpleSLOProjectParams.getOrgIdentifier());
    SLOConsumptionBreakdown sloConsumptionBreakdown =
        SLOConsumptionBreakdown.builder()
            .sloIdentifier(sloDetail.getSloIdentifier())
            .sloName(sloDetail.getName())
            .monitoredServiceIdentifier(sloDetail.getMonitoredServiceIdentifier())
            .serviceName(sloDetail.getServiceName())
            .environmentIdentifier(sloDetail.getEnvironmentIdentifier())
            .sliType(sloDetail.getSliType())
            .weightagePercentage(weightPercentage)
            .sloTargetPercentage(sloDetail.getSloTargetPercentage())
            .sliStatusPercentage(sloGraphData.getSliStatusPercentage())
            .errorBudgetBurned(sloGraphData.getErrorBudgetBurned())
            .projectParams(simpleSLOProjectParams)
            .projectName(projectName)
            .orgName(orgName)
            .sloError(sloDetail.getSloError())
            .build();
    if (compositeServiceLevelObjective.getCompositeSLOFormulaType().equals(CompositeSLOFormulaType.WEIGHTED_AVERAGE)) {
      sloConsumptionBreakdown.setContributedErrorBudgetBurned(
          (int) ((weightPercentage / 100) * sloGraphData.getErrorBudgetBurned()));
    }
    return sloConsumptionBreakdown;
  }

  @Override
  public SLODashboardDetail getSloDashboardDetail(
      ProjectParams projectParams, String identifier, Long startTime, Long endTime) {
    ServiceLevelObjectiveV2Response sloResponse = serviceLevelObjectiveV2Service.get(projectParams, identifier);
    TimeRangeParams filter = null;
    if (Objects.nonNull(startTime) && Objects.nonNull(endTime)) {
      filter = TimeRangeParams.builder()
                   .startTime(Instant.ofEpochMilli(startTime))
                   .endTime(Instant.ofEpochMilli(endTime))
                   .build();
    }
    SLODashboardWidget sloDashboardWidget = getSloDashboardWidget(projectParams, sloResponse, filter);

    return SLODashboardDetail.builder()
        .description(sloResponse.getServiceLevelObjectiveV2DTO().getDescription())
        .createdAt(sloResponse.getCreatedAt())
        .lastModifiedAt(sloResponse.getLastModifiedAt())
        .timeRangeFilters(serviceLevelObjectiveV2Service.getEntity(projectParams, identifier).getTimeRangeFilters())
        .sloDashboardWidget(sloDashboardWidget)
        .build();
  }

  @Override
  public SLORiskCountResponse getRiskCount(
      ProjectParams projectParams, SLODashboardApiFilter serviceLevelObjectiveFilter) {
    return serviceLevelObjectiveV2Service.getRiskCount(projectParams, serviceLevelObjectiveFilter);
  }

  private SLODashboardWidget getSloDashboardWidget(
      ProjectParams projectParams, ServiceLevelObjectiveV2Response sloResponse, TimeRangeParams filter) {
    ServiceLevelObjectiveV2DTO slo = sloResponse.getServiceLevelObjectiveV2DTO();
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, slo.getIdentifier());

    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
    TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    List<SLOErrorBudgetResetDTO> errorBudgetResetDTOS =
        sloErrorBudgetResetService.getErrorBudgetResets(projectParams, slo.getIdentifier());
    int totalErrorBudgetMinutes =
        serviceLevelObjective.getActiveErrorBudgetMinutes(errorBudgetResetDTOS, currentLocalDate);

    SLODashboardWidget.SLOGraphData sloGraphData = graphDataService.getGraphData(serviceLevelObjective,
        timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()), currentTimeMinute, totalErrorBudgetMinutes,
        filter);
    if (slo.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
          (SimpleServiceLevelObjectiveSpec) sloResponse.getServiceLevelObjectiveV2DTO().getSpec();
      MonitoredServiceDTO monitoredService =
          monitoredServiceService.get(projectParams, simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
              .getMonitoredServiceDTO();

      MonitoredServiceDetail monitoredServiceDetail =
          MonitoredServiceDetail.builder()
              .monitoredServiceIdentifier(simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
              .monitoredServiceName(monitoredService.getName())
              .environmentIdentifier(monitoredService.getEnvironmentRef())
              .environmentName(
                  nextGenService
                      .getEnvironment(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                          serviceLevelObjective.getProjectIdentifier(), monitoredService.getEnvironmentRef())
                      .getName())
              .serviceName(
                  nextGenService
                      .getService(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                          serviceLevelObjective.getProjectIdentifier(), monitoredService.getServiceRef())
                      .getName())
              .serviceIdentifier(monitoredService.getServiceRef())
              .healthSourceIdentifier(simpleServiceLevelObjectiveSpec.getHealthSourceRef())
              .healthSourceName(
                  getHealthSourceName(monitoredService, simpleServiceLevelObjectiveSpec.getHealthSourceRef()))
              .projectParams(ProjectParams.builder()
                                 .accountIdentifier(projectParams.getAccountIdentifier())
                                 .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                 .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                 .build())
              .build();
      List<MonitoredServiceDetail> monitoredServiceDetails = Collections.singletonList(monitoredServiceDetail);

      return getSloDashboardWidgetBuilder(
          slo, timePeriod, sloGraphData, serviceLevelObjective, currentLocalDate, monitoredServiceDetails)
          .monitoredServiceIdentifier(simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
          .monitoredServiceName(monitoredService.getName())
          .environmentIdentifier(monitoredService.getEnvironmentRef())
          .environmentName(
              nextGenService
                  .getEnvironment(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                      serviceLevelObjective.getProjectIdentifier(), monitoredService.getEnvironmentRef())
                  .getName())
          .serviceName(nextGenService
                           .getService(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                               serviceLevelObjective.getProjectIdentifier(), monitoredService.getServiceRef())
                           .getName())
          .serviceIdentifier(monitoredService.getServiceRef())
          .healthSourceIdentifier(simpleServiceLevelObjectiveSpec.getHealthSourceRef())
          .healthSourceName(getHealthSourceName(monitoredService, simpleServiceLevelObjectiveSpec.getHealthSourceRef()))
          .type(simpleServiceLevelObjectiveSpec.getServiceLevelIndicatorType())
          .sloError(getErrorForSimpleSLOForDetailsView((SimpleServiceLevelObjective) serviceLevelObjective))
          .build();
    } else {
      CompositeServiceLevelObjective compositeSLO = (CompositeServiceLevelObjective) serviceLevelObjective;

      PageResponse<SLOHealthListView> sloHealthListViewPageResponse = getSloHealthListView(projectParams,
          SLODashboardApiFilter.builder()
              .compositeSLOIdentifier(compositeSLO.getIdentifier())
              .childResource(true)
              .build(),
          PageParams.builder().page(0).size(20).build());
      SLOError sloError = getErrorForCompositeSLOForDetailsView(sloHealthListViewPageResponse.getContent());
      List<MonitoredServiceDetail> monitoredServiceDetails =
          sloHealthListViewPageResponse.getContent()
              .stream()
              .map(sloDetail
                  -> MonitoredServiceDetail.builder()
                         .monitoredServiceIdentifier(sloDetail.getMonitoredServiceIdentifier())
                         .monitoredServiceName(sloDetail.getMonitoredServiceName())
                         .environmentIdentifier(sloDetail.getEnvironmentIdentifier())
                         .environmentName(sloDetail.getEnvironmentName())
                         .serviceName(sloDetail.getServiceName())
                         .serviceIdentifier(sloDetail.getServiceIdentifier())
                         .healthSourceIdentifier(sloDetail.getHealthSourceIdentifier())
                         .healthSourceName(sloDetail.getHealthSourceName())
                         .orgName(sloDetail.getOrgName())
                         .projectName(sloDetail.getProjectName())
                         .projectParams(
                             ProjectParams.builder()
                                 .accountIdentifier(projectParams.getAccountIdentifier())
                                 .orgIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                                         ? sloDetail.getProjectParams().getOrgIdentifier()
                                         : "")
                                 .projectIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                                         ? sloDetail.getProjectParams().getProjectIdentifier()
                                         : "")
                                 .build())
                         .build())
              .collect(Collectors.toList());

      return getSloDashboardWidgetBuilder(
          slo, timePeriod, sloGraphData, serviceLevelObjective, currentLocalDate, monitoredServiceDetails)
          .sloError(sloError)
          .build();
    }
  }

  private SLODashboardWidgetBuilder getSloDashboardWidgetBuilder(ServiceLevelObjectiveV2DTO slo, TimePeriod timePeriod,
      SLODashboardWidget.SLOGraphData sloGraphData, AbstractServiceLevelObjective serviceLevelObjective,
      LocalDateTime currentLocalDate, List<MonitoredServiceDetail> monitoredServiceDetails) {
    return SLODashboardWidget.withGraphData(sloGraphData)
        .sloIdentifier(slo.getIdentifier())
        .title(slo.getName())
        .sloTargetType(slo.getSloTarget().getType())
        .currentPeriodLengthDays(timePeriod.getTotalDays())
        .currentPeriodStartTime(timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .currentPeriodEndTime(timePeriod.getEndTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .monitoredServiceDetails(monitoredServiceDetails)
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .tags(slo.getTags())
        .timeRemainingDays(timePeriod.getRemainingDays(currentLocalDate))
        .burnRate(SLODashboardWidget.BurnRate.builder()
                      .currentRatePercentage(sloGraphData.dailyBurnRate(serviceLevelObjective.getZoneOffset()))
                      .build())
        .evaluationType(sloGraphData.getEvaluationType())
        .isTotalErrorBudgetApplicable(!(serviceLevelObjective.getType() == ServiceLevelObjectiveType.COMPOSITE
            && sloGraphData.getEvaluationType() == SLIEvaluationType.REQUEST))
        .sloType(slo.getType());
  }

  @Override
  public PageResponse<MSDropdownResponse> getSLOAssociatedMonitoredServices(
      ProjectParams projectParams, PageParams pageParams) {
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList =
        serviceLevelObjectiveV2Service.getAllSLOs(projectParams, ServiceLevelObjectiveType.SIMPLE);

    Set<String> monitoredServiceIdentifiers =
        serviceLevelObjectiveList.stream()
            .map(serviceLevelObjective
                -> ((SimpleServiceLevelObjective) serviceLevelObjective).getMonitoredServiceIdentifier())
            .collect(Collectors.toSet());
    List<MonitoredServiceResponse> monitoredServiceResponseList =
        monitoredServiceService.get(projectParams, monitoredServiceIdentifiers);

    List<MSDropdownResponse> msDropdownResponseList =
        monitoredServiceResponseList.stream()
            .map(monitoredServiceResponse -> getMSDropdownResponse(monitoredServiceResponse.getMonitoredServiceDTO()))
            .collect(Collectors.toList());

    return PageUtils.offsetAndLimit(msDropdownResponseList, pageParams.getPage(), pageParams.getSize());
  }

  @Override
  public List<SecondaryEventsResponse> getSecondaryEvents(
      ProjectParams projectParams, long startTime, long endTime, String identifier) {
    ServiceLevelObjectiveV2Response sloResponse = serviceLevelObjectiveV2Service.get(projectParams, identifier);
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO = sloResponse.getServiceLevelObjectiveV2DTO();

    // Adding annotation threads
    List<SecondaryEventsResponse> secondaryEvents = annotationService.getAllInstancesGrouped(projectParams,
        TimeUnit.MILLISECONDS.toSeconds(startTime), TimeUnit.MILLISECONDS.toSeconds(endTime), identifier);

    // Adding Entity Unavailability Status instances
    if (serviceLevelObjectiveV2DTO.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      List<EntityUnavailabilityStatuses> failureInstances =
          getFailureInstances(projectParams, serviceLevelObjectiveV2DTO, startTime, endTime);
      secondaryEvents.addAll(failureInstances.stream()
                                 .map(instance
                                     -> SecondaryEventsResponse.builder()
                                            .type(instance.getEntityType().getSecondaryEventTypeFromEntityType())
                                            .identifiers(Collections.singletonList(instance.getUuid()))
                                            .startTime(instance.getStartTime())
                                            .endTime(instance.getEndTime())
                                            .build())
                                 .collect(Collectors.toList()));

      secondaryEvents.addAll(srmAnalysisStepService.getSRMAnalysisStepExecutions(projectParams,
          ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec()).getMonitoredServiceRef(), startTime,
          endTime));
    }

    // Adding error budget reset instances
    if (serviceLevelObjectiveV2DTO.getSloTarget().getSpec().isErrorBudgetResetEnabled()) {
      List<SLOErrorBudgetReset> sloErrorBudgetResets =
          sloErrorBudgetResetService.getErrorBudgetResetEntities(projectParams, identifier, startTime, endTime);
      secondaryEvents.addAll(sloErrorBudgetResets.stream()
                                 .map(errorBudgetReset
                                     -> SecondaryEventsResponse.builder()
                                            .type(SecondaryEventsType.ERROR_BUDGET_RESET)
                                            .identifiers(Collections.singletonList(errorBudgetReset.getUuid()))
                                            .startTime(TimeUnit.MILLISECONDS.toSeconds(errorBudgetReset.getCreatedAt()))
                                            .endTime(TimeUnit.MILLISECONDS.toSeconds(errorBudgetReset.getCreatedAt()))
                                            .build())
                                 .collect(Collectors.toList()));
    }

    return secondaryEvents.stream()
        .sorted(Comparator.comparing(SecondaryEventsResponse::getStartTime))
        .collect(Collectors.toList());
  }

  private List<EntityUnavailabilityStatuses> getFailureInstances(ProjectParams projectParams,
      ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, long startTime, long endTime) {
    String sliIdentifier = ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
                               .getServiceLevelIndicators()
                               .get(0)
                               .getIdentifier();
    String monitoredServiceIdentifier =
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec()).getMonitoredServiceRef();
    List<String> sliIds =
        serviceLevelIndicatorService.getEntities(projectParams, Collections.singletonList(sliIdentifier))
            .stream()
            .map(serviceLevelIndicator -> serviceLevelIndicator.getUuid())
            .collect(Collectors.toList());
    List<EntityUnavailabilityStatuses> entityUnavailabilityInstances =
        entityUnavailabilityStatusesService.getAllUnavailabilityInstances(
            projectParams, TimeUnit.MILLISECONDS.toSeconds(startTime), TimeUnit.MILLISECONDS.toSeconds(endTime));

    // Adding downtime Instances
    List<EntityUnavailabilityStatuses> failureInstances =
        entityUnavailabilityInstances.stream()
            .filter(instance -> instance.getEntityType().equals(EntityType.MAINTENANCE_WINDOW))
            .collect(Collectors.toList());
    failureInstances = downtimeService.filterDowntimeInstancesOnMSs(
        projectParams, failureInstances, Collections.singleton(monitoredServiceIdentifier));

    // Adding Data collection failure Instances
    List<EntityUnavailabilityStatuses> dcEntityUnavailabilityStatuses =
        entityUnavailabilityInstances.stream()
            .filter(statusesDTO
                -> statusesDTO.getEntityType().equals(EntityType.SLO)
                    && sliIds.contains(statusesDTO.getEntityIdentifier())
                    && (statusesDTO.getStatus().equals(EntityUnavailabilityStatus.DATA_COLLECTION_FAILED)
                        || statusesDTO.getStatus().equals(EntityUnavailabilityStatus.DATA_RECOLLECTION_PASSED)))
            .sorted(Comparator.comparing(entry -> entry.getStartTime()))
            .collect(Collectors.toList());

    if (!dcEntityUnavailabilityStatuses.isEmpty()) {
      EntityUnavailabilityStatuses currentDCInstance = null;
      for (EntityUnavailabilityStatuses entityUnavailabilityStatus : dcEntityUnavailabilityStatuses) {
        if (currentDCInstance != null
            && currentDCInstance.getEndTime() - entityUnavailabilityStatus.getStartTime()
                <= Duration.ofMinutes(1).toMillis()) {
          currentDCInstance.setEndTime(entityUnavailabilityStatus.getEndTime());
        } else {
          if (currentDCInstance != null) {
            failureInstances.add(currentDCInstance);
          }
          currentDCInstance = entityUnavailabilityStatus;
        }
      }
      failureInstances.add(currentDCInstance);
    }
    return failureInstances;
  }

  public SecondaryEventDetailsResponse getSecondaryEventDetails(SecondaryEventsType eventType, List<String> uuids) {
    return secondaryEventsTypeToDetailsMapBinder.get(eventType).getInstanceByUuids(uuids, eventType);
  }

  private MSDropdownResponse getMSDropdownResponse(MonitoredServiceDTO monitoredServiceDTO) {
    return MSDropdownResponse.builder()
        .identifier(monitoredServiceDTO.getIdentifier())
        .name(monitoredServiceDTO.getName())
        .serviceRef(monitoredServiceDTO.getServiceRef())
        .environmentRef(monitoredServiceDTO.getEnvironmentRef())
        .build();
  }

  private SLOHealthListView getSLOListView(ProjectParams projectParams, AbstractServiceLevelObjective slo,
      Map<String, MonitoredServiceDTO> scopedMonitoredServiceIdentifierToDTOMap,
      Map<String, SLOHealthIndicator> scopedSloIdentifierToHealthIndicatorMap,
      Map<String, String> userJourneyIdentifierToNameMap,
      Map<String, EntityUnavailabilityStatusesDTO> monitoredServiceIdentifiersToUnavailabilityStatusesDTOMap,
      Map<String, SLOError> scopedIdentifierToSLOErrorMap) {
    Optional<SLOHealthIndicator> sloHealthIndicator = Optional.ofNullable(
        scopedSloIdentifierToHealthIndicatorMap.get(serviceLevelObjectiveV2Service.getScopedIdentifier(slo)));
    if (!sloHealthIndicator.isPresent()) {
      if (slo.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
        return SLOHealthListView.getSLOHealthListViewBuilderForDeletedSimpleSLO(slo).build();
      }
    }
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), slo.getZoneOffset());
    List<SLOErrorBudgetResetDTO> errorBudgetResetDTOS =
        sloErrorBudgetResetService.getErrorBudgetResets(projectParams, slo.getIdentifier());
    int totalErrorBudgetMinutes = slo.getActiveErrorBudgetMinutes(errorBudgetResetDTOS, currentLocalDate);
    List<UserJourneyDTO> userJourneys = slo.getUserJourneyIdentifiers()
                                            .stream()
                                            .map(userJourneyIdentifier
                                                -> UserJourneyDTO.builder()
                                                       .identifier(userJourneyIdentifier)
                                                       .name(userJourneyIdentifierToNameMap.get(userJourneyIdentifier))
                                                       .build())
                                            .collect(Collectors.toList());
    SLOError sloError = scopedIdentifierToSLOErrorMap.get(serviceLevelObjectiveV2Service.getScopedIdentifier(slo));
    if (slo.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) slo;
      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      MonitoredServiceDTO monitoredService = scopedMonitoredServiceIdentifierToDTOMap.get(
          getScopedIdentifier(projectParams.getAccountIdentifier(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
              ((SimpleServiceLevelObjective) slo).getMonitoredServiceIdentifier()));
      String projectName =
          getProjectName(projectParams.getAccountIdentifier(), slo.getOrgIdentifier(), slo.getProjectIdentifier());
      String orgName = getOrgName(projectParams.getAccountIdentifier(), slo.getOrgIdentifier());
      return SLOHealthListView
          .getSLOHealthListViewBuilder(slo, userJourneys, totalErrorBudgetMinutes, sloHealthIndicator.get(), sloError)
          .monitoredServiceIdentifier(monitoredService.getIdentifier())
          .monitoredServiceName(monitoredService.getName())
          .environmentIdentifier(monitoredService.getEnvironmentRef())
          .projectName(projectName)
          .orgName(orgName)
          .environmentName(nextGenService
                               .getEnvironment(slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
                                   monitoredService.getEnvironmentRef())
                               .getName())
          .serviceName(nextGenService
                           .getService(slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
                               monitoredService.getServiceRef())
                           .getName())
          .serviceIdentifier(monitoredService.getServiceRef())
          .healthSourceIdentifier(simpleServiceLevelObjective.getHealthSourceIdentifier())
          .healthSourceName(
              getHealthSourceName(monitoredService, simpleServiceLevelObjective.getHealthSourceIdentifier()))
          .sliType(simpleServiceLevelObjective.getServiceLevelIndicatorType())
          .sliIdentifier(simpleServiceLevelObjective.getServiceLevelIndicators().get(0))
          .downtimeStatusDetails(
              monitoredServiceIdentifiersToUnavailabilityStatusesDTOMap.containsKey(monitoredService.getIdentifier())
                  ? DowntimeStatusDetails
                        .getDowntimeStatusDetailsInstanceBuilder(
                            monitoredServiceIdentifiersToUnavailabilityStatusesDTOMap
                                .get(monitoredService.getIdentifier())
                                .getStartTime(),
                            monitoredServiceIdentifiersToUnavailabilityStatusesDTOMap
                                .get(monitoredService.getIdentifier())
                                .getEndTime(),
                            clock)
                        .build()
                  : null)
          .build();
    }
    return SLOHealthListView
        .getSLOHealthListViewBuilder(slo, userJourneys, totalErrorBudgetMinutes, sloHealthIndicator.get(), sloError)
        .build();
  }

  private String getHealthSourceName(MonitoredServiceDTO monitoredServiceDTO, String healthSourceRef) {
    return monitoredServiceDTO.getSources()
        .getHealthSources()
        .stream()
        .filter(healthSource -> healthSource.getIdentifier().equals(healthSourceRef))
        .findFirst()
        .orElseThrow(()
                         -> new IllegalStateException(
                             "Health source identifier" + healthSourceRef + " not found in monitored service"))
        .getName();
  }

  private String getScopedIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    return getScopedInformation(accountId, orgIdentifier, projectIdentifier, identifier);
  }

  private String getOrgName(String accountIdentifier, String orgIdentifier) {
    String orgName = "";
    if (orgIdentifier != null) {
      orgName = nextGenService.getOrganization(accountIdentifier, orgIdentifier).getName();
    }
    return orgName;
  }

  private String getProjectName(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    String projectName = "";
    if (orgIdentifier != null) {
      projectName = nextGenService.getCachedProject(accountIdentifier, orgIdentifier, projectIdentifier).getName();
    }
    return projectName;
  }
}
