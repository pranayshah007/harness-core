/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class AuditResourceTest extends CategoryTest {
  private AuditService auditService;
  private AuditPermissionValidator auditPermissionValidator;
  private AuditResource auditResource;

  @Before
  public void setup() {
    auditService = mock(AuditService.class);
    auditPermissionValidator = mock(AuditPermissionValidator.class);
    auditResource = new AuditResourceImpl(auditService, auditPermissionValidator);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListWithoutFilter() {
    String accountIdentifier = randomAlphabetic(10);
    ResourceScopeDTO scopeDTO = ResourceScopeDTO.builder().accountIdentifier(accountIdentifier).build();
    doNothing().when(auditPermissionValidator).validate(accountIdentifier, scopeDTO);
    when(auditService.list(eq(accountIdentifier), any(), eq(null))).thenReturn(getPage(emptyList(), 0));

    auditResource.list(accountIdentifier, PageRequest.builder().pageIndex(0).pageSize(10).build(), null);

    verify(auditPermissionValidator, times(1)).validate(accountIdentifier, scopeDTO);
    verifyAuditServiceInvocation(accountIdentifier, null);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListWithFilter() {
    String accountIdentifier = randomAlphabetic(10);
    List<ResourceScopeDTO> scopes = new ArrayList<>();
    scopes.add(
        ResourceScopeDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(randomAlphabetic(10)).build());
    scopes.add(
        ResourceScopeDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(randomAlphabetic(10)).build());
    AuditFilterPropertiesDTO auditFilterPropertiesDTO = AuditFilterPropertiesDTO.builder().scopes(scopes).build();

    scopes.forEach(scope -> doNothing().when(auditPermissionValidator).validate(accountIdentifier, scope));
    when(auditService.list(eq(accountIdentifier), any(), eq(auditFilterPropertiesDTO)))
        .thenReturn(getPage(emptyList(), 0));

    auditResource.list(
        accountIdentifier, PageRequest.builder().pageIndex(0).pageSize(10).build(), auditFilterPropertiesDTO);

    scopes.forEach(scope -> verify(auditPermissionValidator, times(1)).validate(accountIdentifier, scope));
    verifyAuditServiceInvocation(accountIdentifier, auditFilterPropertiesDTO);
  }

  private void verifyAuditServiceInvocation(
      String accountIdentifier, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    final ArgumentCaptor<PageRequest> pageRequestArgumentCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(auditService, times(1))
        .list(eq(accountIdentifier), pageRequestArgumentCaptor.capture(), eq(auditFilterPropertiesDTO));

    PageRequest pageRequest = pageRequestArgumentCaptor.getValue();

    assertEquals(0, pageRequest.getPageIndex());
    assertEquals(10, pageRequest.getPageSize());
    assertEquals(1, pageRequest.getSortOrders().size());
    assertEquals(AuditEventKeys.timestamp, pageRequest.getSortOrders().get(0).getFieldName());
    assertEquals(DESC, pageRequest.getSortOrders().get(0).getOrderType());
  }
}
