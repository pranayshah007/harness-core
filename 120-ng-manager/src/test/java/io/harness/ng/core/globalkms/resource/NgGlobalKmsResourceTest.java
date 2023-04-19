/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.globalkms.resource;

import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.EDIT_CONNECTOR_PERMISSION;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.secrets.SecretPermissions.SECRET_EDIT_PERMISSION;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.globalkms.dto.ConnectorSecretRequestDTO;
import io.harness.ng.core.globalkms.dto.ConnectorSecretResponseDTO;
import io.harness.ng.core.globalkms.services.NgGlobalKmsService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NgGlobalKmsResourceTest extends CategoryTest {
  @Mock private NgGlobalKmsService ngGlobalKmsService;
  @Mock private AccessControlClient accessControlClient;
  private NgGlobalKmsResource ngGlobalKmsResource;
  @Captor ArgumentCaptor<ConnectorDTO> connectorDTOArgumentCaptor;
  @Captor ArgumentCaptor<SecretDTOV2> secretDTOV2ArgumentCaptor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ngGlobalKmsResource = new NgGlobalKmsResource(ngGlobalKmsService, accessControlClient);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdate() {
    ConnectorSecretRequestDTO dto =
        ConnectorSecretRequestDTO.builder()
            .connector(ConnectorDTO.builder()
                           .connectorInfo(ConnectorInfoDTO.builder().identifier(UUIDGenerator.generateUuid()).build())
                           .build())
            .secret(SecretDTOV2.builder().identifier(UUIDGenerator.generateUuid()).build())
            .build();
    String accountIdentifier = UUIDGenerator.generateUuid();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(ngGlobalKmsService.updateGlobalKms(dto.getConnector(), dto.getSecret()))
        .thenReturn(ConnectorSecretResponseDTO.builder().build());
    ngGlobalKmsResource.update(dto, accountIdentifier);
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), eq(EDIT_CONNECTOR_PERMISSION));
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), eq(SECRET_EDIT_PERMISSION));
    verify(ngGlobalKmsService, times(1)).updateGlobalKms(dto.getConnector(), dto.getSecret());
    verify(ngGlobalKmsService)
        .updateGlobalKms(connectorDTOArgumentCaptor.capture(), secretDTOV2ArgumentCaptor.capture());
    assertEquals(dto.getConnector(), connectorDTOArgumentCaptor.getValue());
    assertEquals(dto.getSecret(), secretDTOV2ArgumentCaptor.getValue());
  }
}
