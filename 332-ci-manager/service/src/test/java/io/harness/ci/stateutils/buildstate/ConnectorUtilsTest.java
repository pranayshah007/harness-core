/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stateutils.buildstate;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsSpecDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class ConnectorUtilsTest extends CIExecutionTestBase {
  @Inject CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Mock CIFeatureFlagService featureFlagService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @InjectMocks ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  private NGAccess ngAccess;
  private ConnectorDTO azureRepoConnectorDto;
  private ConnectorDTO gitHubConnectorDto;
  private ConnectorDTO dockerConnectorDto;
  private ConnectorDTO azureConnectorDto;
  private ConnectorDTO k8sConnectorDto;
  private ConnectorDTO k8sConnectorFromDelegate;
  private ConnectorDTO awsCodeCommitConnectorDto;

  private static final String PROJ_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String ACCOUNT_ID = "accountId";

  private static final String connectorId01 = "gitHubConnector";
  private static final String connectorId02 = "dockerConnector";
  private static final String connectorId03 = "k8sConnector";
  private static final String connectorId04 = "k8sConnectorFromDelegate";
  private static final String connectorId05 = "awsCodeCommitConnector";
  private static final String connectorId06 = "azureRepoConnector";
  private static final String unsupportedConnectorId = "k8sConnectorFromDelegate";
  private static final Set<String> connectorIdSet =
      new HashSet<>(Arrays.asList(connectorId01, connectorId02, connectorId03));
  private Ambiance ambiance;

  @Before
  public void setUp() {
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", "accountId")
                   .putSetupAbstractions("projectIdentifier", "projectId")
                   .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                   .build();
    ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(ACCOUNT_ID).build();
    gitHubConnectorDto = ciExecutionPlanTestHelper.getGitHubConnectorDTO();
    dockerConnectorDto = ciExecutionPlanTestHelper.getDockerConnectorDTO();
    k8sConnectorDto = ciExecutionPlanTestHelper.getK8sConnectorDTO();
    k8sConnectorFromDelegate = ciExecutionPlanTestHelper.getK8sConnectorFromDelegateDTO();
    awsCodeCommitConnectorDto = ciExecutionPlanTestHelper.getAwsCodeCommitConnectorDTO();
    azureConnectorDto = ciExecutionPlanTestHelper.getAzureConnectorDTO();
    azureRepoConnectorDto = ciExecutionPlanTestHelper.getAzureRepoConnectorDTO();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testGetGitHubConnector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(gitHubConnectorDto));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId01), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(GitAuthenticationDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId01);
    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(gitHubConnectorDto.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType()).isEqualTo(gitHubConnectorDto.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier()).isEqualTo(gitHubConnectorDto.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier()).isEqualTo(gitHubConnectorDto.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(gitHubConnectorDto.getConnectorInfo().getProjectIdentifier());
    verify(connectorResourceClient, times(1)).get(eq(connectorId01), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(eq(ngAccess), any(GithubHttpCredentialsSpecDTO.class));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testGetDockerConnector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(dockerConnectorDto));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId02), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(DockerAuthCredentialsDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId02);
    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(dockerConnectorDto.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType()).isEqualTo(dockerConnectorDto.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier()).isEqualTo(dockerConnectorDto.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier()).isEqualTo(dockerConnectorDto.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(dockerConnectorDto.getConnectorInfo().getProjectIdentifier());
    verify(connectorResourceClient, times(1)).get(eq(connectorId02), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(eq(ngAccess), any(DockerAuthCredentialsDTO.class));
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testAzureConnector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(azureConnectorDto));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId02), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId02);
    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(azureConnectorDto.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType()).isEqualTo(azureConnectorDto.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier()).isEqualTo(azureConnectorDto.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier()).isEqualTo(azureConnectorDto.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(azureConnectorDto.getConnectorInfo().getProjectIdentifier());
    verify(connectorResourceClient, times(1)).get(eq(connectorId02), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
    verify(secretManagerClientService, times(1)).getEncryptionDetails(eq(ngAccess), any(AzureAuthCredentialDTO.class));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testK8sDockerConnector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(k8sConnectorDto));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId03), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);

    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(KubernetesAuthCredentialDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId03);

    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(k8sConnectorDto.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType()).isEqualTo(k8sConnectorDto.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier()).isEqualTo(k8sConnectorDto.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier()).isEqualTo(k8sConnectorDto.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(k8sConnectorDto.getConnectorInfo().getProjectIdentifier());

    verify(connectorResourceClient, times(1)).get(eq(connectorId03), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(eq(ngAccess), any(KubernetesAuthCredentialDTO.class));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testK8sDockerConnectorInheritedFromDelegate() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(k8sConnectorFromDelegate));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId04), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(KubernetesAuthCredentialDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId04);

    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(k8sConnectorFromDelegate.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType())
        .isEqualTo(k8sConnectorFromDelegate.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier()).isEqualTo(k8sConnectorFromDelegate.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier())
        .isEqualTo(k8sConnectorFromDelegate.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(k8sConnectorFromDelegate.getConnectorInfo().getProjectIdentifier());
    verify(connectorResourceClient, times(1)).get(eq(connectorId04), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testGetConnectorMap() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall01 = mock(Call.class);
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall02 = mock(Call.class);
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall03 = mock(Call.class);

    when(getConnectorResourceCall01.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(gitHubConnectorDto))));
    when(getConnectorResourceCall02.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(dockerConnectorDto))));
    when(getConnectorResourceCall03.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(k8sConnectorDto))));

    when(connectorResourceClient.get(eq(connectorId01), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall01);
    when(connectorResourceClient.get(eq(connectorId02), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall02);
    when(connectorResourceClient.get(eq(connectorId03), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall03);

    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(DecryptableEntity.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    Map<String, ConnectorDetails> connectorDetailsMap = connectorUtils.getConnectorDetailsMap(ngAccess, connectorIdSet);

    assertThat(connectorDetailsMap).hasSize(3);
    assertThat(connectorDetailsMap.keySet()).isEqualTo(connectorIdSet);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testGetConnector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> emptyResponseDTO = ResponseDTO.newResponse(Optional.empty());
    when(getConnectorResourceCall.execute())
        .thenReturn(Response.success(emptyResponseDTO))
        .thenThrow(new IOException("Error getting connector"));

    when(connectorResourceClient.get(eq(connectorId01), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(GitAuthenticationDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    assertThatThrownBy(() -> connectorUtils.getConnectorDetails(ngAccess, connectorId01))
        .isInstanceOf(CIStageExecutionException.class);
    assertThatThrownBy(() -> connectorUtils.getConnectorDetails(ngAccess, connectorId01))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testUnsupported() throws IOException {
    ConnectorDTO connectorDTO = ConnectorDTO.builder()
                                    .connectorInfo(ConnectorInfoDTO.builder()
                                                       .identifier(unsupportedConnectorId)
                                                       .connectorType(ConnectorType.VAULT)
                                                       .build())
                                    .build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);

    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(connectorDTO));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(unsupportedConnectorId), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(GitAuthenticationDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    assertThatThrownBy(() -> connectorUtils.getConnectorDetails(ngAccess, unsupportedConnectorId))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testGetAwsCodeCommitConnectorDetails() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(awsCodeCommitConnectorDto));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId05), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(GitAuthenticationDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId05);
    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(awsCodeCommitConnectorDto.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType())
        .isEqualTo(awsCodeCommitConnectorDto.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier())
        .isEqualTo(awsCodeCommitConnectorDto.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier())
        .isEqualTo(awsCodeCommitConnectorDto.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(awsCodeCommitConnectorDto.getConnectorInfo().getProjectIdentifier());
    verify(connectorResourceClient, times(1)).get(eq(connectorId05), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(eq(ngAccess), any(AwsCodeCommitHttpsCredentialsSpecDTO.class));
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldAddDelegateSelector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(k8sConnectorFromDelegate));
    when(featureFlagService.isEnabled(FeatureName.DISABLE_CI_STAGE_DEL_SELECTOR, "accountId")).thenReturn(false);

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));
    when(connectorResourceClient.get(any(), any(), any(), any())).thenReturn(getConnectorResourceCall);
    when(featureFlagService.isEnabled(FeatureName.DISABLE_CI_STAGE_DEL_SELECTOR, "accountId")).thenReturn(false);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .infrastructure(K8sDirectInfraYaml.builder()
                                                        .spec(K8sDirectInfraYamlSpec.builder()
                                                                  .connectorRef(ParameterField.createValueField("fd"))
                                                                  .build())
                                                        .build())
                                    .containerNames(new ArrayList<>())
                                    .build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(TASK_SELECTORS)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    List<TaskSelector> taskSelectors = connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
    assertThat(taskSelectors)
        .isEqualTo(Arrays.asList(TaskSelector.newBuilder().setSelector("delegate").setOrigin("default").build()));
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldAddPipelineDelegateSelector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(k8sConnectorFromDelegate));
    when(featureFlagService.isEnabled(FeatureName.DISABLE_CI_STAGE_DEL_SELECTOR, "accountId")).thenReturn(false);

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));
    when(connectorResourceClient.get(any(), any(), any(), any())).thenReturn(getConnectorResourceCall);
    when(featureFlagService.isEnabled(FeatureName.DISABLE_CI_STAGE_DEL_SELECTOR, "accountId")).thenReturn(false);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .infrastructure(K8sDirectInfraYaml.builder()
                                                        .spec(K8sDirectInfraYamlSpec.builder()
                                                                  .connectorRef(ParameterField.createValueField("fd"))
                                                                  .build())
                                                        .build())
                                    .containerNames(new ArrayList<>())
                                    .build())
                        .build());
    List<TaskSelector> selectorList = new ArrayList<>();
    selectorList.add(TaskSelector.newBuilder().setSelector("PipelineDelegate").setOrigin("Pipeline").build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(TASK_SELECTORS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(TaskSelectorSweepingOutput.builder().taskSelectors(selectorList).build())
                        .build());

    List<TaskSelector> taskSelectors = connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
    assertThat(taskSelectors)
        .isEqualTo(
            Arrays.asList(TaskSelector.newBuilder().setSelector("PipelineDelegate").setOrigin("Pipeline").build()));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetAzureRepoConnector() throws IOException {
    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(azureRepoConnectorDto));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(connectorId06), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID)))
        .thenReturn(getConnectorResourceCall);
    when(secretManagerClientService.getEncryptionDetails(eq(ngAccess), any(AzureRepoUsernameTokenDTO.class)))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorId06);
    assertThat(connectorDetails.getConnectorConfig())
        .isEqualTo(azureRepoConnectorDto.getConnectorInfo().getConnectorConfig());
    assertThat(connectorDetails.getConnectorType())
        .isEqualTo(azureRepoConnectorDto.getConnectorInfo().getConnectorType());
    assertThat(connectorDetails.getIdentifier()).isEqualTo(azureRepoConnectorDto.getConnectorInfo().getIdentifier());
    assertThat(connectorDetails.getOrgIdentifier())
        .isEqualTo(azureRepoConnectorDto.getConnectorInfo().getOrgIdentifier());
    assertThat(connectorDetails.getProjectIdentifier())
        .isEqualTo(azureRepoConnectorDto.getConnectorInfo().getProjectIdentifier());
    verify(connectorResourceClient, times(1)).get(eq(connectorId06), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJ_ID));
    verify(secretManagerClientService, times(1))
        .getEncryptionDetails(eq(ngAccess), any(AzureRepoHttpCredentialsSpecDTO.class));
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetScmAuthType() throws IOException {
    ConnectorDetails connectorDetails1 = ciExecutionPlanTestHelper.getGitConnector();
    assertThat(connectorUtils.getScmAuthType(connectorDetails1)).isEqualTo("Http");

    ConnectorDetails connectorDetails2 = ciExecutionPlanTestHelper.getGithubConnector();
    assertThat(connectorUtils.getScmAuthType(connectorDetails2)).isEqualTo("Http");

    ConnectorDetails connectorDetails3 = ciExecutionPlanTestHelper.getBitBucketConnector();
    assertThat(connectorUtils.getScmAuthType(connectorDetails3)).isEqualTo("Http");

    ConnectorDetails connectorDetails4 = ciExecutionPlanTestHelper.getGitLabConnector();
    assertThat(connectorUtils.getScmAuthType(connectorDetails4)).isEqualTo("Http");

    ConnectorDetails connectorDetails5 = ciExecutionPlanTestHelper.getCodeCommitConnector();
    assertThat(connectorUtils.getScmAuthType(connectorDetails5)).isEqualTo("HTTPS");
  }
}
