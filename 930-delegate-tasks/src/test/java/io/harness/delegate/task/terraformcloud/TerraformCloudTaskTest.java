/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudTaskTest {
  private static final String token = "t-o-k-e-n";
  private static final String url = "https://some.io";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerraformCloudConfigMapper terraformCloudConfigMapper;
  @Mock private TerraformCloudValidationHandler terraformCloudValidationHandler;

  @InjectMocks
  private TerraformCloudTaskNG task = new TerraformCloudTaskNG(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().build()).build(), null, null,
      null);

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateTaskTypeSuccessfully() throws IOException {
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.VALIDATE);

    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();

    doReturn(connectorValidationResult).when(terraformCloudValidationHandler).validate(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudValidateTaskResponse.class);

    TerraformCloudValidateTaskResponse terraformCloudValidateTaskResponse =
        (TerraformCloudValidateTaskResponse) delegateResponseData;
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getDelegateId())
        .isEqualTo("delegateId");
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateTaskTypeFailed() {
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.VALIDATE);

    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.FAILURE)
                                                              .errorSummary("Some error")
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();

    doReturn(connectorValidationResult).when(terraformCloudValidationHandler).validate(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudValidateTaskResponse.class);

    TerraformCloudValidateTaskResponse terraformCloudValidateTaskResponse =
        (TerraformCloudValidateTaskResponse) delegateResponseData;
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getDelegateId())
        .isEqualTo("delegateId");
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getErrorSummary())
        .isEqualTo("Some error");
  }

  private TaskParameters getTerraformCloudTaskParams(TerraformCloudTaskType taskType) {
    return TerraformCloudTaskParams.builder()
        .terraformCloudTaskType(taskType)
        .encryptionDetails(null)
        .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
        .params(ImmutableMap.of("ENV", "Dev"))
        .build();
  }

  private TerraformCloudConnectorDTO getTerraformCloudConnectorDTO() {
    return TerraformCloudConnectorDTO.builder()
        .terraformCloudUrl(url)
        .delegateSelectors(null)
        .credential(TerraformCloudCredentialDTO.builder()
                        .type(TerraformCloudCredentialType.API_TOKEN)
                        .spec(TerraformCloudTokenCredentialsDTO.builder()
                                  .apiToken(SecretRefData.builder().decryptedValue(token.toCharArray()).build())
                                  .build())
                        .build())
        .build();
  }
}
