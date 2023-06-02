/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.connector.ConnectorCategory.SECRET_MANAGER;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.helper.HarnessManagedConnectorHelper;
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorActivityService;
import io.harness.connector.services.ConnectorHeartbeatService;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraPATDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.opa.entities.connector.OpaConnectorService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.helpers.ConnectorInstrumentationHelper;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class ConnectorServiceImplTest extends CategoryTest {
  private ConnectorServiceImpl connectorService;
  @Mock private ConnectorService defaultConnectorService;
  @Mock private ConnectorService secretManagerConnectorService;
  @Mock private ConnectorActivityService connectorActivityService;
  @Mock private ConnectorHeartbeatService connectorHeartbeatService;
  @Mock private ConnectorRepository connectorRepository;
  @Mock private Producer eventProducer;
  @Mock private ExecutorService executorService;
  @Mock private ConnectorErrorMessagesHelper connectorErrorMessagesHelper;
  @Mock private HarnessManagedConnectorHelper harnessManagedConnectorHelper;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private ConnectorInstrumentationHelper instrumentationHelper;
  @Mock private OpaConnectorService opaConnectorService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    connectorService = spy(new ConnectorServiceImpl(defaultConnectorService, secretManagerConnectorService,
        connectorActivityService, connectorHeartbeatService, connectorRepository, eventProducer, executorService,
        connectorErrorMessagesHelper, harnessManagedConnectorHelper, ngErrorHelper, gitSyncSdkService,
        instrumentationHelper, opaConnectorService, ngFeatureFlagHelperService));
  }

  private ConnectorDTO getRequestDTO_vaultAppRole() {
    SecretRefData secretRefData = new SecretRefData(randomAlphabetic(10));
    secretRefData.setDecryptedValue(randomAlphabetic(5).toCharArray());
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().build();
    connectorInfo.setConnectorType(ConnectorType.VAULT);
    connectorInfo.setConnectorConfig(VaultConnectorDTO.builder()
                                         .vaultUrl("https://vaultqa.harness.io")
                                         .secretEngineVersion(1)
                                         .appRoleId(randomAlphabetic(10))
                                         .secretId(secretRefData)
                                         .renewalIntervalMinutes(10)
                                         .build());
    connectorInfo.setName("name");
    connectorInfo.setIdentifier("identifier");
    connectorInfo.setOrgIdentifier("orgIdentifier");
    connectorInfo.setProjectIdentifier("projectIdentifier");
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void createConnector_appRoleWithFFEnabled() {
    ConnectorDTO connectorDTO = getRequestDTO_vaultAppRole();
    String accountIdentifier = randomAlphabetic(10);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    when(harnessManagedConnectorHelper.isHarnessManagedSecretManager(any())).thenReturn(true);
    when(gitSyncSdkService.isDefaultBranch(any(), any(), any())).thenReturn(false);
    ArgumentCaptor<ConnectorDTO> argumentCaptor = ArgumentCaptor.forClass(ConnectorDTO.class);
    when(secretManagerConnectorService.create(argumentCaptor.capture(), any(), any()))
        .thenReturn(ConnectorResponseDTO.builder().build());
    when(instrumentationHelper.sendConnectorCreateEvent(any(), any())).thenReturn(null);

    connectorService.create(connectorDTO, accountIdentifier, ChangeType.ADD);
    VaultConnectorDTO vaultConnectorDTO =
        (VaultConnectorDTO) argumentCaptor.getValue().getConnectorInfo().getConnectorConfig();
    assertThat(vaultConnectorDTO.isRenewAppRoleToken()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void createConnector_appRoleWithFFDisabled() {
    ConnectorDTO connectorDTO = getRequestDTO_vaultAppRole();
    String accountIdentifier = randomAlphabetic(10);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    when(harnessManagedConnectorHelper.isHarnessManagedSecretManager(any())).thenReturn(true);
    when(gitSyncSdkService.isDefaultBranch(any(), any(), any())).thenReturn(false);
    ArgumentCaptor<ConnectorDTO> argumentCaptor = ArgumentCaptor.forClass(ConnectorDTO.class);
    when(secretManagerConnectorService.create(argumentCaptor.capture(), any(), any()))
        .thenReturn(ConnectorResponseDTO.builder().build());
    when(instrumentationHelper.sendConnectorCreateEvent(any(), any())).thenReturn(null);

    connectorService.create(connectorDTO, accountIdentifier, ChangeType.ADD);
    VaultConnectorDTO vaultConnectorDTO =
        (VaultConnectorDTO) argumentCaptor.getValue().getConnectorInfo().getConnectorConfig();
    assertThat(vaultConnectorDTO.isRenewAppRoleToken()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void createUpdateJiraConnector_PatAuthWithFFDisabled() {
    ConnectorDTO connectorDTO = getJiraConnectorPatDTO();
    String accountIdentifier = randomAlphabetic(10);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> connectorService.create(connectorDTO, accountIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported jira auth type provided : PAT");
    assertThatThrownBy(() -> connectorService.update(connectorDTO, accountIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported jira auth type provided : PAT");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void deleteSecretManagerWhenNoOtherSMPresent() {
    String accountIdentifier = randomAlphabetic(10);
    String connectorIdentifier = randomAlphabetic(10);
    List<ConnectorResponseDTO> connectorsList = new ArrayList();
    connectorsList.add(ConnectorResponseDTO.builder().build());
    Page<ConnectorResponseDTO> connectorsPage = new PageImpl(connectorsList);
    Connector secretManager = VaultConnector.builder().build();
    secretManager.setType(ConnectorType.VAULT);
    String fullyQualifiedIdentifier =
        FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier, null, null, connectorIdentifier);
    int page = 0;
    int size = 2;
    when(
        defaultConnectorService.list(page, size, accountIdentifier, null, null, null, null, SECRET_MANAGER, null, null))
        .thenReturn(connectorsPage);
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
             fullyQualifiedIdentifier, null, null, accountIdentifier, true))
        .thenReturn(Optional.of(secretManager));
    try {
      connectorService.delete(accountIdentifier, null, null, connectorIdentifier, false);
      fail("Should fail with InvalidRequestException as no other secret manager is present in the account");
    } catch (InvalidRequestException ex) {
      assertEquals(ex.getMessage(),
          String.format("Cannot delete the connector: %s as no other secret manager is present in the account.",
              connectorIdentifier));
    }
  }

  private ConnectorDTO getJiraConnectorPatDTO() {
    SecretRefData secretRefData = new SecretRefData(randomAlphabetic(10));
    secretRefData.setDecryptedValue(randomAlphabetic(5).toCharArray());
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().build();
    connectorInfo.setConnectorType(ConnectorType.JIRA);
    connectorInfo.setConnectorConfig(JiraConnectorDTO.builder()
                                         .auth(JiraAuthenticationDTO.builder()
                                                   .authType(JiraAuthType.PAT)
                                                   .credentials(JiraPATDTO.builder().patRef(secretRefData).build())
                                                   .build())
                                         .jiraUrl("https://test.atlassian.com")
                                         .build());
    connectorInfo.setName("name");
    connectorInfo.setIdentifier("identifier");
    connectorInfo.setOrgIdentifier("orgIdentifier");
    connectorInfo.setProjectIdentifier("projectIdentifier");
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }
}
