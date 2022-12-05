/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api.monitoredService;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.ServiceDependency;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;

@OwnedBy(CV)
public interface ServiceDependencyService {
  void updateDependencies(ProjectParams projectParams, String toMonitoredServiceIdentifier,
      Set<ServiceDependencyDTO> fromMonitoredServiceIdentifiers);

  void deleteDependenciesForService(ProjectParams projectParams, String monitoredServiceIdentifier);

  Set<ServiceDependencyDTO> getDependentServicesForMonitoredService(
      @NonNull ProjectParams projectParams, String monitoredServiceIdentifier);

  Set<ServiceDependencyDTO> getDependentServicesToMonitoredService(
      @NonNull ProjectParams projectParams, String monitoredServiceIdentifier);

  List<ServiceDependency> getServiceDependencies(
      @NonNull ProjectParams projectParams, @NonNull List<String> monitoredServiceIdentifiers);

  Map<String, List<String>> getMonitoredServiceToDependentServicesMap(
      @NonNull ProjectParams projectParams, List<String> monitoredServiceIdentifiers);
}
