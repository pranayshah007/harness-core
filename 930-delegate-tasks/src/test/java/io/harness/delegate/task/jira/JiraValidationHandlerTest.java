/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraValidationParams;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JiraValidationHandlerTest extends CategoryTest {
  @Mock JiraTaskNGHelper jiraTaskNGHelper;
  @Mock private NGErrorHelper ngErrorHelper;
  @InjectMocks JiraValidationHandler jiraValidationHandler;
  private final String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testValidateSuccess() {
    String secretKeyRefIdentifier = "secretKeyRefIdentifier";
    String secretKey = "secretKey";
    SecretRefData passwordSecretRef = SecretRefData.builder()
                                          .identifier(secretKeyRefIdentifier)
                                          .scope(Scope.ACCOUNT)
                                          .decryptedValue(secretKey.toCharArray())
                                          .build();

    JiraConnectorDTO awsConnectorDTO =
        JiraConnectorDTO.builder().username(secretKey).passwordRef(passwordSecretRef).build();

    ConnectorValidationParams connectorValidationParams = JiraValidationParams.builder()
                                                              .jiraConnectorDTO(awsConnectorDTO)
                                                              .connectorName("Test-Jira")
                                                              .encryptedDataDetails(null)
                                                              .build();

    ConnectorValidationResult result = jiraValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testValidateFailure() {
    doThrow(new RuntimeException("Invalid Credentials")).when(jiraTaskNGHelper).getJiraTaskResponse(any());
    doAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class))
        .when(ngErrorHelper)
        .getErrorSummary(any());
    String secretKeyRefIdentifier = "secretKeyRefIdentifier";
    String secretKey = "secretKey";
    SecretRefData passwordSecretRef = SecretRefData.builder()
                                          .identifier(secretKeyRefIdentifier)
                                          .scope(Scope.ACCOUNT)
                                          .decryptedValue(secretKey.toCharArray())
                                          .build();

    JiraConnectorDTO awsConnectorDTO =
        JiraConnectorDTO.builder().username(secretKey).passwordRef(passwordSecretRef).build();

    ConnectorValidationParams connectorValidationParams = JiraValidationParams.builder()
                                                              .jiraConnectorDTO(awsConnectorDTO)
                                                              .connectorName("Test-Jira")
                                                              .encryptedDataDetails(null)
                                                              .build();

    ConnectorValidationResult result = jiraValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrorSummary()).isEqualTo("Invalid Credentials");
  }
}
