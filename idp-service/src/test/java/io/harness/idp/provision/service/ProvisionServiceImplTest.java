/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.provision.service;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.client.NgConnectorManagerClient;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import java.io.IOException;
import java.util.List;
import okhttp3.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
public class ProvisionServiceImplTest {
  @Spy @InjectMocks private ProvisionServiceImpl provisionServiceImpl;
  @InjectMocks IdpCommonService idpCommonService;
  @Mock NgConnectorManagerClient ngConnectorManagerClient;
  @Mock BackstagePermissionsService backstagePermissionsService;
  @Mock SecretManagerClientService ngSecretService;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock ConfigManagerService configManagerService;
  @Mock private ProvisionModuleConfig provisionModuleConfig;
  @Mock private OkHttpClient client;
  @Mock private Call call;
  private static final String ERROR_MESSAGE =
      "Invalid request: Secret with identifier IDP_BACKEND_SECRET already exists in this scope";
  private static final String ADMIN_USER_ID = "lv0euRhKRCyiXWzS7pOg6g";
  private static final String DEFAULT_USER_ID = "0osgWsTZRsSZ8RWfjLRkEg";
  private static final String ACCOUNT_ID = "123";
  private static final String NAMESPACE = "8982311";
  static final String TEST_USERGROUP = " ";
  static final List<String> TEST_PERMISSIONS =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckUserAuthorization() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = Mockito.mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);
    idpCommonService.checkUserAuthorization();
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(ADMIN_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = SARTHAK_KASAT)
  public void testCreateDefaultBackstagePermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    provisionServiceImpl.createDefaultPermissions(ACCOUNT_ID);
    verify(backstagePermissionsService).createPermissions(backstagePermissions, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Category(UnitTests.class)
  @Owner(developers = VIGNESWARA)
  public void testCreateDefaultBackstagePermissionsThrowsException() {
    when(backstagePermissionsService.createPermissions(any(), any()))
        .thenThrow(new InvalidRequestException("Creating permission failed"));
    provisionServiceImpl.createDefaultPermissions(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckUserAuthorizationThrowsException() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = Mockito.mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(DEFAULT_USER_ID, "default@harness.io", "default", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    try {
      idpCommonService.checkUserAuthorization();
    } catch (Exception e) {
      String expectedMessage = String.format("User : %s not allowed to do action on IDP module", DEFAULT_USER_ID);
      Assert.assertEquals(expectedMessage, e.getMessage());
    }
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(DEFAULT_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateBackstageBackendSecret() {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    provisionServiceImpl.createBackstageBackendSecret(ACCOUNT_ID);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(Constants.BACKEND_SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(dto.getSecret().getIdentifier());
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    verify(backstageEnvVariableService).create(backstageEnvSecretVariable, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testUpdateBackstageBackendSecret() {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any()))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE));
    when(ngSecretService.updateSecret(eq(Constants.IDP_BACKEND_SECRET), eq(ACCOUNT_ID), eq(null), eq(null), any()))
        .thenReturn(dto);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(Constants.BACKEND_SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(dto.getSecret().getIdentifier());
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    when(backstageEnvVariableService.create(backstageEnvSecretVariable, ACCOUNT_ID))
        .thenThrow(new DuplicateKeyException(""));
    provisionServiceImpl.createBackstageBackendSecret(ACCOUNT_ID);
    verify(backstageEnvVariableService).update(backstageEnvSecretVariable, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateBackstageBackendSecretThrowsException() {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any()))
        .thenThrow(new InvalidRequestException("Unexpected Error occurred"));
    provisionServiceImpl.createBackstageBackendSecret(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testTriggerPipeline() throws Exception {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    Request request = new Request.Builder().url("https://harness.trigger.com").method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from Trigger"))
            .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any())).thenReturn(new BackstageEnvVariable());
    when(backstagePermissionsService.createPermissions(any(), any())).thenReturn(new BackstagePermissions());
    when(configManagerService.mergeAndSaveAppConfig(any())).thenReturn(MergedAppConfigEntity.builder().build());
    when(provisionModuleConfig.getTriggerPipelineUrl()).thenReturn("https://harness.trigger.com");
    when(provisionServiceImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(responseSuccess);
    provisionServiceImpl.triggerPipelineAndCreatePermissions(ACCOUNT_ID, NAMESPACE);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testTriggerPipelineThrowsIOException() throws Exception {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any())).thenReturn(new BackstageEnvVariable());
    when(backstagePermissionsService.createPermissions(any(), any())).thenThrow(new DuplicateKeyException(""));
    when(configManagerService.mergeAndSaveAppConfig(any())).thenReturn(MergedAppConfigEntity.builder().build());
    when(provisionModuleConfig.getTriggerPipelineUrl()).thenReturn("https://harness.trigger.com");
    when(provisionServiceImpl.getOkHttpClient()).thenReturn(client);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenThrow(IOException.class);
    provisionServiceImpl.triggerPipelineAndCreatePermissions(ACCOUNT_ID, NAMESPACE);
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateBackstageOverrideConfigThrowsException() throws Exception {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any())).thenReturn(new BackstageEnvVariable());
    when(backstagePermissionsService.createPermissions(any(), any())).thenThrow(new DuplicateKeyException(""));
    when(configManagerService.mergeAndSaveAppConfig(any())).thenThrow(InvalidRequestException.class);
    provisionServiceImpl.triggerPipelineAndCreatePermissions(ACCOUNT_ID, NAMESPACE);
  }
}
