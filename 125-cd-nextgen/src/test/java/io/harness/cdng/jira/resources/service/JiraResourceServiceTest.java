/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jira.resources.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.delegate.task.jira.JiraSearchUserData;
import io.harness.delegate.task.jira.JiraSearchUserParams;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDC)
public class JiraResourceServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String IDENTIFIER = "identifier";

  private static final IdentifierRef identifierRef = IdentifierRef.builder()
                                                         .accountIdentifier(ACCOUNT_ID)
                                                         .identifier(IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .build();

  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock CDFeatureFlagHelper cdFeatureFlagHelper;

  @Spy @InjectMocks JiraResourceServiceImpl jiraResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(false)));
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(EncryptedDataDetail.builder().build()));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testValidateCredentials() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(JiraTaskNGResponse.builder().build());
    assertThat(jiraResourceService.validateCredentials(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER)).isTrue();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testValidateCredentialsDelegateError() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ErrorNotifyResponseData.builder().errorMessage("exception").build());
    assertThatThrownBy(() -> jiraResourceService.validateCredentials(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(HarnessJiraException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetProjects() {
    List<JiraProjectBasicNG> projects = Collections.emptyList();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(JiraTaskNGResponse.builder().projects(projects).build());
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    assertThat(jiraResourceService.getProjects(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER)).isEqualTo(projects);
    verify(secretManagerClientService).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof JiraConnectorDTO).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetProjectsWithUpdatedConnectorFlow() {
    List<JiraProjectBasicNG> projects = Collections.emptyList();
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(true)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(JiraTaskNGResponse.builder().projects(projects).build());
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    assertThat(jiraResourceService.getProjects(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER)).isEqualTo(projects);
    verify(secretManagerClientService).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof JiraAuthCredentialsDTO).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetProjectsWhenDelegatesNotAvailable() {
    List<JiraProjectBasicNG> projects = Collections.emptyList();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new DelegateServiceDriverException("delegates not available"));
    assertThatThrownBy(() -> jiraResourceService.getProjects(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(HintException.class)
        .hasMessage(
            String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK))
        .hasCause(new DelegateNotAvailableException(
            "Delegates are not available for performing jira operation.", WingsException.USER));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetIssueCreateMeta() {
    JiraIssueCreateMetadataNG createMetadata = new JiraIssueCreateMetadataNG();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(JiraTaskNGResponse.builder().issueCreateMetadata(createMetadata).build());
    assertThat(jiraResourceService.getIssueCreateMetadata(
                   identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, null, null, false, false))
        .isEqualTo(createMetadata);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSearchUser() {
    when(cdFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA)).thenReturn(true);
    String connectorId = "connectorId";
    long defaultSyncTimeout = 10;
    String offset = "0";
    JiraSearchUserParams jiraSearchUserParams =
        JiraSearchUserParams.builder().accountId(ACCOUNT_ID).userQuery("search").startAt(offset).build();
    ArgumentCaptor<JiraTaskNGParametersBuilder> captor = ArgumentCaptor.forClass(JiraTaskNGParametersBuilder.class);

    JiraSearchUserData jiraSearchUserData = JiraSearchUserData.builder().build();
    JiraTaskNGResponse jiraTaskNGResponse = JiraTaskNGResponse.builder().jiraSearchUserData(jiraSearchUserData).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(jiraTaskNGResponse);
    jiraResourceService.searchUser(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, connectorId, defaultSyncTimeout, "search", offset);

    verify(jiraResourceService, times(1)).obtainJiraTaskNGResponse(any(), any(), any(), captor.capture());
    assertThat(captor.getValue().build().getJiraSearchUserParams()).isEqualTo(jiraSearchUserParams);
  }

  private ConnectorResponseDTO getConnector(boolean updatedYaml) {
    if (updatedYaml) {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder()
              .connectorType(ConnectorType.JIRA)
              .connectorConfig(JiraConnectorDTO.builder()
                                   .jiraUrl("url")
                                   .username("username")
                                   .passwordRef(SecretRefData.builder().build())
                                   .auth(JiraAuthenticationDTO.builder()
                                             .authType(JiraAuthType.USER_PASSWORD)
                                             .credentials(JiraUserNamePasswordDTO.builder()
                                                              .username("username")
                                                              .passwordRef(SecretRefData.builder().build())
                                                              .build())
                                             .build())
                                   .build())
              .build();
      return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
    }
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.JIRA)
                                            .connectorConfig(JiraConnectorDTO.builder()
                                                                 .jiraUrl("url")
                                                                 .username("username")
                                                                 .passwordRef(SecretRefData.builder().build())
                                                                 .build())
                                            .build();
    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }
}
