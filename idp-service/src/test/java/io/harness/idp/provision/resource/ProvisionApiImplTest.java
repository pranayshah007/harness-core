/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.provision.resource;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.rule.Owner;

import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
public class ProvisionApiImplTest {
  @InjectMocks private ProvisionApiImpl provisionApiImpl;
  @Mock private NamespaceService namespaceService;
  @Mock private ProvisionService provisionService;
  @Mock private IdpCommonService idpCommonService;
  private static final String ACCOUNT_ID = "123";
  private static final String NAMESPACE = "default";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProvisionIdp() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    NamespaceEntity namespaceEntity = NamespaceEntity.builder().accountIdentifier(ACCOUNT_ID).id(NAMESPACE).build();
    when(namespaceService.saveAccountIdNamespace(ACCOUNT_ID)).thenReturn(namespaceEntity);
    doNothing().when(provisionService).triggerPipelineAndCreatePermissions(ACCOUNT_ID, NAMESPACE);
    Response response = provisionApiImpl.provisionIdp(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProvisionIdpWithNamespaceExists() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    NamespaceEntity namespaceEntity = NamespaceEntity.builder().accountIdentifier(ACCOUNT_ID).id(NAMESPACE).build();
    when(namespaceService.saveAccountIdNamespace(ACCOUNT_ID)).thenThrow(DuplicateKeyException.class);
    when(namespaceService.getNamespaceForAccountIdentifier(ACCOUNT_ID))
        .thenReturn(NamespaceMapper.toDTO(namespaceEntity));
    doNothing().when(provisionService).triggerPipelineAndCreatePermissions(ACCOUNT_ID, NAMESPACE);
    Response response = provisionApiImpl.provisionIdp(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProvisionIdpThrowsException() {
    doNothing().when(idpCommonService).checkUserAuthorization();
    when(namespaceService.saveAccountIdNamespace(ACCOUNT_ID)).thenThrow(InvalidRequestException.class);
    Response response = provisionApiImpl.provisionIdp(ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }
}
