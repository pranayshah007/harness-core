/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.principals.serviceaccounts.persistence.ServiceAccountDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ServiceAccountServiceImplTest extends AccessControlCoreTestBase {
  private ServiceAccountDao serviceAccountDao;
  private RoleAssignmentService roleAssignmentService;
  private TransactionTemplate transactionTemplate;
  private ServiceAccountService serviceAccountService;
  private ScopeService scopeService;

  @Before
  public void setup() {
    serviceAccountDao = mock(ServiceAccountDao.class);
    roleAssignmentService = mock(RoleAssignmentService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    scopeService = mock(ScopeService.class);
    serviceAccountService =
        spy(new ServiceAccountServiceImpl(serviceAccountDao, transactionTemplate, roleAssignmentService, scopeService));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateIfNotPresent() {
    ServiceAccount serviceAccount = ServiceAccount.builder().build();
    when(serviceAccountDao.createIfNotPresent(serviceAccount)).thenReturn(serviceAccount);
    assertEquals(serviceAccount, serviceAccountService.createIfNotPresent(serviceAccount));
    verify(serviceAccountDao, times(1)).createIfNotPresent(serviceAccount);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().build();
    String scopeIdentifier = randomAlphabetic(10);
    PageResponse pageResponse = PageResponse.getEmptyPageResponse(pageRequest);
    when(serviceAccountDao.list(pageRequest, scopeIdentifier)).thenReturn(pageResponse);
    assertEquals(pageResponse, serviceAccountService.list(pageRequest, scopeIdentifier));
    verify(serviceAccountDao, times(1)).list(pageRequest, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<ServiceAccount> serviceAccountOptional = Optional.of(ServiceAccount.builder().build());
    when(serviceAccountDao.get(identifier, scopeIdentifier)).thenReturn(serviceAccountOptional);
    assertEquals(serviceAccountOptional, serviceAccountService.get(identifier, scopeIdentifier));
    verify(serviceAccountDao, times(1)).get(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresent() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<ServiceAccount> serviceAccountOptional = Optional.of(ServiceAccount.builder().build());
    when(serviceAccountDao.get(identifier, scopeIdentifier)).thenReturn(serviceAccountOptional);
    when(transactionTemplate.execute(any())).thenReturn(serviceAccountOptional);
    assertEquals(serviceAccountOptional, serviceAccountService.deleteIfPresent(identifier, scopeIdentifier));
    verify(serviceAccountDao, times(1)).get(identifier, scopeIdentifier);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresentNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<ServiceAccount> serviceAccountOptional = Optional.empty();
    when(serviceAccountDao.get(identifier, scopeIdentifier)).thenReturn(serviceAccountOptional);
    assertEquals(serviceAccountOptional, serviceAccountService.deleteIfPresent(identifier, scopeIdentifier));
    verify(serviceAccountDao, times(1)).get(identifier, scopeIdentifier);
  }
}
