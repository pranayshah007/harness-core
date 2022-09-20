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
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.remote.ServiceAccountClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
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
  public void sync(String identifier, Scope scope) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);
    List<String> resourceIds = new ArrayList<>();
    resourceIds.add(identifier);

    List<ServiceAccountDTO> serviceAccountDTOs =
        getResponse(serviceAccountClient.listServiceAccounts(scopeParams.getAccountIdentifier(),
            scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier(), resourceIds));

    if (!serviceAccountDTOs.isEmpty()) {
      ServiceAccount serviceAccount =
          ServiceAccount.builder().identifier(identifier).scopeIdentifier(scope.toString()).build();
      serviceAccountService.createIfNotPresent(serviceAccount);
    } else {
      serviceAccountService.deleteIfPresent(identifier, scope.toString());
    }
  }
}
