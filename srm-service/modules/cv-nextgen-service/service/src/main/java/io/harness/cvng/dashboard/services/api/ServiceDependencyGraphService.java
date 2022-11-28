/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.api;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.dashboard.beans.ServiceDependencyGraphDTO;

import java.util.List;
import javax.annotation.Nullable;
import lombok.NonNull;

@OwnedBy(CV)
public interface ServiceDependencyGraphService {
  @Deprecated
  ServiceDependencyGraphDTO getDependencyGraph(@NonNull ProjectParams projectParams, @Nullable String serviceIdentifier,
      @Nullable String environmentIdentifier, @NonNull boolean servicesAtRiskFilter, @Nullable String filter);
  ServiceDependencyGraphDTO getDependencyGraph(@NonNull ProjectParams projectParams,
      @Nullable List<String> monitoredServiceIdentifiers, @NonNull boolean servicesAtRiskFilter,
      @Nullable String filter);
}
