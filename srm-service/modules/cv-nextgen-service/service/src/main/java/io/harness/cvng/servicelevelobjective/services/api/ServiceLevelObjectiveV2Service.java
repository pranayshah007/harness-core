/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ServiceLevelObjectiveV2Service extends DeleteEntityByHandler<AbstractServiceLevelObjective> {
  TimeGraphResponse getOnboardingGraph(CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec);

  ServiceLevelObjectiveV2Response create(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO);

  ServiceLevelObjectiveV2Response update(
      ProjectParams projectParams, String identifier, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO);

  AbstractServiceLevelObjective getEntity(ProjectParams projectParams, String identifier);

  boolean delete(ProjectParams projectParams, String identifier);

  void setMonitoredServiceSLOsEnableFlag(
      ProjectParams projectParams, String monitoreServiceIdentifier, boolean isEnabled);

  void updateNotificationRuleRefInSLO(ProjectParams projectParams, AbstractServiceLevelObjective serviceLevelObjective,
      List<String> notificationRuleRefs);

  PageResponse<ServiceLevelObjectiveV2Response> get(ProjectParams projectParams, Integer offset, Integer pageSize,
      ServiceLevelObjectiveFilter serviceLevelObjectiveFilter);

  ServiceLevelObjectiveV2Response get(ProjectParams projectParams, String identifier);

  SLORiskCountResponse getRiskCount(ProjectParams projectParams, SLODashboardApiFilter serviceLevelObjectiveFilter);

  List<AbstractServiceLevelObjective> getAllSLOs(ProjectParams projectParams);

  List<AbstractServiceLevelObjective> getAllSLOs(
      ProjectParams projectParams, ServiceLevelObjectiveType serviceLevelObjectiveType);

  List<AbstractServiceLevelObjective> get(ProjectParams projectParams, List<String> identifiers);

  List<AbstractServiceLevelObjective> getSimpleSLOWithChildResource(
      ProjectParams projectParams, List<String> identifiers);

  List<AbstractServiceLevelObjective> getByMonitoredServiceIdentifier(
      ProjectParams projectParams, String monitoredServiceIdentifier);

  List<SimpleServiceLevelObjective> getByMonitoredServiceIdentifiers(
      ProjectParams projectParams, Set<String> monitoredServiceIdentifiers);
  PageResponse<CVNGLogDTO> getCVNGLogs(
      ProjectParams projectParams, String identifier, SLILogsFilter sliLogsFilter, PageParams pageParams);

  PageResponse<AbstractServiceLevelObjective> getSLOForListView(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams);

  List<String> getReferencedSimpleSLOs(
      ProjectParams projectParams, CompositeServiceLevelObjective compositeServiceLevelObjective);

  Set<String> getReferencedMonitoredServices(List<AbstractServiceLevelObjective> serviceLevelObjectiveList);

  SimpleServiceLevelObjective getFromSLIIdentifier(ProjectParams projectParams, String serviceLevelIndicatorIdentifier);

  PageResponse<NotificationRuleResponse> getNotificationRules(
      ProjectParams projectParams, String sloIdentifier, PageParams pageParams);

  void beforeNotificationRuleDelete(ProjectParams projectParams, String notificationRuleRef);

  AbstractServiceLevelObjective get(String sloId);

  void handleNotification(AbstractServiceLevelObjective serviceLevelObjective);

  Map<AbstractServiceLevelObjective, SLIEvaluationType> getEvaluationType(
      ProjectParams projectParams, List<AbstractServiceLevelObjective> serviceLevelObjectiveList);

  String getScopedIdentifier(AbstractServiceLevelObjective abstractServiceLevelObjective);

  String getScopedIdentifier(ServiceLevelObjectivesDetail serviceLevelObjectivesDetail);

  String getScopedIdentifierForSLI(SimpleServiceLevelObjective serviceLevelObjective);

  List<SLOErrorBudgetResetDTO> getErrorBudgetResetHistory(ProjectParams projectParams, String sloIdentifier);
  SLOErrorBudgetResetDTO resetErrorBudget(ProjectParams projectParams, SLOErrorBudgetResetDTO resetDTO);
}
