/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.delegate.task.terraformcloud.TerraformCloudCleanupTaskNG.DISCARD_MESSAGE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.terraformcloud.model.RunStatus.DISCARDED;
import static io.harness.terraformcloud.model.RunStatus.PENDING;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskParams;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskResponse;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRefreshTaskParams;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.terraformcloud.TerraformCloudApiException;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.Attributes;
import io.harness.terraformcloud.model.RunData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudCleanupTaskNGTest {
  private static final String token = "t-o-k-e-n";
  private static final String url = "https://some.io";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerraformCloudConfigMapper terraformCloudConfigMapper;
  @Mock private TerraformCloudTaskHelper terraformCloudTaskHelper;

  @InjectMocks
  private TerraformCloudCleanupTaskNG task = new TerraformCloudCleanupTaskNG(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().build()).build(), null, null,
      null);

  private TerraformCloudConfig terraformCloudConfig;

  @Before
  public void setUp() {
    terraformCloudConfig =
        TerraformCloudConfig.builder()
            .terraformCloudCredentials(TerraformCloudApiTokenCredentials.builder().token(token).url(url).build())
            .build();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunSuccessfully() throws IOException {
    RunData initialRunData = new RunData();
    initialRunData.setAttributes(
        Attributes.builder().actions(Attributes.Actions.builder().isDiscardable(true).build()).build());
    RunData runData = new RunData();
    runData.setAttributes(Attributes.builder().status(DISCARDED).build());
    String runId = "runId";
    TerraformCloudConnectorDTO terraformCloudConnectorDTO = TerraformCloudConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    TerraformCloudConfig terraformCloudConfig =
        TerraformCloudConfig.builder()
            .terraformCloudCredentials(TerraformCloudApiTokenCredentials.builder().token(token).url(url).build())
            .build();
    TerraformCloudCleanupTaskParams taskParameters = TerraformCloudCleanupTaskParams.builder()
                                                         .terraformCloudConnectorDTO(terraformCloudConnectorDTO)
                                                         .encryptionDetails(encryptionDetails)
                                                         .runId(runId)
                                                         .build();
    doReturn(terraformCloudConfig)
        .when(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(eq(terraformCloudConnectorDTO), eq(encryptionDetails));
    when(terraformCloudTaskHelper.getRun(any(), any(), any())).thenReturn(initialRunData).thenReturn(runData);

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    TerraformCloudCleanupTaskResponse response = (TerraformCloudCleanupTaskResponse) delegateResponseData;
    verify(terraformCloudTaskHelper, times(2)).getRun(eq(url), eq(token), eq(runId));
    verify(terraformCloudTaskHelper).discardRun(eq(url), eq(token), eq(runId), eq(DISCARD_MESSAGE));
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getRunId()).isEqualTo(runId);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNotDiscardable() throws IOException {
    RunData initialRunData = new RunData();
    initialRunData.setAttributes(Attributes.builder()
                                     .status(PENDING)
                                     .actions(Attributes.Actions.builder().isDiscardable(false).build())
                                     .build());

    String runId = "runId";
    TerraformCloudConnectorDTO terraformCloudConnectorDTO = TerraformCloudConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    TerraformCloudConfig terraformCloudConfig =
        TerraformCloudConfig.builder()
            .terraformCloudCredentials(TerraformCloudApiTokenCredentials.builder().token(token).url(url).build())
            .build();
    TerraformCloudCleanupTaskParams taskParameters = TerraformCloudCleanupTaskParams.builder()
                                                         .terraformCloudConnectorDTO(terraformCloudConnectorDTO)
                                                         .encryptionDetails(encryptionDetails)
                                                         .runId(runId)
                                                         .build();
    doReturn(terraformCloudConfig)
        .when(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(eq(terraformCloudConnectorDTO), eq(encryptionDetails));
    when(terraformCloudTaskHelper.getRun(any(), any(), any())).thenReturn(initialRunData);

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    TerraformCloudCleanupTaskResponse response = (TerraformCloudCleanupTaskResponse) delegateResponseData;
    verify(terraformCloudTaskHelper, times(1)).getRun(eq(url), eq(token), eq(runId));
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getRunId()).isEqualTo(runId);
    assertThat(response.getErrorMessage()).isEqualTo("Run is not discardable, status is: PENDING");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCannotDiscard() throws IOException {
    RunData initialRunData = new RunData();
    initialRunData.setAttributes(
        Attributes.builder().actions(Attributes.Actions.builder().isDiscardable(true).build()).build());
    RunData runData = new RunData();
    runData.setAttributes(Attributes.builder().status(PENDING).build());
    String runId = "runId";
    TerraformCloudConnectorDTO terraformCloudConnectorDTO = TerraformCloudConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    TerraformCloudConfig terraformCloudConfig =
        TerraformCloudConfig.builder()
            .terraformCloudCredentials(TerraformCloudApiTokenCredentials.builder().token(token).url(url).build())
            .build();
    TerraformCloudCleanupTaskParams taskParameters = TerraformCloudCleanupTaskParams.builder()
                                                         .terraformCloudConnectorDTO(terraformCloudConnectorDTO)
                                                         .encryptionDetails(encryptionDetails)
                                                         .runId(runId)
                                                         .build();
    doReturn(terraformCloudConfig)
        .when(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(eq(terraformCloudConnectorDTO), eq(encryptionDetails));
    when(terraformCloudTaskHelper.getRun(any(), any(), any())).thenReturn(initialRunData).thenReturn(runData);

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    TerraformCloudCleanupTaskResponse response = (TerraformCloudCleanupTaskResponse) delegateResponseData;
    verify(terraformCloudTaskHelper, times(2)).getRun(eq(url), eq(token), eq(runId));
    verify(terraformCloudTaskHelper).discardRun(eq(url), eq(token), eq(runId), eq(DISCARD_MESSAGE));
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getRunId()).isEqualTo(runId);
    assertThat(response.getErrorMessage()).isEqualTo("Did not discard, status is: PENDING");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExceptionThrown() throws IOException {
    RunData initialRunData = new RunData();
    initialRunData.setAttributes(
        Attributes.builder().actions(Attributes.Actions.builder().isDiscardable(true).build()).build());
    String runId = "runId";
    TerraformCloudConnectorDTO terraformCloudConnectorDTO = TerraformCloudConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    TerraformCloudConfig terraformCloudConfig =
        TerraformCloudConfig.builder()
            .terraformCloudCredentials(TerraformCloudApiTokenCredentials.builder().token(token).url(url).build())
            .build();
    TerraformCloudCleanupTaskParams taskParameters = TerraformCloudCleanupTaskParams.builder()
                                                         .terraformCloudConnectorDTO(terraformCloudConnectorDTO)
                                                         .encryptionDetails(encryptionDetails)
                                                         .runId(runId)
                                                         .build();
    doReturn(terraformCloudConfig)
        .when(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(eq(terraformCloudConnectorDTO), eq(encryptionDetails));
    when(terraformCloudTaskHelper.getRun(any(), any(), any())).thenReturn(initialRunData);
    doThrow(new TerraformCloudApiException("Failed to discard", 404))
        .when(terraformCloudTaskHelper)
        .discardRun(eq(url), eq(token), eq(runId), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    TerraformCloudCleanupTaskResponse response = (TerraformCloudCleanupTaskResponse) delegateResponseData;
    verify(terraformCloudTaskHelper, times(1)).getRun(eq(url), eq(token), eq(runId));
    verify(terraformCloudTaskHelper).discardRun(eq(url), eq(token), eq(runId), eq(DISCARD_MESSAGE));
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getRunId()).isEqualTo(runId);
    assertThat(response.getErrorMessage()).isEqualTo("Failed to discard");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUnsupportedParametersType() throws IOException {
    assertThatThrownBy(() -> task.run(TerraformCloudRefreshTaskParams.builder().build()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Unsupported parameters type");
  }
}
