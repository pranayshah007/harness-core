/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awscodecommitconnector.AwsCodeCommitTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.rule.Owner;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import java.util.Collections;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AwsCodeCommitDelegateTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AwsClient awsClient;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private NGErrorHelper ngErrorHelper;

  @InjectMocks
  private AwsCodeCommitDelegateTask task = new AwsCodeCommitDelegateTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRunWithObjectParams() {
    assertThatThrownBy(() -> task.run(new Object[10]))
        .hasMessage("Not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleValidationTask() {
    AwsCodeCommitConnectorDTO connectorDTO =
        AwsCodeCommitConnectorDTO.builder()
            .url("https://git-codecommit.us-east-1.amazonaws.com/v1/repos/MyDemoRepo")
            .authentication(AwsCodeCommitAuthenticationDTO.builder().build())
            .build();
    AwsCodeCommitTaskParams awsCodeCommitTaskParams =
        AwsCodeCommitTaskParams.builder().awsConnector(connectorDTO).encryptionDetails(Collections.emptyList()).build();
    AwsConfig awsConfig = AwsConfig.builder().build();

    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsCodeCommit(any(), any());

    DelegateResponseData result = task.run(awsCodeCommitTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse response = (AwsValidateTaskResponse) result;
    assertThat(response.getConnectorValidationResult().getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);

    ArgumentCaptor<String> regionCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> repoCaptor = ArgumentCaptor.forClass(String.class);
    verify(awsClient, times(1))
        .validateAwsCodeCommitCredential(eq(awsConfig), regionCaptor.capture(), repoCaptor.capture());
    assertThat(regionCaptor.getValue()).isEqualTo("us-east-1");
    assertThat(repoCaptor.getValue()).isEqualTo("MyDemoRepo");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleValidationTaskRegionFailure() {
    AwsCodeCommitConnectorDTO connectorDTO = AwsCodeCommitConnectorDTO.builder()
                                                 .url("https://git-codecommit")
                                                 .authentication(AwsCodeCommitAuthenticationDTO.builder().build())
                                                 .build();
    AwsCodeCommitTaskParams awsCodeCommitTaskParams =
        AwsCodeCommitTaskParams.builder().awsConnector(connectorDTO).encryptionDetails(Collections.emptyList()).build();

    DelegateResponseData result = task.run(awsCodeCommitTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse response = (AwsValidateTaskResponse) result;
    assertThat(response.getConnectorValidationResult().getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleValidationTaskRepoFailure() {
    AwsCodeCommitConnectorDTO connectorDTO = AwsCodeCommitConnectorDTO.builder()
                                                 .url("https://git-codecommit.us-east-1.amazonaws.com/v1")
                                                 .authentication(AwsCodeCommitAuthenticationDTO.builder().build())
                                                 .build();
    AwsCodeCommitTaskParams awsCodeCommitTaskParams =
        AwsCodeCommitTaskParams.builder().awsConnector(connectorDTO).encryptionDetails(Collections.emptyList()).build();

    DelegateResponseData result = task.run(awsCodeCommitTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse response = (AwsValidateTaskResponse) result;
    assertThat(response.getConnectorValidationResult().getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleAmazonServiceException() {
    AwsCodeCommitConnectorDTO connectorDTO =
        AwsCodeCommitConnectorDTO.builder()
            .url("https://git-codecommit.us-east-1.amazonaws.com/v1/repos/MyDemoRepo")
            .authentication(AwsCodeCommitAuthenticationDTO.builder().build())
            .build();
    AwsCodeCommitTaskParams awsCodeCommitTaskParams =
        AwsCodeCommitTaskParams.builder().awsConnector(connectorDTO).encryptionDetails(Collections.emptyList()).build();
    AwsConfig awsConfig = AwsConfig.builder().build();

    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsCodeCommit(any(), any());

    doThrow(new AmazonServiceException("Bad service"))
        .when(awsClient)
        .validateAwsCodeCommitCredential(any(), any(), any());
    DelegateResponseData result = task.run(awsCodeCommitTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse response = (AwsValidateTaskResponse) result;
    assertThat(response.getConnectorValidationResult().getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleAmazonClientException() {
    AwsCodeCommitConnectorDTO connectorDTO =
        AwsCodeCommitConnectorDTO.builder()
            .url("https://git-codecommit.us-east-1.amazonaws.com/v1/repos/MyDemoRepo")
            .authentication(AwsCodeCommitAuthenticationDTO.builder().build())
            .build();
    AwsCodeCommitTaskParams awsCodeCommitTaskParams =
        AwsCodeCommitTaskParams.builder().awsConnector(connectorDTO).encryptionDetails(Collections.emptyList()).build();
    AwsConfig awsConfig = AwsConfig.builder().build();

    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsCodeCommit(any(), any());

    doThrow(new AmazonClientException("Bad client"))
        .when(awsClient)
        .validateAwsCodeCommitCredential(any(), any(), any());
    DelegateResponseData result = task.run(awsCodeCommitTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse response = (AwsValidateTaskResponse) result;
    assertThat(response.getConnectorValidationResult().getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldHandleOtherException() {
    AwsCodeCommitConnectorDTO connectorDTO =
        AwsCodeCommitConnectorDTO.builder()
            .url("https://git-codecommit.us-east-1.amazonaws.com/v1/repos/MyDemoRepo")
            .authentication(AwsCodeCommitAuthenticationDTO.builder().build())
            .build();
    AwsCodeCommitTaskParams awsCodeCommitTaskParams =
        AwsCodeCommitTaskParams.builder().awsConnector(connectorDTO).encryptionDetails(Collections.emptyList()).build();
    AwsConfig awsConfig = AwsConfig.builder().build();

    doReturn(awsConfig).when(awsNgConfigMapper).mapAwsCodeCommit(any(), any());

    doThrow(new NullPointerException("Timeout")).when(awsClient).validateAwsCodeCommitCredential(any(), any(), any());
    DelegateResponseData result = task.run(awsCodeCommitTaskParams);
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AwsValidateTaskResponse.class);
    AwsValidateTaskResponse response = (AwsValidateTaskResponse) result;
    assertThat(response.getConnectorValidationResult().getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }
}
