/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.service.impl;

import static io.harness.perpetualtask.PerpetualTaskType.ARTIFACT_COLLECTION_NG;
import static io.harness.perpetualtask.PerpetualTaskType.GITPOLLING_NG;
import static io.harness.perpetualtask.PerpetualTaskType.MANIFEST_COLLECTION_NG;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.gitpolling.utils.GitPollingStepHelper;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.delegate.task.gitpolling.request.GitPollingTaskParameters;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.HelmVersion;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.polling.ArtifactCollectionTaskParamsNg;
import io.harness.perpetualtask.polling.GitPollingTaskParamsNg;
import io.harness.perpetualtask.polling.ManifestCollectionTaskParamsNg;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.GitHubPollingInfo;
import io.harness.polling.bean.HelmChartManifestInfo;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingType;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.service.impl.artifact.ArtifactPerpetualTaskHelperNg;
import io.harness.polling.service.impl.gitpolling.GitPollingPerpetualTaskHelperNg;
import io.harness.polling.service.impl.manifest.ManifestPerpetualTaskHelperNg;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.util.Durations;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class PollingPerpetualTaskServiceTest extends CDNGTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String CONNECTOR_REF = "CONNECTOR_REF";
  private static final String UUID = "UUID";
  private static final String SIGNATURE_1 = "sig-1";
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";

  private ManifestPerpetualTaskHelperNg spyManifestPerpetualTaskHelperNg;
  private ArtifactPerpetualTaskHelperNg spyArtifactPerpetualTaskHelperNg;

  private GitPollingPerpetualTaskHelperNg spyGitPollingPerpetualTaskHelperNg;
  private PollingPerpetualTaskServiceImpl pollingPerpetualTaskService;
  @Mock DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Mock PollingServiceImpl pollingService;
  @Mock K8sStepHelper k8sStepHelper;
  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock ArtifactStepHelper artifactStepHelper;
  @Mock GitPollingStepHelper gitPollingStepHelper;

  @Before
  public void setup() {
    ManifestPerpetualTaskHelperNg manifestPerpetualTaskHelperNg = new ManifestPerpetualTaskHelperNg(
        k8sStepHelper, kryoSerializer, referenceFalseKryoSerializer, delegateServiceGrpcClient);
    spyManifestPerpetualTaskHelperNg = Mockito.spy(manifestPerpetualTaskHelperNg);

    ArtifactPerpetualTaskHelperNg artifactPerpetualTaskHelperNg = new ArtifactPerpetualTaskHelperNg(
        kryoSerializer, referenceFalseKryoSerializer, delegateServiceGrpcClient, artifactStepHelper);
    spyArtifactPerpetualTaskHelperNg = Mockito.spy(artifactPerpetualTaskHelperNg);

    GitPollingPerpetualTaskHelperNg gitPollingPerpetualTaskHelperNg = new GitPollingPerpetualTaskHelperNg(
        referenceFalseKryoSerializer, kryoSerializer, delegateServiceGrpcClient, gitPollingStepHelper);
    spyGitPollingPerpetualTaskHelperNg = Mockito.spy(gitPollingPerpetualTaskHelperNg);

    pollingPerpetualTaskService =
        new PollingPerpetualTaskServiceImpl(spyManifestPerpetualTaskHelperNg, spyArtifactPerpetualTaskHelperNg,
            spyGitPollingPerpetualTaskHelperNg, delegateServiceGrpcClient, pollingService);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateManifestPerpetualTask() {
    HttpStoreConfig storeConfig =
        HttpStoreConfig.builder().connectorRef(ParameterField.<String>builder().value(CONNECTOR_REF).build()).build();
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .chartName("chartName")
                                                      .helmVersion(HelmVersion.V2)
                                                      .store(storeConfig)
                                                      .storeType(StoreConfigType.HTTP)
                                                      .build();
    PollingDocument pollingDocument = PollingDocument.builder()
                                          .uuid(UUID)
                                          .accountId(ACCOUNT_ID)
                                          .pollingType(PollingType.MANIFEST)
                                          .signatures(Collections.singletonList(SIGNATURE_1))
                                          .pollingInfo(helmChartManifestInfo)
                                          .build();

    HelmChartManifestDelegateConfig delegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .helmVersion(HelmVersion.V2)
            .chartName("chartName")
            .storeDelegateConfig(
                HttpHelmStoreDelegateConfig.builder().httpHelmConnector(HttpHelmConnectorDTO.builder().build()).build())
            .build();
    when(pollingService.attachPerpetualTask(anyString(), anyString(), anyString())).thenReturn(true);
    when(k8sStepHelper.getManifestDelegateConfig(any(), any())).thenReturn(delegateConfig);
    when(delegateServiceGrpcClient.createPerpetualTask(any(AccountId.class), eq(MANIFEST_COLLECTION_NG), any(), any(),
             eq(false), eq("MANIFEST Collection Task"), eq(UUID)))
        .thenReturn(PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build());

    pollingPerpetualTaskService.createPerpetualTask(pollingDocument);

    ArgumentCaptor<PerpetualTaskSchedule> scheduleCaptor = ArgumentCaptor.forClass(PerpetualTaskSchedule.class);
    ArgumentCaptor<PerpetualTaskClientContextDetails> taskContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContextDetails.class);

    verify(spyManifestPerpetualTaskHelperNg).createPerpetualTaskExecutionBundle(pollingDocument);
    verify(pollingService).attachPerpetualTask(anyString(), anyString(), anyString());
    verify(delegateServiceGrpcClient)
        .createPerpetualTask(any(AccountId.class), eq(MANIFEST_COLLECTION_NG), scheduleCaptor.capture(),
            taskContextCaptor.capture(), eq(false), eq("MANIFEST Collection Task"), eq(UUID));

    PerpetualTaskSchedule schedule = scheduleCaptor.getValue();
    PerpetualTaskClientContextDetails taskContext = taskContextCaptor.getValue();

    assertThat(schedule).isNotNull();
    assertThat(schedule.getInterval()).isEqualTo(Durations.fromMinutes(2));
    assertThat(schedule.getTimeout()).isEqualTo(Durations.fromMinutes(3));

    assertThat(taskContext).isNotNull();
    assertThat(taskContext.getExecutionBundle()).isNotNull();
    PerpetualTaskExecutionBundle executionBundle = taskContext.getExecutionBundle();
    Any perpetualTaskParams = executionBundle.getTaskParams();
    ManifestCollectionTaskParamsNg params = AnyUtils.unpack(perpetualTaskParams, ManifestCollectionTaskParamsNg.class);
    assertThat(params.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(params.getPollingDocId()).isEqualTo(UUID);

    ManifestDelegateConfig manifestConfig = (ManifestDelegateConfig) referenceFalseKryoSerializer.asObject(
        params.getManifestCollectionParams().toByteArray());
    assertThat(manifestConfig).isEqualTo(delegateConfig);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateArtifactPerpetualTask() {
    DockerHubArtifactInfo dockerHubArtifactInfo =
        DockerHubArtifactInfo.builder().imagePath("imagePath").connectorRef(CONNECTOR_REF).build();
    PollingDocument pollingDocument = PollingDocument.builder()
                                          .uuid(UUID)
                                          .accountId(ACCOUNT_ID)
                                          .pollingType(PollingType.ARTIFACT)
                                          .signatures(Collections.singletonList(SIGNATURE_1))
                                          .pollingInfo(dockerHubArtifactInfo)
                                          .build();

    DockerArtifactDelegateRequest delegateRequest =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .connectorRef(CONNECTOR_REF)
            .dockerConnectorDTO(DockerConnectorDTO.builder().dockerRegistryUrl("url").build())
            .build();
    when(pollingService.attachPerpetualTask(anyString(), anyString(), anyString())).thenReturn(true);
    when(artifactStepHelper.toSourceDelegateRequest(any(), any())).thenReturn(delegateRequest);
    when(delegateServiceGrpcClient.createPerpetualTask(any(AccountId.class), eq(ARTIFACT_COLLECTION_NG), any(), any(),
             eq(false), eq("ARTIFACT Collection Task"), eq(UUID)))
        .thenReturn(PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build());
    when(delegateServiceGrpcClient.isTaskTypeSupported(any(), any())).thenReturn(false);

    pollingPerpetualTaskService.createPerpetualTask(pollingDocument);

    ArgumentCaptor<PerpetualTaskSchedule> scheduleCaptor = ArgumentCaptor.forClass(PerpetualTaskSchedule.class);
    ArgumentCaptor<PerpetualTaskClientContextDetails> taskContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContextDetails.class);

    verify(spyArtifactPerpetualTaskHelperNg).createPerpetualTaskExecutionBundle(pollingDocument);
    verify(pollingService).attachPerpetualTask(anyString(), anyString(), anyString());
    verify(delegateServiceGrpcClient)
        .createPerpetualTask(any(AccountId.class), eq(ARTIFACT_COLLECTION_NG), scheduleCaptor.capture(),
            taskContextCaptor.capture(), eq(false), eq("ARTIFACT Collection Task"), eq(UUID));

    PerpetualTaskSchedule schedule = scheduleCaptor.getValue();
    PerpetualTaskClientContextDetails taskContext = taskContextCaptor.getValue();

    assertThat(schedule).isNotNull();
    assertThat(schedule.getInterval()).isEqualTo(Durations.fromMinutes(1));
    assertThat(schedule.getTimeout()).isEqualTo(Durations.fromMinutes(2));

    assertThat(taskContext).isNotNull();
    assertThat(taskContext.getExecutionBundle()).isNotNull();
    PerpetualTaskExecutionBundle executionBundle = taskContext.getExecutionBundle();
    Any perpetualTaskParams = executionBundle.getTaskParams();
    ArtifactCollectionTaskParamsNg params = AnyUtils.unpack(perpetualTaskParams, ArtifactCollectionTaskParamsNg.class);
    assertThat(params.getPollingDocId()).isEqualTo(UUID);

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) referenceFalseKryoSerializer.asObject(
        params.getArtifactCollectionParams().toByteArray());
    assertThat(taskParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(taskParameters.getAttributes()).isEqualTo(delegateRequest);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testCreateGitpollingPerpetualTask() {
    GitHubPollingInfo gitHubPollingInfo =
        GitHubPollingInfo.builder().pollInterval(1).connectorRef(CONNECTOR_REF).build();
    PollingDocument pollingDocument = PollingDocument.builder()
                                          .uuid(UUID)
                                          .accountId(ACCOUNT_ID)
                                          .pollingType(PollingType.WEBHOOK_POLLING)
                                          .signatures(Collections.singletonList(SIGNATURE_1))
                                          .pollingInfo(gitHubPollingInfo)
                                          .build();
    Set<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add("xyz");
    GitHubPollingDelegateRequest delegateRequest =
        GitHubPollingDelegateRequest.builder()
            .connectorRef(CONNECTOR_REF)
            .githubConnectorDTO(GithubConnectorDTO.builder().url("url").build())
            .connectorDetails(
                ConnectorDetails.builder()
                    .connectorConfig(
                        GithubConnectorDTO.builder()
                            .authentication(
                                GithubAuthenticationDTO.builder()
                                    .authType(GitAuthType.HTTP)
                                    .credentials(GithubHttpCredentialsDTO.builder()
                                                     .httpCredentialsSpec(GithubOauthDTO.builder()
                                                                              .tokenRef(SecretRefData.builder().build())
                                                                              .build())
                                                     .build())
                                    .build())
                            .delegateSelectors(delegateSelectors)
                            .url("url")
                            .build())
                    .encryptedDataDetails(Collections.emptyList())
                    .build())
            .build();
    when(pollingService.attachPerpetualTask(anyString(), anyString(), anyString())).thenReturn(true);
    when(gitPollingStepHelper.toSourceDelegateRequest(any(), any())).thenReturn(delegateRequest);
    when(delegateServiceGrpcClient.createPerpetualTask(any(AccountId.class), eq(GITPOLLING_NG), any(), any(), eq(false),
             eq("WEBHOOK_POLLING Collection Task"), eq(UUID)))
        .thenReturn(PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build());

    pollingPerpetualTaskService.createPerpetualTask(pollingDocument);

    ArgumentCaptor<PerpetualTaskSchedule> scheduleCaptor = ArgumentCaptor.forClass(PerpetualTaskSchedule.class);
    ArgumentCaptor<PerpetualTaskClientContextDetails> taskContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContextDetails.class);

    verify(spyGitPollingPerpetualTaskHelperNg).createPerpetualTaskExecutionBundle(pollingDocument);
    verify(pollingService).attachPerpetualTask(anyString(), anyString(), anyString());
    verify(delegateServiceGrpcClient)
        .createPerpetualTask(any(AccountId.class), eq(GITPOLLING_NG), scheduleCaptor.capture(),
            taskContextCaptor.capture(), eq(false), eq("WEBHOOK_POLLING Collection Task"), eq(UUID));

    PerpetualTaskSchedule schedule = scheduleCaptor.getValue();
    PerpetualTaskClientContextDetails taskContext = taskContextCaptor.getValue();

    assertThat(schedule).isNotNull();
    assertThat(schedule.getInterval()).isEqualTo(Durations.fromMinutes(1));
    assertThat(schedule.getTimeout()).isEqualTo(Durations.fromMinutes(2));

    assertThat(taskContext).isNotNull();
    assertThat(taskContext.getExecutionBundle()).isNotNull();
    PerpetualTaskExecutionBundle executionBundle = taskContext.getExecutionBundle();
    Any perpetualTaskParams = executionBundle.getTaskParams();
    GitPollingTaskParamsNg params = AnyUtils.unpack(perpetualTaskParams, GitPollingTaskParamsNg.class);
    assertThat(params.getPollingDocId()).isEqualTo(UUID);
    assertThat(executionBundle.getCapabilitiesCount()).isEqualTo(1);

    GitPollingTaskParameters taskParameters =
        (GitPollingTaskParameters) kryoSerializer.asObject(params.getGitpollingWebhookParams().toByteArray());
    assertThat(taskParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeletePerpetualTask() {
    pollingPerpetualTaskService.deletePerpetualTask(PERPETUAL_TASK_ID, ACCOUNT_ID);
    verify(delegateServiceGrpcClient)
        .deletePerpetualTask(AccountId.newBuilder().setId(ACCOUNT_ID).build(),
            PerpetualTaskId.newBuilder().setId(PERPETUAL_TASK_ID).build());
  }
}
