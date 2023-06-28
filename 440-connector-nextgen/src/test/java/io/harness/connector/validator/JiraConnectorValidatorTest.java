/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.FAILURE;
import static io.harness.connector.ConnectivityStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectionTaskParams;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.delegate.beans.connector.jira.connection.JiraTestConnectionTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JiraConnectorValidatorTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String JIRA_URL = "https://jira.dev.harness.io";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String USERNAME = "username";

  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @InjectMocks JiraConnectorValidator connectorValidator;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ALEXEI)
  @Category(UnitTests.class)
  public void shouldValidate() {
    JiraConnectorDTO jiraConnectorDTO = JiraConnectorDTO.builder()
                                            .username(USERNAME)
                                            .jiraUrl(JIRA_URL)
                                            .passwordRef(SecretRefData.builder().build())
                                            .build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of("xxxxxx", JiraTestConnectionTaskNGResponse.builder().canConnect(true).build()));

    ConnectorValidationResult result = connectorValidator.validate(
        jiraConnectorDTO, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);

    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2ReturnTaskId(any());
  }

  @Test
  @Owner(developers = OwnerRule.ALEXEI)
  @Category(UnitTests.class)
  public void shouldValidateFailed() {
    JiraConnectorDTO jiraConnectorDTO = JiraConnectorDTO.builder()
                                            .username(USERNAME)
                                            .jiraUrl(JIRA_URL)
                                            .passwordRef(SecretRefData.builder().build())
                                            .build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of("xxxxxx", JiraTestConnectionTaskNGResponse.builder().canConnect(false).build()));

    ConnectorValidationResult result = connectorValidator.validate(
        jiraConnectorDTO, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);

    assertThat(result.getStatus()).isEqualTo(FAILURE);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2ReturnTaskId(any());
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testGetTaskParametersWithoutAuthDTO() {
    JiraConnectorDTO jiraConnectorDTO = JiraConnectorDTO.builder()
                                            .username(USERNAME)
                                            .jiraUrl(JIRA_URL)
                                            .passwordRef(SecretRefData.builder().build())
                                            .build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);

    JiraConnectionTaskParams params = (JiraConnectionTaskParams) connectorValidator.getTaskParameters(
        jiraConnectorDTO, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(params.getJiraConnectorDTO()).isEqualTo(jiraConnectorDTO);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    verify(encryptionHelper).getEncryptionDetail(requestArgumentCaptorForSecretService.capture(), any(), any(), any());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof JiraConnectorDTO).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testGetTaskParametersWithAuthDTO() {
    JiraConnectorDTO jiraConnectorDTO = JiraConnectorDTO.builder()
                                            .username(USERNAME)
                                            .jiraUrl(JIRA_URL)
                                            .passwordRef(SecretRefData.builder().build())
                                            .auth(JiraAuthenticationDTO.builder()
                                                      .authType(JiraAuthType.USER_PASSWORD)
                                                      .credentials(JiraUserNamePasswordDTO.builder()
                                                                       .username(USERNAME)
                                                                       .passwordRef(SecretRefData.builder().build())
                                                                       .build())
                                                      .build())
                                            .build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);

    JiraConnectionTaskParams params = (JiraConnectionTaskParams) connectorValidator.getTaskParameters(
        jiraConnectorDTO, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(params.getJiraConnectorDTO()).isEqualTo(jiraConnectorDTO);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    verify(encryptionHelper).getEncryptionDetail(requestArgumentCaptorForSecretService.capture(), any(), any(), any());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof JiraAuthCredentialsDTO).isTrue();
  }
}
