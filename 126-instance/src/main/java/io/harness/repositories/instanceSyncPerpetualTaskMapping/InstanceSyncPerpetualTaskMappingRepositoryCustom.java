/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.instanceSyncPerpetualTaskMapping;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InstanceSyncPerpetualTaskMapping;

import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public interface InstanceSyncPerpetualTaskMappingRepositoryCustom {
  Optional<InstanceSyncPerpetualTaskMapping> findByConnectorRef(
      String accountId, String OrgId, String projectId, String ConnectorRef);
}
