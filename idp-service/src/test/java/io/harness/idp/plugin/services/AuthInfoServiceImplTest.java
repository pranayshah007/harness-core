/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
public class AuthInfoServiceImplTest {
  @InjectMocks private AuthInfoServiceImpl authInfoServiceImpl;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private ConfigManagerService configManagerService;
  @Mock private NamespaceService namespaceService;

  @Mock private TransactionTemplate transactionTemplate;

  @Mock private OutboxService outboxService;
  AutoCloseable openMocks;
  private static final String ACCOUNT_ID = "123";
  private static final String GOOGLE_AUTH = "google-auth";
  private static final String GITHUB_AUTH = "github-auth";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetGoogleAuthInfo() {
    when(namespaceService.getNamespaceForAccountIdentifier(ACCOUNT_ID)).thenReturn(new NamespaceInfo());
    when(
        backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(Constants.GOOGLE_AUTH_ENV_VARIABLES, ACCOUNT_ID))
        .thenReturn(buildGoogleAuthEnvVariables());
    AuthInfo authInfo = authInfoServiceImpl.getAuthInfo(GOOGLE_AUTH, ACCOUNT_ID);
    assertEquals(authInfo.getEnvVariables().size(), 2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetGithubAuthInfo() {
    when(namespaceService.getNamespaceForAccountIdentifier(ACCOUNT_ID)).thenReturn(new NamespaceInfo());
    when(
        backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(Constants.GITHUB_AUTH_ENV_VARIABLES, ACCOUNT_ID))
        .thenReturn(buildGithubAuthEnvVariables());
    AuthInfo authInfo = authInfoServiceImpl.getAuthInfo(GITHUB_AUTH, ACCOUNT_ID);
    assertEquals(authInfo.getEnvVariables().size(), 3);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveGoogleAuthEnvVariables() throws Exception {
    String jsonString =
        "{\"auth\":{\"environment\":\"development\",\"providers\":{\"github\":{\"development\":{\"clientId\":\"${AUTH_GOOGLE_CLIENT_ID}\",\"clientSecret\":\"${AUTH_GOOGLE_CLIENT_SECRET}\"}}}}}";
    JsonNode rootNode = objectMapper.readTree(jsonString);
    doNothing().when(backstageEnvVariableService).deleteMultiUsingEnvNames(any(), any());
    when(backstageEnvVariableService.createOrUpdate(buildGoogleAuthEnvVariables(), ACCOUNT_ID))
        .thenReturn(buildGoogleAuthEnvVariables());
    MockedStatic<ConfigManagerUtils> mockStatic = Mockito.mockStatic(ConfigManagerUtils.class);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(ConfigManagerUtils.getAuthConfig(any())).thenReturn("");
    when(ConfigManagerUtils.asJsonNode(any())).thenReturn(rootNode);
    when(ConfigManagerUtils.getAuthConfigSchema(any())).thenReturn("");
    when(ConfigManagerUtils.isValidSchema(any(), any())).thenReturn(true);
    when(configManagerService.saveUpdateAndMergeConfigForAccount(any(), any(), any())).thenReturn(new AppConfig());
    // case for create event - when no previous env variable was present.
    when(backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(any(), any()))
        .thenReturn(Collections.emptyList());
    List<BackstageEnvVariable> backstageEnvVariables =
        authInfoServiceImpl.saveAuthEnvVariables(GOOGLE_AUTH, buildGoogleAuthEnvVariables(), ACCOUNT_ID);
    assertEquals(backstageEnvVariables.size(), 2);
    verify(configManagerService, times(1)).saveUpdateAndMergeConfigForAccount(any(), any(), any());

    // case for update event - when env variables are present
    when(backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(any(), any()))
        .thenReturn(Collections.singletonList(new BackstageEnvVariable()));
    backstageEnvVariables =
        authInfoServiceImpl.saveAuthEnvVariables(GOOGLE_AUTH, buildGoogleAuthEnvVariables(), ACCOUNT_ID);
    assertEquals(backstageEnvVariables.size(), 2);
    verify(configManagerService, times(2)).saveUpdateAndMergeConfigForAccount(any(), any(), any());
    mockStatic.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testSaveGithubAuthEnvVariables() throws Exception {
    String jsonString =
        "{\"auth\":{\"environment\":\"development\",\"providers\":{\"github\":{\"development\":{\"clientId\":\"${AUTH_GITHUB_CLIENT_ID}\",\"clientSecret\":\"${AUTH_GITHUB_CLIENT_SECRET}\"}}}}}";
    String development =
        "{\"development\":{\"clientId\":\"${AUTH_GITHUB_CLIENT_ID}\",\"clientSecret\":\"${AUTH_GITHUB_CLIENT_SECRET}\"}}";
    JsonNode rootNode = objectMapper.readTree(jsonString);
    JsonNode developmentNode = objectMapper.readTree(development);
    doNothing().when(backstageEnvVariableService).deleteMultiUsingEnvNames(any(), any());
    when(backstageEnvVariableService.createOrUpdate(buildGithubAuthEnvVariables(), ACCOUNT_ID))
        .thenReturn(buildGithubAuthEnvVariables());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    MockedStatic<ConfigManagerUtils> mockStatic = Mockito.mockStatic(ConfigManagerUtils.class);
    when(ConfigManagerUtils.getAuthConfig(any())).thenReturn("");
    when(ConfigManagerUtils.asJsonNode(any())).thenReturn(rootNode);
    when(ConfigManagerUtils.getAuthConfigSchema(any())).thenReturn("");
    when(ConfigManagerUtils.isValidSchema(any(), any())).thenReturn(true);
    when(ConfigManagerUtils.getNodeByName(any(), any())).thenReturn(developmentNode);
    when(configManagerService.saveUpdateAndMergeConfigForAccount(any(), any(), any())).thenReturn(new AppConfig());
    when(configManagerService.mergeAndSaveAppConfig(any())).thenReturn(MergedAppConfigEntity.builder().build());
    // case for create event - when no previous env variable was present.
    when(backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(any(), any()))
        .thenReturn(Collections.emptyList());
    List<BackstageEnvVariable> backstageEnvVariables =
        authInfoServiceImpl.saveAuthEnvVariables(GITHUB_AUTH, buildGithubAuthEnvVariables(), ACCOUNT_ID);
    assertEquals(backstageEnvVariables.size(), 3);
    verify(configManagerService, times(1)).saveUpdateAndMergeConfigForAccount(any(), any(), any());

    // case for update event - when env variables are present
    when(backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(any(), any()))
        .thenReturn(Collections.singletonList(new BackstageEnvVariable()));
    backstageEnvVariables =
        authInfoServiceImpl.saveAuthEnvVariables(GITHUB_AUTH, buildGithubAuthEnvVariables(), ACCOUNT_ID);
    assertEquals(backstageEnvVariables.size(), 3);
    verify(configManagerService, times(2)).saveUpdateAndMergeConfigForAccount(any(), any(), any());
    mockStatic.close();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private List<BackstageEnvVariable> buildGoogleAuthEnvVariables() {
    List<BackstageEnvVariable> backstageEnvVariables = new ArrayList<>();
    BackstageEnvConfigVariable backstageEnvConfigVariable = new BackstageEnvConfigVariable();
    backstageEnvConfigVariable.setEnvName("AUTH_GOOGLE_CLIENT_ID");
    backstageEnvConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvConfigVariable.setValue("9812322");
    backstageEnvVariables.add(backstageEnvConfigVariable);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName("AUTH_GOOGLE_CLIENT_SECRET");
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier("google-client-secret");
    backstageEnvVariables.add(backstageEnvConfigVariable);
    return backstageEnvVariables;
  }

  private List<BackstageEnvVariable> buildGithubAuthEnvVariables() {
    List<BackstageEnvVariable> backstageEnvVariables = new ArrayList<>();
    BackstageEnvConfigVariable backstageEnvConfigVariable = new BackstageEnvConfigVariable();
    backstageEnvConfigVariable.setEnvName("AUTH_GITHUB_CLIENT_ID");
    backstageEnvConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvConfigVariable.setValue("19002432");
    backstageEnvVariables.add(backstageEnvConfigVariable);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName("AUTH_GITHUB_CLIENT_SECRET");
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier("github-client-secret");
    backstageEnvVariables.add(backstageEnvConfigVariable);
    BackstageEnvConfigVariable envConfigVariable = new BackstageEnvConfigVariable();
    envConfigVariable.setEnvName("AUTH_GITHUB_ENTERPRISE_INSTANCE_URL");
    envConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    envConfigVariable.setValue("https://ghe.harness.com");
    backstageEnvVariables.add(envConfigVariable);
    return backstageEnvVariables;
  }
}
