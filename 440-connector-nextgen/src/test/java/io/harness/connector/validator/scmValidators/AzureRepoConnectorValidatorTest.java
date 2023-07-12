/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.ScmConnectorValidationParamsProvider;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.git.GitCommandTaskHandler;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.GitValidationHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class AzureRepoConnectorValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ConnectorService connectorService;
  @Mock GitDecryptionHelper gitDecryptionHelper;
  @Mock GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private GitCommandTaskHandler gitCommandTaskHandler;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;
  @InjectMocks private AzureRepoConnectorValidator azureRepoConnectorValidator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.AZURE_REPO)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateTestViaManager() {
    AzureRepoConnectorDTO azureRepoConnectorDTO = getConnector(false);

    GitValidationHandler gitValidationHandler = mock(GitValidationHandler.class);
    on(gitValidationHandler).set("gitCommandTaskHandler", gitCommandTaskHandler);
    on(gitValidationHandler).set("gitDecryptionHelper", gitDecryptionHelper);
    when(gitValidationHandler.validate(any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(eq("AzureRepo"))).thenReturn(gitValidationHandler);

    ConnectorValidationResult connectorValidationResult =
        ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();

    when(gitCommandTaskHandler.validateGitCredentials(any(), any(), any(), any()))
        .thenReturn(connectorValidationResult);

    ScmConnectorValidationParamsProvider scmConnectorValidationParamsProvider =
        new ScmConnectorValidationParamsProvider();
    on(scmConnectorValidationParamsProvider)
        .set("gitConfigAuthenticationInfoHelper", gitConfigAuthenticationInfoHelper);
    when(connectorValidationParamsProviderMap.get(eq("AzureRepo"))).thenReturn(scmConnectorValidationParamsProvider);

    ConnectorValidationResult validationResult = azureRepoConnectorValidator.validate(
        azureRepoConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateTestViaDelegate() {
    AzureRepoConnectorDTO azureRepoConnectorDTO = getConnector(true);
    final String taskId = "xxxxxx";

    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of(taskId,
            GitCommandExecutionResponse.builder()
                .connectorValidationResult(
                    ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                .build()));

    ConnectorValidationResult validationResult = azureRepoConnectorValidator.validate(
        azureRepoConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(validationResult.getTaskId()).isEqualTo(taskId);
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  private static AzureRepoConnectorDTO getConnector(boolean executeOnDelegate) {
    String gitUrl = "url";
    String userName = "userName";
    String tokenRefIdentifier = "passwordRefIdentifier";
    SecretRefData tokenSecretRef = SecretRefData.builder().identifier(tokenRefIdentifier).scope(Scope.ACCOUNT).build();

    AzureRepoHttpCredentialsSpecDTO azureRepoHttpCredentialsSpecDTO =
        AzureRepoUsernameTokenDTO.builder().username(userName).tokenRef(tokenSecretRef).build();

    AzureRepoAuthenticationDTO azureRepoAuthenticationDTO =
        AzureRepoAuthenticationDTO.builder()
            .authType(GitAuthType.HTTP)
            .credentials(
                AzureRepoHttpCredentialsDTO.builder().httpCredentialsSpec(azureRepoHttpCredentialsSpecDTO).build())
            .build();

    AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                      .url(gitUrl)
                                                      .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                      .authentication(azureRepoAuthenticationDTO)
                                                      .executeOnDelegate(executeOnDelegate)
                                                      .build();
    return azureRepoConnectorDTO;
  }
}
