/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.utils;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class EcrImagePullSecretHelperTest extends CategoryTest {
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private KryoSerializer kryoSerializer;
  public static String accountId = "accountId";
  public static String orgId = "orgId";
  public static String projectId = "projectId";
  @Mock ExceptionManager exceptionManager;
  @InjectMocks private EcrImagePullSecretHelper ecrImagePullSecretHelper;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExecuteSyncTask() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    DelegateResponseData response = ArtifactTaskResponse.builder()
                                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                        .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                        .build();
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();
    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    EcrArtifactDelegateRequest ecrArtifactDelegateRequest =
        EcrArtifactDelegateRequest.builder().imagePath("imagePath").tag("1.0").build();
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);
    assertThat(ecrImagePullSecretHelper.executeSyncTask(ecrArtifactDelegateRequest, ArtifactTaskType.GET_IMAGE_URL,
                   baseNGAccess, "execute sync task failed"))
        .isEqualTo(artifactTaskExecutionResponse);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBaseNGAccess() {
    BaseNGAccess baseNGAccess = ecrImagePullSecretHelper.getBaseNGAccess(accountId, orgId, projectId);
    assertEquals(baseNGAccess.getAccountIdentifier(), accountId);
    assertEquals(baseNGAccess.getOrgIdentifier(), orgId);
    assertEquals(baseNGAccess.getProjectIdentifier(), projectId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails() {
    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();

    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    when(secretManagerClientService.getEncryptionDetails(eq(baseNGAccess), any())).thenReturn(encryptedDataDetailList);
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    assertEquals(ecrImagePullSecretHelper.getEncryptionDetails(awsConnectorDTO, baseNGAccess).size(), 0);
    awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().config(AwsManualConfigSpecDTO.builder().build()).build())
            .build();
    assertEquals(ecrImagePullSecretHelper.getEncryptionDetails(awsConnectorDTO, baseNGAccess).size(), 1);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testResponseDataError() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();

    DelegateResponseData response = ArtifactTaskResponse.builder()
                                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                        .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                        .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();

    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest =
        EcrArtifactDelegateRequest.builder().imagePath("imagePath").tag("1.0").build();

    when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ErrorNotifyResponseData.builder().errorMessage("Testing").build());

    assertThatThrownBy(()
                           -> ecrImagePullSecretHelper.executeSyncTask(ecrArtifactDelegateRequest,
                               ArtifactTaskType.GET_IMAGE_URL, baseNGAccess, "execute sync task failed"))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("execute sync task failed - Testing");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncTaskFailure() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();

    DelegateResponseData response = ArtifactTaskResponse.builder()
                                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                        .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                        .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
                                                    .errorMessage("Test failed")
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();

    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest =
        EcrArtifactDelegateRequest.builder().imagePath("imagePath").tag("1.0").build();

    when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> ecrImagePullSecretHelper.executeSyncTask(ecrArtifactDelegateRequest,
                               ArtifactTaskType.GET_IMAGE_URL, baseNGAccess, "execute sync task failed"))
        .isInstanceOf(WingsException.class)
        .hasMessage("execute sync task failed - Test failed with error code: DEFAULT_ERROR_CODE");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testDelegateServiceDriverException() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();

    DelegateResponseData response = ArtifactTaskResponse.builder()
                                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                        .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                        .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
                                                    .errorMessage("Test failed")
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();

    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest =
        EcrArtifactDelegateRequest.builder().imagePath("imagePath").tag("1.0").build();

    when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new DelegateServiceDriverException("DelegateServiceDriverException"));

    when(exceptionManager.processException(any(), any(), any()))
        .thenThrow(new WingsException("wings exception message"));

    assertThatThrownBy(()
                           -> ecrImagePullSecretHelper.executeSyncTask(ecrArtifactDelegateRequest,
                               ArtifactTaskType.GET_IMAGE_URL, baseNGAccess, "execute sync task failed"))
        .isInstanceOf(WingsException.class)
        .hasMessage("wings exception message");
  }
}
