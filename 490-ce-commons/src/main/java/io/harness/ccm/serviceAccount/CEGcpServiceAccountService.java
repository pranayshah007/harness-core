/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.serviceAccount;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CEGcpServiceAccount;

import java.io.IOException;

@OwnedBy(CE)
public interface CEGcpServiceAccountService {
  CEGcpServiceAccount create(String accountId, String ccmProjectId);
  CEGcpServiceAccount provisionAndRetrieveServiceAccount(
      String accountId, String ccmProjectId, String serviceAccountEmailSource) throws IOException;
}
