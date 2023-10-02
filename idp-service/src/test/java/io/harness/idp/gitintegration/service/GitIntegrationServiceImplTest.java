/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.service;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.common.Constants;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.processor.impl.GithubConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GitlabConnectorProcessor;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationServiceImplTest {
  private static final String DELEGATE_SELECTOR1 = "ds1";
  private static final String DELEGATE_SELECTOR2 = "ds2";
  private static final String TEST_IDENTIFIER = "123";
  private static final String TEST_GITLAB_URL =
      "https://gitlab.com/sathish1293/sathish/-/blob/main/sathish/Organization/default.yaml";
  private static final String GITHUB_IDENTIFIER = "testGithub";
  private static final String TEST_GITHUB_URL = "https://github.com/harness/harness-core";
  private static final String TEST_GITHUB_URL1 = "https://github.harness.com/harness/harness-core";
  private static final String TEST_GITHUB_HOST = "github.com";
  private static final String TEST_GITHUB1_HOST = "github.harness.com";
  @InjectMocks GitIntegrationServiceImpl gitIntegrationServiceImpl;
  AutoCloseable openMocks;
  @Mock private CatalogConnectorRepository catalogConnectorRepository;
  @Mock ConnectorProcessorFactory connectorProcessorFactory;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock ConfigManagerService configManagerService;
  @Mock DelegateSelectorsCache delegateSelectorsCache;
  @Mock ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  @Captor private ArgumentCaptor<JSONObject> hostProxyMapCaptor;

  String ACCOUNT_IDENTIFIER = "test-secret-identifier";
  String TOKEN_SECRET_IDENTIFIER = "test-secret-identifier";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllConnectorDetails() {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    List<CatalogConnectorEntity> catalogConnectorEntityList = new ArrayList<>();
    catalogConnectorEntityList.add(getGithubConnectorEntity(TEST_GITHUB_HOST, delegateSelectors));
    catalogConnectorEntityList.add(getGitlabConnectorEntity(delegateSelectors));
    when(catalogConnectorRepository.findAllByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(catalogConnectorEntityList);
    List<CatalogConnectorEntity> result = gitIntegrationServiceImpl.getAllConnectorDetails(ACCOUNT_IDENTIFIER);
    assertEquals(catalogConnectorEntityList.size(), result.size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindByAccountIdAndProviderType() {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity(TEST_GITHUB_HOST, delegateSelectors);
    when(catalogConnectorRepository.findByAccountIdentifierAndConnectorProviderType(ACCOUNT_IDENTIFIER, "Github"))
        .thenReturn(Optional.ofNullable(catalogConnectorEntity));
    Optional<CatalogConnectorEntity> result =
        gitIntegrationServiceImpl.findByAccountIdAndProviderType(ACCOUNT_IDENTIFIER, "Github");
    assertTrue(result.isPresent());
    assertEquals(catalogConnectorEntity, result.get());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveConnectorDetails() throws Exception {
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier("account.testGitlab");
    connectorDetails.setType(ConnectorDetails.TypeEnum.GITLAB);
    GitlabConnectorProcessor processor = mock(GitlabConnectorProcessor.class);
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.GITLAB)).thenReturn(processor);
    Map<String, BackstageEnvVariable> secrets = new HashMap<>();
    secrets.put(Constants.GITLAB_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(TOKEN_SECRET_IDENTIFIER, Constants.GITLAB_TOKEN));
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfoDTO(delegateSelectors);
    when(processor.getConnectorInfo(any(), any())).thenReturn(connectorInfoDTO);
    when(processor.getConnectorAndSecretsInfo(any(), any())).thenReturn(secrets);
    doNothing().when(backstageEnvVariableService).findAndSync(any());
    MockedStatic<GitIntegrationUtils> gitIntegrationUtilsMockedStatic = Mockito.mockStatic(GitIntegrationUtils.class);
    MockedStatic<ConfigManagerUtils> configManagerUtilsMockedStatic = Mockito.mockStatic(ConfigManagerUtils.class);
    when(GitIntegrationUtils.getHostForConnector(any())).thenReturn("dummyUrl");
    when(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(any())).thenReturn("Sample Config");
    when(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(any())).thenReturn("Sample Json Schema");
    when(ConfigManagerUtils.isValidSchema(any(), any())).thenReturn(false);
    when(configManagerService.saveConfigForAccount(any(), any(), any())).thenReturn(new AppConfig());
    when(configManagerService.mergeAndSaveAppConfig(any())).thenReturn(MergedAppConfigEntity.builder().build());
    when(processor.getInfraConnectorType(any())).thenReturn("DIRECT");
    CatalogConnectorEntity catalogConnectorEntity = getGitlabConnectorEntity(delegateSelectors);
    when(catalogConnectorRepository.saveOrUpdate(any())).thenReturn(catalogConnectorEntity);
    when(proxyEnvVariableServiceWrapper.getHostProxyMap(ACCOUNT_IDENTIFIER)).thenReturn(new JSONObject());

    CatalogConnectorEntity result =
        gitIntegrationServiceImpl.saveConnectorDetails(ACCOUNT_IDENTIFIER, connectorDetails);

    verify(delegateSelectorsCache).put(eq(ACCOUNT_IDENTIFIER), any(), any());
    assertEquals("testGitlab", result.getConnectorIdentifier());
    assertEquals(delegateSelectors, result.getDelegateSelectors());
    gitIntegrationUtilsMockedStatic.close();
    configManagerUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindDefaultConnectorDetails() {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity(TEST_GITHUB_HOST, delegateSelectors);
    when(catalogConnectorRepository.findLastUpdated(ACCOUNT_IDENTIFIER)).thenReturn(catalogConnectorEntity);
    CatalogConnectorEntity result = gitIntegrationServiceImpl.findDefaultConnectorDetails(ACCOUNT_IDENTIFIER);
    assertEquals(catalogConnectorEntity, result);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessConnectorUpdate() throws Exception {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(GITHUB_IDENTIFIER))
                                          .build();
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder().putAllMetadata(
                              ImmutableMap.of("connectorType", ConnectorType.GITHUB.toString())))
                          .build();
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity(TEST_GITHUB_HOST, delegateSelectors);
    when(
        catalogConnectorRepository.findByAccountIdentifierAndConnectorIdentifier(ACCOUNT_IDENTIFIER, GITHUB_IDENTIFIER))
        .thenReturn(Optional.ofNullable(catalogConnectorEntity));
    when(catalogConnectorRepository.findByAccountIdentifierAndConnectorProviderType(ACCOUNT_IDENTIFIER, "Github"))
        .thenReturn(Optional.ofNullable(catalogConnectorEntity));
    GithubConnectorProcessor processor = mock(GithubConnectorProcessor.class);
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.GITHUB)).thenReturn(processor);
    // update delegate selectors
    when(processor.getConnectorInfo(ACCOUNT_IDENTIFIER, GITHUB_IDENTIFIER))
        .thenReturn(getGithubConnectorInfoDTO(TEST_GITHUB_URL, Collections.singleton(DELEGATE_SELECTOR1)));
    when(processor.getInfraConnectorType(any())).thenReturn("PROXY");
    doNothing().when(delegateSelectorsCache).remove(any(), any());
    JSONObject hostProxyMap = new JSONObject();
    hostProxyMap.put(TEST_GITHUB_HOST, false);
    when(proxyEnvVariableServiceWrapper.getHostProxyMap(ACCOUNT_IDENTIFIER)).thenReturn(hostProxyMap);
    when(catalogConnectorRepository.saveOrUpdate(any())).thenReturn(catalogConnectorEntity);

    gitIntegrationServiceImpl.processConnectorUpdate(message, entityChangeDTO);

    verify(proxyEnvVariableServiceWrapper).setHostProxyMap(eq(ACCOUNT_IDENTIFIER), hostProxyMapCaptor.capture());
    assertEquals(hostProxyMap, hostProxyMapCaptor.getValue());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessConnectorUpdateHostUpdate() throws Exception {
    Set<String> delegateSelectors = new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2));
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(GITHUB_IDENTIFIER))
                                          .build();
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder().putAllMetadata(
                              ImmutableMap.of("connectorType", ConnectorType.GITHUB.toString())))
                          .build();
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity(TEST_GITHUB_HOST, delegateSelectors);
    when(
        catalogConnectorRepository.findByAccountIdentifierAndConnectorIdentifier(ACCOUNT_IDENTIFIER, GITHUB_IDENTIFIER))
        .thenReturn(Optional.ofNullable(catalogConnectorEntity));
    GithubConnectorProcessor processor = mock(GithubConnectorProcessor.class);
    when(connectorProcessorFactory.getConnectorProcessor(ConnectorType.GITHUB)).thenReturn(processor);
    when(processor.getConnectorInfo(ACCOUNT_IDENTIFIER, GITHUB_IDENTIFIER))
        .thenReturn(getGithubConnectorInfoDTO(TEST_GITHUB_URL1, delegateSelectors));
    when(processor.getInfraConnectorType(any())).thenReturn("DIRECT");
    when(catalogConnectorRepository.findByAccountIdentifierAndConnectorProviderType(ACCOUNT_IDENTIFIER, "Github"))
        .thenReturn(Optional.ofNullable(catalogConnectorEntity));
    doNothing().when(delegateSelectorsCache).remove(any(), any());
    JSONObject hostProxyMap = new JSONObject();
    hostProxyMap.put(TEST_GITHUB_HOST, false);
    when(proxyEnvVariableServiceWrapper.getHostProxyMap(ACCOUNT_IDENTIFIER)).thenReturn(hostProxyMap);
    when(catalogConnectorRepository.saveOrUpdate(any())).thenReturn(catalogConnectorEntity);

    gitIntegrationServiceImpl.processConnectorUpdate(message, entityChangeDTO);

    verify(proxyEnvVariableServiceWrapper).setHostProxyMap(eq(ACCOUNT_IDENTIFIER), hostProxyMapCaptor.capture());
    assertEquals(hostProxyMap, hostProxyMapCaptor.getValue());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessConnectorUpdateWithNoMatchingConnector() throws Exception {
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_IDENTIFIER))
                                          .build();
    when(catalogConnectorRepository.findByAccountIdentifierAndConnectorIdentifier(ACCOUNT_IDENTIFIER, TEST_IDENTIFIER))
        .thenReturn(Optional.empty());
    gitIntegrationServiceImpl.processConnectorUpdate(Message.newBuilder().build(), entityChangeDTO);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private CatalogConnectorEntity getGithubConnectorEntity(String host, Set<String> delegateSelectors) {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .connectorIdentifier(GITHUB_IDENTIFIER)
        .host(host)
        .delegateSelectors(delegateSelectors)
        .connectorProviderType(ConnectorType.GITHUB.toString())
        .type(CatalogInfraConnectorType.DIRECT)
        .build();
  }

  private CatalogConnectorEntity getGitlabConnectorEntity(Set<String> delegateSelectors) {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .connectorIdentifier("testGitlab")
        .connectorProviderType(ConnectorType.GITLAB.toString())
        .delegateSelectors(delegateSelectors)
        .type(CatalogInfraConnectorType.DIRECT)
        .build();
  }

  private ConnectorInfoDTO getGithubConnectorInfoDTO(String url, Set<String> delegateSelectors) {
    return ConnectorInfoDTO.builder()
        .identifier(GITHUB_IDENTIFIER)
        .connectorType(ConnectorType.GITHUB)
        .connectorConfig(GithubConnectorDTO.builder().url(url).delegateSelectors(delegateSelectors).build())
        .build();
  }

  private ConnectorInfoDTO getConnectorInfoDTO(Set<String> delegateSelectors) {
    return ConnectorInfoDTO.builder()
        .identifier(TEST_IDENTIFIER)
        .connectorType(ConnectorType.GITLAB)
        .connectorConfig(GithubConnectorDTO.builder().url(TEST_GITLAB_URL).delegateSelectors(delegateSelectors).build())
        .build();
  }
}