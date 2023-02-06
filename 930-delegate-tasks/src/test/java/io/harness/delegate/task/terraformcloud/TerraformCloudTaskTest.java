/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

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
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudOrganizationsTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudWorkspacesTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
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
  @Mock private TerraformCloudTaskHelper terraformCloudTaskHelper;

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
  public void testValidateTaskTypeFailed() throws IOException {
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.VALIDATE);
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.FAILURE)
                                                              .errorSummary("Some error")
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();
    doReturn(connectorValidationResult).when(terraformCloudValidationHandler).validate(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    verify(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(
            taskParameters.getTerraformCloudConnectorDTO(), taskParameters.getEncryptionDetails());
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

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOrganizations() throws IOException {
    Map<String, String> organizationsMap = Collections.singletonMap("id1", "org1");
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.GET_ORGANIZATIONS);
    doReturn(organizationsMap).when(terraformCloudTaskHelper).getOrganizationsMap(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    verify(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(
            taskParameters.getTerraformCloudConnectorDTO(), taskParameters.getEncryptionDetails());
    verify(terraformCloudTaskHelper).getOrganizationsMap(any());
    assertThat(delegateResponseData).isInstanceOf(TerraformCloudOrganizationsTaskResponse.class);
    TerraformCloudOrganizationsTaskResponse terraformCloudOrganizationsTaskResponse =
        (TerraformCloudOrganizationsTaskResponse) delegateResponseData;
    assertThat(terraformCloudOrganizationsTaskResponse.getOrganizations()).isEqualTo(organizationsMap);
    assertThat(terraformCloudOrganizationsTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetWorkspaces() throws IOException {
    Map<String, String> workspacesMap = Collections.singletonMap("id1", "ws1");
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.GET_WORKSPACES);
    doReturn(workspacesMap).when(terraformCloudTaskHelper).getWorkspacesMap(any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    verify(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(
            taskParameters.getTerraformCloudConnectorDTO(), taskParameters.getEncryptionDetails());
    verify(terraformCloudTaskHelper).getWorkspacesMap(any(), any());
    assertThat(delegateResponseData).isInstanceOf(TerraformCloudWorkspacesTaskResponse.class);
    TerraformCloudWorkspacesTaskResponse terraformCloudWorkspacesTaskResponse =
        (TerraformCloudWorkspacesTaskResponse) delegateResponseData;
    assertThat(terraformCloudWorkspacesTaskResponse.getWorkspaces()).isEqualTo(workspacesMap);
    assertThat(terraformCloudWorkspacesTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  private TerraformCloudTaskParams getTerraformCloudTaskParams(TerraformCloudTaskType taskType) {
    return TerraformCloudTaskParams.builder()
        .terraformCloudTaskType(taskType)
        .encryptionDetails(null)
        .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
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
