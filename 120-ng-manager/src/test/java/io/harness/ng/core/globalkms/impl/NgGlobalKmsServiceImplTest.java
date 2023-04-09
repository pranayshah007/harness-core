/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.globalkms.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.context.GlobalContext;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.globalkms.dto.ConnectorSecretResponseDTO;
import io.harness.request.RequestContext;
import io.harness.request.RequestContextData;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.services.NgConnectorManagerClientService;

import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class NgGlobalKmsServiceImplTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private SecretCrudService ngSecretService;
  @Mock private NgConnectorManagerClientService ngConnectorManagerClientService;
  @Mock private NGSecretManagerService ngSecretManagerService;
  private NgGlobalKmsServiceImpl globalKmsService;
  @Captor ArgumentCaptor<String> accountIdentifierArgumentCaptor;
  @Captor ArgumentCaptor<String> orgIdentifierArgumentCaptor;
  @Captor ArgumentCaptor<String> projectIdentifierArgumentCaptor;
  @Captor ArgumentCaptor<String> identifierArgumentCaptor;
  @Captor ArgumentCaptor<ConnectorDTO> connectorDTOArgumentCaptor;
  @Captor ArgumentCaptor<SecretDTOV2> secretDTOV2ArgumentCaptor;
  @Captor ArgumentCaptor<SecretManagerConfigDTO> secretManagerConfigDTOArgumentCaptor;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  String accountId = UUIDGenerator.generateUuid();
  String userId = UUIDGenerator.generateUuid();
  String secretIdentifier = HARNESS_SECRET_MANAGER_IDENTIFIER + "_" + randomAlphabetic(10);
  UserPrincipal userPrincipal;
  static final String GET_USER_PRINCIPAL_OR_THROW = "getUserPrincipalOrThrow";
  static final String VALIDATE = "validate";
  static final String CAN_UPDATE_GLOBAL_KMS = "canUpdateGlobalKms";
  static final String CHECK_FOR_HARNESS_SUPPORT_USER = "checkForHarnessSupportUser";
  static final String CHECK_CONNECTOR_TYPE_AND_CREDENTIALS_MATCH = "checkConnectorTypeAndCredentialsMatch";
  static final String CHECK_CONNECTOR_HAS_ONLY_ACCOUNT_SCOPE_INFO = "checkConnectorHasOnlyAccountScopeInfo";
  static final String GET_GLOBAL_KMS_SECRET_OR_THROW = "getGlobalKmsSecretOrThrow";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    globalKmsService = new NgGlobalKmsServiceImpl(
        connectorService, ngSecretService, ngConnectorManagerClientService, ngSecretManagerService);
    userPrincipal = new UserPrincipal(userId, randomAlphabetic(10), userId, accountId);
  }

  private GcpKmsConnectorDTO getGlobalGcpKmsConnectorDto() {
    SecretRefData secretRefData = SecretRefData.builder().identifier(secretIdentifier).scope(Scope.ACCOUNT).build();
    GcpKmsConnectorDTO globalGcpKmsConnectorDto =
        GcpKmsConnectorDTO.builder().isDefault(true).credentials(secretRefData).build();
    globalGcpKmsConnectorDto.setHarnessManaged(true);
    return globalGcpKmsConnectorDto;
  }

  private ConnectorResponseDTO getGlobalKmsConnector() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .connectorConfig(getGlobalGcpKmsConnectorDto())
                       .connectorType(ConnectorType.GCP_KMS)
                       .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                       .build())
        .build();
  }

  private ConnectorInfoDTO getConnectorInfoDTO() {
    SecretRefData secretRefData = SecretRefData.builder().identifier(secretIdentifier).scope(Scope.ACCOUNT).build();
    GcpKmsConnectorDTO connectorConfig = GcpKmsConnectorDTO.builder().credentials(secretRefData).build();
    return ConnectorInfoDTO.builder()
        .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
        .connectorType(ConnectorType.GCP_KMS)
        .connectorConfig(connectorConfig)
        .build();
  }

  private ConnectorDTO getConnectorDTO() {
    return ConnectorDTO.builder().connectorInfo(getConnectorInfoDTO()).build();
  }

  private SecretDTOV2 getSecretDTOV2() {
    return SecretDTOV2.builder()
        .identifier(secretIdentifier)
        .spec(SecretTextSpecDTO.builder().value(randomAlphabetic(50)).build())
        .build();
  }

  private SecretResponseWrapper getSecretResponseWrapper() {
    return SecretResponseWrapper.builder().secret(SecretDTOV2.builder().identifier(secretIdentifier).build()).build();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKms() throws Exception {
    try (MockedStatic<GlobalContextManager> globalContextManagerMockedStatic = mockStatic(GlobalContextManager.class)) {
      ConnectorResponseDTO globalKmsConnector = getGlobalKmsConnector();
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      SecretResponseWrapper secretResponseWrapper = getSecretResponseWrapper();
      when(connectorService.get(GLOBAL_ACCOUNT_ID, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER))
          .thenReturn(Optional.of(globalKmsConnector));
      when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
               secretDTOV2.getIdentifier()))
          .thenReturn(Optional.of(secretResponseWrapper));
      mockForValidUser();
      when(ngSecretManagerService.validateNGSecretManager(eq(GLOBAL_ACCOUNT_ID), any())).thenReturn(true);
      when(ngSecretService.update(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
               secretDTOV2.getIdentifier(), secretDTOV2))
          .thenReturn(SecretResponseWrapper.builder().build());
      when(connectorService.update(
               ConnectorDTO.builder().connectorInfo(globalKmsConnector.getConnector()).build(), GLOBAL_ACCOUNT_ID))
          .thenReturn(ConnectorResponseDTO.builder().build());
      ConnectorSecretResponseDTO response = globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      globalContextManagerMockedStatic.verify(() -> GlobalContextManager.obtainGlobalContext(), times(1));
      verify(ngConnectorManagerClientService, times(1)).isHarnessSupportUser(userId);
      verify(ngSecretService, times(1))
          .update(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
              secretDTOV2.getIdentifier(), secretDTOV2);
      verify(ngSecretService)
          .update(accountIdentifierArgumentCaptor.capture(), orgIdentifierArgumentCaptor.capture(),
              projectIdentifierArgumentCaptor.capture(), identifierArgumentCaptor.capture(),
              secretDTOV2ArgumentCaptor.capture());
      assertEquals(GLOBAL_ACCOUNT_ID, accountIdentifierArgumentCaptor.getValue());
      assertEquals(secretDTOV2.getOrgIdentifier(), orgIdentifierArgumentCaptor.getValue());
      assertEquals(secretDTOV2.getProjectIdentifier(), projectIdentifierArgumentCaptor.getValue());
      assertEquals(secretDTOV2, secretDTOV2ArgumentCaptor.getValue());
      verify(connectorService, times(1))
          .update(ConnectorDTO.builder().connectorInfo(globalKmsConnector.getConnector()).build(), GLOBAL_ACCOUNT_ID);
      verify(connectorService).update(connectorDTOArgumentCaptor.capture(), accountIdentifierArgumentCaptor.capture());
      assertEquals(GLOBAL_ACCOUNT_ID, accountIdentifierArgumentCaptor.getValue());
      assertEquals(ConnectorDTO.builder().connectorInfo(globalKmsConnector.getConnector()).build(),
          connectorDTOArgumentCaptor.getValue());
      ConnectorDTO connectorUpdateDto = connectorDTOArgumentCaptor.getValue();
      GcpKmsConnectorDTO connectorUpdateConfigDto =
          (GcpKmsConnectorDTO) connectorUpdateDto.getConnectorInfo().getConnectorConfig();
      GcpKmsConnectorDTO connectorConfigDto = (GcpKmsConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
      assertEquals(connectorConfigDto.getKeyRing(), connectorUpdateConfigDto.getKeyRing());
      assertEquals(connectorConfigDto.getKeyName(), connectorUpdateConfigDto.getKeyName());
      assertEquals(connectorConfigDto.getProjectId(), connectorUpdateConfigDto.getProjectId());
      assertEquals(connectorConfigDto.getRegion(), connectorUpdateConfigDto.getRegion());
      assertNotNull(response.getConnectorResponseDTO());
      assertNotNull(response.getSecretResponseWrapper());
    }
  }

  private void mockForValidUser() {
    when(ngConnectorManagerClientService.isHarnessSupportUser(userId)).thenReturn(true);
    GlobalContext globalContext = mock(GlobalContext.class);
    when(globalContext.get(PRINCIPAL_CONTEXT))
        .thenReturn(PrincipalContextData.builder().principal(userPrincipal).build());
    when(GlobalContextManager.obtainGlobalContext()).thenReturn(globalContext);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKms_connector_not_present() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      mockForValidUser();
      when(connectorService.get(GLOBAL_ACCOUNT_ID, null, null, connectorDTO.getConnectorInfo().getIdentifier()))
          .thenReturn(Optional.ofNullable(null));
      exceptionRule.expect(NotFoundException.class);
      exceptionRule.expectMessage(String.format("Global connector of type %s not found", ConnectorType.GCP_KMS));
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(connectorService, times(1))
          .get(GLOBAL_ACCOUNT_ID, null, null, connectorDTO.getConnectorInfo().getIdentifier());
      verify(connectorService)
          .get(accountIdentifierArgumentCaptor.capture(), orgIdentifierArgumentCaptor.capture(),
              projectIdentifierArgumentCaptor.capture(), identifierArgumentCaptor.capture());
      assertEquals(GLOBAL_ACCOUNT_ID, accountIdentifierArgumentCaptor.getValue());
      assertNull(orgIdentifierArgumentCaptor.getValue());
      assertNull(projectIdentifierArgumentCaptor.getValue());
      assertEquals(connectorDTO.getConnectorInfo().getIdentifier(), identifierArgumentCaptor.getValue());
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKms_connector_type_not_gcp_kms() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      mockForValidUser();
      when(connectorService.get(GLOBAL_ACCOUNT_ID, null, null, connectorDTO.getConnectorInfo().getIdentifier()))
          .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                      .connector(ConnectorInfoDTO.builder().connectorType(ConnectorType.LOCAL).build())
                                      .build()));
      exceptionRule.expect(NotFoundException.class);
      exceptionRule.expectMessage(String.format("Global connector of type %s not found", ConnectorType.GCP_KMS));
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(connectorService, times(1))
          .get(GLOBAL_ACCOUNT_ID, null, null, connectorDTO.getConnectorInfo().getIdentifier());
      verify(connectorService)
          .get(accountIdentifierArgumentCaptor.capture(), orgIdentifierArgumentCaptor.capture(),
              projectIdentifierArgumentCaptor.capture(), identifierArgumentCaptor.capture());
      assertEquals(GLOBAL_ACCOUNT_ID, accountIdentifierArgumentCaptor.getValue());
      assertNull(orgIdentifierArgumentCaptor.getValue());
      assertNull(projectIdentifierArgumentCaptor.getValue());
      assertEquals(connectorDTO.getConnectorInfo().getIdentifier(), identifierArgumentCaptor.getValue());
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetUserPrincipalOrThrow_exception_global_context_null() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      when(GlobalContextManager.obtainGlobalContext()).thenReturn(null);
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Not authorized to update in current context");
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(GlobalContextManager.class, times(1));
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetUserPrincipalOrThrow_exception_not_principal_context() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      GlobalContext globalContext = new GlobalContext();
      globalContext.upsertGlobalContextRecord(
          RequestContextData.builder().requestContext(RequestContext.builder().build()).build());
      when(GlobalContextManager.obtainGlobalContext()).thenReturn(globalContext);
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Not authorized to update in current context");
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(GlobalContextManager.class, times(1));
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetUserPrincipalOrThrow_not_user_principal() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      GlobalContext globalContext = new GlobalContext();
      globalContext.upsertGlobalContextRecord(PrincipalContextData.builder().principal(new ServicePrincipal()).build());
      when(GlobalContextManager.obtainGlobalContext()).thenReturn(globalContext);
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Not authorized to update in current context");
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(GlobalContextManager.class, times(1));
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_not_harness_secret_manager() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      mockForValidUser();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      ConnectorDTO nonHarnessConnector =
          ConnectorDTO.builder()
              .connectorInfo(ConnectorInfoDTO.builder().identifier(UUIDGenerator.generateUuid()).build())
              .build();
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Update operation not supported");
      globalKmsService.updateGlobalKms(nonHarnessConnector, secretDTOV2);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_not_gcp_kms() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      mockForValidUser();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      ConnectorDTO nonHarnessConnector = ConnectorDTO.builder()
                                             .connectorInfo(ConnectorInfoDTO.builder()
                                                                .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                                                .connectorType(ConnectorType.LOCAL)
                                                                .build())
                                             .build();
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Update operation not supported");
      globalKmsService.updateGlobalKms(nonHarnessConnector, secretDTOV2);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_credential_secret_identifier_mismatch() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      mockForValidUser();
      ConnectorDTO connectorDTO = getConnectorDTO();
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Secret credential reference cannot be changed");
      SecretDTOV2 secretDTO = SecretDTOV2.builder().identifier(UUIDGenerator.generateUuid()).build();
      globalKmsService.updateGlobalKms(connectorDTO, secretDTO);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorTypeAndCredentialsMatch_connector_credential_scope_not_global() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      mockForValidUser();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      ConnectorDTO connectorDTO =
          ConnectorDTO.builder()
              .connectorInfo(
                  ConnectorInfoDTO.builder()
                      .identifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                      .connectorType(ConnectorType.GCP_KMS)
                      .connectorConfig(
                          GcpKmsConnectorDTO.builder()
                              .credentials(
                                  SecretRefData.builder().identifier(secretIdentifier).scope(Scope.ORG).build())
                              .build())
                      .build())
              .build();
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Invalid credential scope");
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCheckConnectorHasOnlyAccountScopeInfo_has_org() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      ConnectorDTO connectorDTO = getConnectorDTO();
      connectorDTO.getConnectorInfo().setOrgIdentifier(UUIDGenerator.generateUuid());
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      mockForValidUser();
      when(connectorService.get(GLOBAL_ACCOUNT_ID, connectorDTO.getConnectorInfo().getOrgIdentifier(), null,
               HARNESS_SECRET_MANAGER_IDENTIFIER))
          .thenReturn(Optional.of(getGlobalKmsConnector()));
      when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
               secretDTOV2.getIdentifier()))
          .thenReturn(Optional.of(getSecretResponseWrapper()));
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Global connector cannot have org/project identifier");
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetGlobalKmsSecretOrThrow_secret_not_exist() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      mockForValidUser();
      ConnectorDTO connectorDTO = getConnectorDTO();
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      when(connectorService.get(GLOBAL_ACCOUNT_ID, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER))
          .thenReturn(Optional.of(getGlobalKmsConnector()));
      when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
               secretDTOV2.getIdentifier()))
          .thenReturn(Optional.ofNullable(null));
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage(
          String.format("Secret with identifier %s does not exist in global scope", secretDTOV2.getIdentifier()));
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(ngSecretService, times(1))
          .get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
              secretDTOV2.getIdentifier());
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidate_when_false() {
    try (MockedStatic<GlobalContextManager> ignored = mockStatic(GlobalContextManager.class)) {
      SecretDTOV2 secretDTOV2 = getSecretDTOV2();
      ConnectorDTO connectorDTO = getConnectorDTO();
      connectorDTO.setConnectorInfo(getConnectorInfoDTO());
      mockForValidUser();
      when(connectorService.get(GLOBAL_ACCOUNT_ID, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER))
          .thenReturn(Optional.of(getGlobalKmsConnector()));
      when(ngSecretService.get(GLOBAL_ACCOUNT_ID, secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier(),
               secretDTOV2.getIdentifier()))
          .thenReturn(Optional.of(getSecretResponseWrapper()));
      when(ngSecretManagerService.validateNGSecretManager(anyString(), any())).thenReturn(false);
      exceptionRule.expect(InvalidRequestException.class);
      exceptionRule.expectMessage("Failed to validate secret manager");
      globalKmsService.updateGlobalKms(connectorDTO, secretDTOV2);
      verify(ngSecretManagerService, times(1)).validateNGSecretManager(anyString(), any());
    }
  }
}
