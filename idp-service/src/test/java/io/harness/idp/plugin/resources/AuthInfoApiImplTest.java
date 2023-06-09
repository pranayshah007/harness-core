/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.plugin.services.AuthInfoService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.*;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class AuthInfoApiImplTest {
  @InjectMocks private AuthInfoApiImpl authInfoApiImpl;
  @Mock private AuthInfoService authInfoService;
  private static final String ACCOUNT_ID = "123";
  private static final String GOOGLE_AUTH = "google-auth";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAuthInfoAuthId() {
    AuthInfo authInfo = new AuthInfo();
    authInfo.setEnvVariables(new ArrayList<>());
    authInfo.setNamespace("default");
    when(authInfoService.getAuthInfo(GOOGLE_AUTH, ACCOUNT_ID)).thenReturn(authInfo);
    Response response = authInfoApiImpl.getAuthInfoAuthId(GOOGLE_AUTH, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAuthInfoAuthIdThrowsException() {
    when(authInfoService.getAuthInfo(GOOGLE_AUTH, ACCOUNT_ID)).thenThrow(InvalidRequestException.class);
    Response response = authInfoApiImpl.getAuthInfoAuthId(GOOGLE_AUTH, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveAuthInfoAuthId() throws Exception {
    BackstageEnvVariableBatchRequest request = new BackstageEnvVariableBatchRequest();
    request.setEnvVariables(buildAuthEnvVariables());
    when(authInfoService.saveAuthEnvVariables(GOOGLE_AUTH, buildAuthEnvVariables(), ACCOUNT_ID))
        .thenReturn(buildAuthEnvVariables());
    Response response = authInfoApiImpl.saveAuthInfoAuthId(GOOGLE_AUTH, request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(((List<BackstageEnvVariableResponse>) response.getEntity()).size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveAuthInfoAuthIdThrowsException() throws Exception {
    BackstageEnvVariableBatchRequest request = new BackstageEnvVariableBatchRequest();
    request.setEnvVariables(buildAuthEnvVariables());
    when(authInfoService.saveAuthEnvVariables(GOOGLE_AUTH, buildAuthEnvVariables(), ACCOUNT_ID))
        .thenThrow(InvalidRequestException.class);
    Response response = authInfoApiImpl.saveAuthInfoAuthId(GOOGLE_AUTH, request, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  private List<BackstageEnvVariable> buildAuthEnvVariables() {
    List<BackstageEnvVariable> backstageEnvVariables = new ArrayList<>();
    BackstageEnvConfigVariable backstageEnvConfigVariable = new BackstageEnvConfigVariable();
    backstageEnvConfigVariable.setEnvName("GOOGLE_AUTH_CLIENT_ID");
    backstageEnvConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvConfigVariable.setValue("9812322");
    backstageEnvVariables.add(backstageEnvConfigVariable);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName("GOOGLE_AUTH_CLIENT_SECRET");
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier("google-client-secret");
    backstageEnvVariables.add(backstageEnvConfigVariable);
    return backstageEnvVariables;
  }
}
