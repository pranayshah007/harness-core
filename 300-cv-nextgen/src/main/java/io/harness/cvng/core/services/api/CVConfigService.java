/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.encryption.Scope;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public interface CVConfigService extends DeleteEntityByHandler<CVConfig> {
  CVConfig save(CVConfig cvConfig);
  List<CVConfig> save(List<CVConfig> cvConfig);
  void update(CVConfig cvConfig);
  void update(List<CVConfig> cvConfigs);
  @Nullable CVConfig get(String cvConfigId);
  void delete(String cvConfigId);

  void deleteByIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier);
  List<CVConfig> findByConnectorIdentifier(String accountId, @Nullable String orgIdentifier,
      @Nullable String projectIdentifier, String connectorIdentifierWithoutScopePrefix, Scope scope);
  List<CVConfig> list(String accountId, String connectorIdentifier);
  List<CVConfig> list(String accountId, String connectorIdentifier, String productName);
  List<CVConfig> list(
      String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier);
  List<CVConfig> listByMonitoringSources(
      MonitoredServiceParams monitoredServiceParams, List<String> healthSourceIdentifiers);
  List<String> getProductNames(String accountId, String connectorIdentifier);
  List<String> getMonitoringSourceIds(String accountId, String orgIdentifier, String projectIdentifier, String filter);
  List<CVConfig> listByMonitoringSources(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> monitoringSourceIdentifier);

  boolean doesAnyCVConfigExistsInProject(String accountId, String orgIdentifier, String projectIdentifier);

  void setHealthMonitoringFlag(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> identifiers, boolean isEnabled);
  List<CVConfig> list(MonitoredServiceParams monitoredServiceParams);
  List<CVConfig> list(MonitoredServiceParams monitoredServiceParams, List<String> identifiers);
  Map<String, DataSourceType> getDataSourceTypeForCVConfigs(MonitoredServiceParams monitoredServiceParams);

  List<CVConfig> getCVConfigs(MonitoredServiceParams monitoredServiceParams);

  List<CVConfig> getCVConfigs(ProjectParams projectParams, String identifier);

  List<CVConfig> list(ProjectParams projectParams, List<String> identifiers);
}
