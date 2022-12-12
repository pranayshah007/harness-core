/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api.monitoredService;

import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public interface HealthSourceService {
  String DELIMITER = "/";
  void create(String accountId, String orgIdentifier, String projectIdentifier, String environmentRef,
      String serviceRef, String monitoredServiceIdentifier, String nameSpaceIdentifier, Set<HealthSource> healthSources,
      boolean enabled);
  void checkIfAlreadyPresent(String accountId, String orgIdentifier, String projectIdentifier,
      String nameSpaceIdentifier, Set<HealthSource> healthSources);
  Set<HealthSource> get(String accountId, String orgIdentifier, String projectIdentifier, String nameSpaceIdentifier,
      List<String> identifiers);
  void delete(String accountId, String orgIdentifier, String projectIdentifier,
      String nameSpaceIdentifierSpaceIdentifier, List<String> identifiers);
  void update(String accountId, String orgIdentifier, String projectIdentifier, String environmentRef,
      String serviceRef, String monitoredServiceIdentifier, String nameSpaceIdentifier, Set<HealthSource> healthSource,
      boolean isEnabled);
  static String getNameSpacedIdentifier(String nameSpace, String identifier) {
    return nameSpace + DELIMITER + identifier;
  }
  void setHealthMonitoringFlag(String accountId, String orgIdentifier, String projectIdentifier, String namespace,
      List<String> healthSourceIdentifiers, boolean enable);

  List<CVConfig> getCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String monitoredServiceIdentifier, String healthSourceIdentifier);
  static Pair<String, String> getNameSpaceAndIdentifier(String identifier) {
    String[] identifiers = identifier.split(DELIMITER);
    return Pair.of(identifiers[0], identifiers[1]);
  }
  Map<String, Set<HealthSource>> getHealthSource(List<MonitoredService> monitoredServiceEntities);
}
