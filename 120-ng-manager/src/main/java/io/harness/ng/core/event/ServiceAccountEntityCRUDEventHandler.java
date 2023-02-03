/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ServiceAccountEntityCRUDEventHandler {
  private final ServiceAccountService serviceAccountService;

  @Inject
  public ServiceAccountEntityCRUDEventHandler(ServiceAccountService serviceAccountService) {
    this.serviceAccountService = serviceAccountService;
  }

  public boolean deleteAssociatedServiceAccounts(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    serviceAccountService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier);
    return true;
  }
}
