/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.remote.ServiceAccountClient;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Response;

@OwnedBy(PL)
public class HarnessServiceAccountServiceImplTest extends AccessControlTestBase {
  private ServiceAccountClient serviceAccountClient;
  private ServiceAccountService serviceAccountService;
  private HarnessServiceAccountService harnessServiceAccountService;

  @Before
  public void setup() {
    serviceAccountClient = mock(ServiceAccountClient.class, RETURNS_DEEP_STUBS);
    serviceAccountService = mock(ServiceAccountService.class);
    harnessServiceAccountService =
        spy(new HarnessServiceAccountServiceImpl(serviceAccountService, serviceAccountClient));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncFound() throws IOException {
    String identifier = randomAlphabetic(10);
    List<String> resourceIds = Lists.newArrayList(identifier);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    ServiceAccount serviceAccount =
        ServiceAccount.builder().identifier(identifier).scopeIdentifier(scope.toString()).build();
    List<ServiceAccountDTO> serviceAccountDTOs = Lists.newArrayList(
        ServiceAccountDTO.builder().identifier(identifier).accountIdentifier(accountIdentifier).build());
    when(serviceAccountClient.listServiceAccounts(accountIdentifier, null, null, resourceIds).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(serviceAccountDTOs)));
    when(serviceAccountService.createIfNotPresent(serviceAccount)).thenReturn(serviceAccount);
    harnessServiceAccountService.sync(identifier, scope);
    verify(serviceAccountClient, atLeast(1)).listServiceAccounts(accountIdentifier, null, null, resourceIds);
    verify(serviceAccountService, times(1)).createIfNotPresent(serviceAccount);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSyncNotFound() throws IOException {
    String identifier = randomAlphabetic(10);
    List<String> resourceIds = Lists.newArrayList(identifier);
    String accountIdentifier = randomAlphabetic(11);
    Scope scope =
        Scope.builder().level(HarnessScopeLevel.ACCOUNT).parentScope(null).instanceId(accountIdentifier).build();
    ServiceAccount serviceAccount =
        ServiceAccount.builder().identifier(identifier).scopeIdentifier(scope.toString()).build();
    when(serviceAccountClient.listServiceAccounts(accountIdentifier, null, null, resourceIds).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(emptyList())));
    when(serviceAccountService.deleteIfPresent(identifier, scope.toString())).thenReturn(Optional.of(serviceAccount));
    harnessServiceAccountService.sync(identifier, scope);
    verify(serviceAccountClient, atLeast(1)).listServiceAccounts(accountIdentifier, null, null, resourceIds);
    verify(serviceAccountService, times(1)).deleteIfPresent(identifier, scope.toString());
  }
}
