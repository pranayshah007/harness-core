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
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
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

import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ProvisionServiceImplTest {
  @InjectMocks private ProvisionServiceImpl provisionServiceImpl;
  @InjectMocks IdpCommonService idpCommonService;
  @Mock NgConnectorManagerClient ngConnectorManagerClient;
  @Mock BackstagePermissionsService backstagePermissionsService;
  @Mock SecretManagerClientService ngSecretService;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  private static final String ADMIN_USER_ID = "lv0euRhKRCyiXWzS7pOg6g";
  private static final String DEFAULT_USER_ID = "0osgWsTZRsSZ8RWfjLRkEg";
  private static final String ACCOUNT_ID = "123";
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
}
