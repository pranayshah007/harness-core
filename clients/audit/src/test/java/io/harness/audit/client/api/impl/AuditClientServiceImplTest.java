/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.client.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.CorrelationContext.getCorrelationIdKey;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.singletonMap;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.PrincipalType;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.remote.AuditClient;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.context.MdcGlobalContextData;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AuditClientServiceImplTest extends CategoryTest {
  private AuditClient auditClient;
  private AuditClientServiceImpl auditClientService;

  @Before
  public void setup() {
    auditClient = mock(AuditClient.class, RETURNS_DEEP_STUBS);
    auditClientService = new AuditClientServiceImpl(auditClient);
  }

  private AuditEntry getAuditEntry() {
    ResourceScopeDTO resourceScope = ResourceScopeDTO.builder().accountIdentifier(randomAlphabetic(10)).build();
    ResourceDTO resource = ResourceDTO.builder().type("ORGANIZATION").identifier(randomAlphabetic(10)).build();
    long timestamp = 171819;
    return AuditEntry.builder()
        .resourceScope(resourceScope)
        .resource(resource)
        .action(Action.CREATE)
        .insertId(randomAlphabetic(10))
        .timestamp(timestamp)
        .module(ModuleType.CORE)
        .newYaml(randomAlphabetic(10))
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testPublishAudit() throws IOException {
    AuditEntry auditEntry = getAuditEntry();
    GlobalContext globalContext = new GlobalContext();

    UserPrincipal userPrincipal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    PrincipalContextData principalContextData = PrincipalContextData.builder().principal(userPrincipal).build();
    globalContext.upsertGlobalContextRecord(principalContextData);

    MdcGlobalContextData mdcGlobalContextData =
        MdcGlobalContextData.builder().map(singletonMap(getCorrelationIdKey(), randomAlphabetic(10))).build();
    globalContext.upsertGlobalContextRecord(mdcGlobalContextData);

    final ArgumentCaptor<AuditEventDTO> auditEventDTOArgumentCaptor = ArgumentCaptor.forClass(AuditEventDTO.class);
    verifyMethodInvocation(auditEventDTOArgumentCaptor, auditEntry, globalContext);
    AuditEventDTO auditEventDTO = auditEventDTOArgumentCaptor.getValue();

    assertAuditEventDTO(auditEntry, auditEventDTO);
    assertNotNull(auditEventDTO.getAuthenticationInfo());
    assertNotNull(auditEventDTO.getAuthenticationInfo().getPrincipal());
    assertEquals(PrincipalType.USER, auditEventDTO.getAuthenticationInfo().getPrincipal().getType());
    assertEquals(userPrincipal.getEmail(), auditEventDTO.getAuthenticationInfo().getPrincipal().getIdentifier());
    assertEquals(userPrincipal.getUsername(), auditEventDTO.getAuthenticationInfo().getLabels().get("username"));
    assertEquals(userPrincipal.getName(), auditEventDTO.getAuthenticationInfo().getLabels().get("userId"));
    assertNotNull(auditEventDTO.getInternalInfo());
    assertEquals(
        mdcGlobalContextData.getMap().get(getCorrelationIdKey()), auditEventDTO.getInternalInfo().get("correlationId"));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testPublishAuditWithNullGlobalContext() throws IOException {
    AuditEntry auditEntry = getAuditEntry();

    final ArgumentCaptor<AuditEventDTO> auditEventDTOArgumentCaptor = ArgumentCaptor.forClass(AuditEventDTO.class);
    verifyMethodInvocation(auditEventDTOArgumentCaptor, auditEntry, null);
    AuditEventDTO auditEventDTO = auditEventDTOArgumentCaptor.getValue();

    assertAuditEventDTO(auditEntry, auditEventDTO);
  }

  private void assertAuditEventDTO(AuditEntry auditEntry, AuditEventDTO auditEventDTO) {
    assertEquals(auditEntry.getInsertId(), auditEventDTO.getInsertId());
    assertEquals(auditEntry.getResourceScope(), auditEventDTO.getResourceScope());
    assertEquals(auditEntry.getResource(), auditEventDTO.getResource());
    assertEquals(auditEntry.getTimestamp(), auditEventDTO.getTimestamp().longValue());
    assertEquals(auditEntry.getAction(), auditEventDTO.getAction());
    assertEquals(auditEntry.getModule(), auditEventDTO.getModule());
    assertEquals(auditEntry.getNewYaml(), auditEventDTO.getYamlDiffRecord().getNewYaml());
    assertNull(auditEventDTO.getYamlDiffRecord().getOldYaml());
    assertNull(auditEventDTO.getAuditEventData());
  }

  private void verifyMethodInvocation(ArgumentCaptor<AuditEventDTO> auditEventDTOArgumentCaptor, AuditEntry auditEntry,
      GlobalContext globalContext) throws IOException {
    ResponseDTO<Boolean> restResponse = ResponseDTO.newResponse(true);
    Response<ResponseDTO<Boolean>> response = Response.success(restResponse);
    Call<ResponseDTO<Boolean>> responseDTOCall = mock(Call.class);
    when(auditClient.createAudit(any())).thenReturn(responseDTOCall);
    when(responseDTOCall.execute()).thenReturn(response);

    auditClientService.publishAudit(auditEntry, globalContext);

    verify(auditClient, times(1)).createAudit(auditEventDTOArgumentCaptor.capture());
  }
}
