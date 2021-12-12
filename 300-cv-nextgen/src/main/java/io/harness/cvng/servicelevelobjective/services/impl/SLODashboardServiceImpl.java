package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.ng.beans.PageResponse;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class SLODashboardServiceImpl implements SLODashboardService {
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLIRecordService sliRecordService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private Clock clock;
  @Override
  public PageResponse<SLODashboardWidget> getSloDashboardWidgets(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    PageResponse<ServiceLevelObjectiveResponse> sloPageResponse =
        serviceLevelObjectiveService.getSLOForDashboard(projectParams, filter, pageParams);
    Set<String> monitoredServiceIdentifiers =
        sloPageResponse.getContent()
            .stream()
            .map(slo -> slo.getServiceLevelObjectiveDTO().getMonitoredServiceRef())
            .collect(Collectors.toSet());
    Map<String, MonitoredServiceDTO> identifierToMonitoredServiceMap =
        getIdentifierToMonitoredServiceDTOMap(projectParams, monitoredServiceIdentifiers);
    List<SLODashboardWidget> sloDashboardWidgets =
        sloPageResponse.getContent()
            .stream()
            .map(sloResponse -> {
              return getSloDashboardWidget(projectParams, identifierToMonitoredServiceMap, sloResponse);
            })
            .collect(Collectors.toList());
    return PageResponse.<SLODashboardWidget>builder()
        .pageSize(sloPageResponse.getPageSize())
        .pageIndex(sloPageResponse.getPageIndex())
        .totalPages(sloPageResponse.getTotalPages())
        .totalItems(sloPageResponse.getTotalItems())
        .pageItemCount(sloPageResponse.getPageItemCount())
        .content(sloDashboardWidgets)
        .build();
  }

  private SLODashboardWidget getSloDashboardWidget(ProjectParams projectParams,
      Map<String, MonitoredServiceDTO> identifierToMonitoredServiceMap, ServiceLevelObjectiveResponse sloResponse) {
    Preconditions.checkState(sloResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().size() == 1,
        "Only one service level indicator is supported");
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        sloResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());

    ServiceLevelObjectiveDTO slo = sloResponse.getServiceLevelObjectiveDTO();
    ServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveService.getEntity(projectParams, slo.getIdentifier());
    MonitoredServiceDTO monitoredService = identifierToMonitoredServiceMap.get(slo.getMonitoredServiceRef());
    LocalDate currentLocalDate =
        LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset()).toLocalDate();
    TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    int totalErrorBudgetMinutes = serviceLevelObjective.getTotalErrorBudgetMinutes(currentLocalDate);
    SLODashboardWidget.SLOGraphData sloGraphData = sliRecordService.getGraphData(serviceLevelIndicator.getUuid(),
        timePeriod.getStartDate().atStartOfDay().toInstant(serviceLevelObjective.getZoneOffset()), currentTimeMinute,
        totalErrorBudgetMinutes, serviceLevelIndicator.getSliMissingDataType());
    int remainingDays = timePeriod.getRemainingDays(currentLocalDate).getDays();
    double dailyBurnRate = sloGraphData.errorBudgetSpentPercentage() / (timePeriod.getTotalDays() - remainingDays);
    return SLODashboardWidget.withGraphData(sloGraphData)
        .sloIdentifier(slo.getIdentifier())
        .title(slo.getName())
        .sloTargetType(slo.getTarget().getType())
        .currentPeriodLengthDays(timePeriod.getTotalDays())
        .currentPeriodStartTime(timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .currentPeriodEndTime(timePeriod.getEndTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .monitoredServiceIdentifier(slo.getMonitoredServiceRef())
        .monitoredServiceName(monitoredService.getName())
        .healthSourceIdentifier(slo.getHealthSourceRef())
        .healthSourceName(getHealthSourceName(monitoredService, slo.getHealthSourceRef()))
        .tags(slo.getTags())
        .type(slo.getServiceLevelIndicators().get(0).getType())
        .burnRate(SLODashboardWidget.BurnRate.builder().currentRatePercentage(dailyBurnRate).build())
        .totalErrorBudget(totalErrorBudgetMinutes)
        .timeRemainingDays(remainingDays)
        .build();
  }

  @NotNull
  private Map<String, MonitoredServiceDTO> getIdentifierToMonitoredServiceDTOMap(
      ProjectParams projectParams, Set<String> monitoredServiceIdentifiers) {
    List<MonitoredServiceDTO> monitoredServiceDTOS =
        monitoredServiceService.get(projectParams, monitoredServiceIdentifiers)
            .stream()
            .map(monitoredServiceResponse -> monitoredServiceResponse.getMonitoredServiceDTO())
            .collect(Collectors.toList());
    return monitoredServiceDTOS.stream().collect(
        Collectors.toMap(MonitoredServiceDTO::getIdentifier, Function.identity()));
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
}
