/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.SUCCESS;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.USER_PASSWORD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.DockerConnectorValidationParamsProvider;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.docker.DockerArtifactTaskHelper;
import io.harness.connector.task.docker.DockerValidationHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerTestConnectionTaskResponse;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class DockerConnectionValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private ConnectorService connectorService;

  @InjectMocks private NGErrorHelper ngErrorHelper;
  @InjectMocks private DockerConnectionValidator dockerConnectionValidator;

  @Mock private DockerArtifactTaskHelper dockerArtifactTaskHelper;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void validateTest() {
    String dockerRegistryUrl = "url";
    String dockerUserName = "dockerUserName";
    String passwordRefIdentifier = "passwordRefIdentifier";
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier(passwordRefIdentifier).scope(Scope.ACCOUNT).build();

    DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
        DockerUserNamePasswordDTO.builder().username(dockerUserName).passwordRef(passwordSecretRef).build();

    DockerAuthenticationDTO dockerAuthenticationDTO =
        DockerAuthenticationDTO.builder().authType(USER_PASSWORD).credentials(dockerUserNamePasswordDTO).build();
    DockerConnectorDTO dockerConnectorDTO =
        DockerConnectorDTO.builder().dockerRegistryUrl(dockerRegistryUrl).auth(dockerAuthenticationDTO).build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of("xxxxxx",
            DockerTestConnectionTaskResponse.builder()
                .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
                .build()));
    dockerConnectionValidator.validate(
        dockerConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2ReturnTaskId(any());
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateTestViaManager() {
    String dockerRegistryUrl = "url";
    String dockerUserName = "dockerUserName";
    String passwordRefIdentifier = "passwordRefIdentifier";
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier(passwordRefIdentifier).scope(Scope.ACCOUNT).build();

    DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
        DockerUserNamePasswordDTO.builder().username(dockerUserName).passwordRef(passwordSecretRef).build();

    DockerAuthenticationDTO dockerAuthenticationDTO =
        DockerAuthenticationDTO.builder().authType(USER_PASSWORD).credentials(dockerUserNamePasswordDTO).build();
    DockerConnectorDTO dockerConnectorDTO = DockerConnectorDTO.builder()
                                                .dockerRegistryUrl(dockerRegistryUrl)
                                                .auth(dockerAuthenticationDTO)
                                                .executeOnDelegate(false)
                                                .build();
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.DOCKER)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));

    DockerValidationHandler dockerValidationHandler = mock(DockerValidationHandler.class);
    on(dockerValidationHandler).set("dockerArtifactTaskHelper", dockerArtifactTaskHelper);
    when(dockerValidationHandler.validate(any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("DockerRegistry")))
        .thenReturn(dockerValidationHandler);

    ArtifactTaskExecutionResponse taskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().isArtifactServerValid(true).build();
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .artifactTaskExecutionResponse(taskExecutionResponse)
                                                    .build();
    when(dockerArtifactTaskHelper.getArtifactCollectResponse(any(ArtifactTaskParameters.class)))
        .thenReturn(artifactTaskResponse);

    DockerConnectorValidationParamsProvider dockerConnectorValidationParamsProvider =
        new DockerConnectorValidationParamsProvider();
    on(dockerConnectorValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("DockerRegistry")))
        .thenReturn(dockerConnectorValidationParamsProvider);

    ConnectorValidationResult validationResult = dockerConnectionValidator.validate(
        dockerConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(validationResult.getStatus()).isEqualTo(SUCCESS);
  }
}
