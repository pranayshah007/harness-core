/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.autodiscovery.services;

import io.harness.cvng.autodiscovery.beans.AutoDiscoveryAsyncResponseDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryRequestDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;

import java.io.IOException;

public interface AutoDiscoveryService {
  AutoDiscoveryResponseDTO create(ProjectParams projectParams, AutoDiscoveryRequestDTO autoDiscoveryRequestDTO)
      throws IOException;

  AutoDiscoveryAsyncResponseDTO reImport(ProjectParams projectParams);

  AutoDiscoveryAsyncResponseDTO status(String correlationId);
}
