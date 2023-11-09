/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serviceaccount.ServiceAccountDTOInternal;
import io.harness.serviceaccount.remote.ServiceAccountClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class HarnessServiceAccountServiceImpl implements HarnessServiceAccountService {
  private final ServiceAccountService serviceAccountService;
  private final ServiceAccountClient serviceAccountClient;

  @Inject
  public HarnessServiceAccountServiceImpl(
      ServiceAccountService serviceAccountService, @Named("PRIVILEGED") ServiceAccountClient serviceAccountClient) {
    this.serviceAccountService = serviceAccountService;
    this.serviceAccountClient = serviceAccountClient;
  }

  @Override
  public void sync(String identifier, Scope scope, String uniqueId) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);

    ServiceAccountDTOInternal serviceAccountDTO = getResponse(serviceAccountClient.gerServiceAccountInternal(identifier,
        scopeParams.getAccountIdentifier(), scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier()));

    if (serviceAccountDTO != null) {
      ServiceAccount serviceAccount = ServiceAccount.builder()
                                          .identifier(identifier)
                                          .scopeIdentifier(scope.toString())
                                          .uniqueId(serviceAccountDTO.getUniqueId())
                                          .build();
      ServiceAccount savedServiceAccount = serviceAccountService.createIfNotPresent(serviceAccount);
      if (savedServiceAccount.getUniqueId() == null && serviceAccountDTO.getUniqueId() != null) {
        savedServiceAccount.setUniqueId(serviceAccountDTO.getUniqueId());
        serviceAccountService.update(savedServiceAccount);
      }
    } else {
      serviceAccountService.deleteIfPresent(identifier, scope.toString());
    }
  }
}
