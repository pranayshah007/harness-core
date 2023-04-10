/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.connector;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorValidationParameterResponse;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(DX)
public class ConnectorHeartbeatPerpetualTaskClientTest extends WingsBaseTest {
  @Inject private KryoSerializer kryoSerializer;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService ngSecretManagerService;
  @Mock private Call<ResponseDTO<ConnectorValidationParameterResponse>> call;
  @InjectMocks ConnectorHeartbeatPerpetualTaskClient connectorHeartbeatPerpetualTaskClient;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";
  PerpetualTaskClientContext perpetualTaskClientContext;

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    when(connectorResourceClient.getConnectorValidationParams(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(call);
    when(ngSecretManagerService.getSecretManager(anyString(), anyString(), anyString(), anyString(), eq(false)))
        .thenReturn(null);
    ScmValidationParams gitValidationParameters =
        ScmValidationParams.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).build())
            .build();
    ConnectorValidationParameterResponse connectorValidationParameterResponse =
        ConnectorValidationParameterResponse.builder()
            .connectorValidationParams(gitValidationParameters)
            .isInvalid(false)
            .build();
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(connectorValidationParameterResponse)));
    FieldUtils.writeField(connectorHeartbeatPerpetualTaskClient, "kryoSerializer", kryoSerializer, true);
    Map<String, String> connectorDetails = ImmutableMap.of(ACCOUNT_KEY, accountIdentifier, ORG_KEY, orgIdentifier,
        PROJECT_KEY, projectIdentifier, CONNECTOR_IDENTIFIER_KEY, identifier);
    perpetualTaskClientContext = PerpetualTaskClientContext.builder().clientParams(connectorDetails).build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void getTaskParams() {
    ConnectorHeartbeatTaskParams taskParams =
        (ConnectorHeartbeatTaskParams) connectorHeartbeatPerpetualTaskClient.getTaskParams(perpetualTaskClientContext);
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getAccountIdentifier()).isEqualTo(accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void getValidationTask() {
    DelegateTask delegateTask =
        connectorHeartbeatPerpetualTaskClient.getValidationTask(perpetualTaskClientContext, accountIdentifier);
    assertThat(delegateTask).isNotNull();
  }
}
