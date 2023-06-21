/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.executioncapability.AlwaysFalseValidationCapability;
import io.harness.delegate.beans.executioncapability.AwsCliInstallationCapability;
import io.harness.delegate.beans.executioncapability.AwsRegionCapability;
import io.harness.delegate.beans.executioncapability.AwsSamInstallationCapability;
import io.harness.delegate.beans.executioncapability.ChartMuseumCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.GitInstallationCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.delegate.beans.executioncapability.LiteEngineConnectionCapability;
import io.harness.delegate.beans.executioncapability.PcfAutoScalarCapability;
import io.harness.delegate.beans.executioncapability.PcfInstallationCapability;
import io.harness.delegate.beans.executioncapability.ProcessExecutorCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.executioncapability.ServerlessInstallationCapability;
import io.harness.delegate.beans.executioncapability.SmbConnectionCapability;
import io.harness.delegate.beans.executioncapability.SmtpCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityBulkOrExecutionCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;
import io.harness.delegate.beans.executioncapability.SystemEnvCheckerCapability;
import io.harness.delegate.beans.executioncapability.WinrmConnectivityExecutionCapability;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateResponse;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.delegate.task.gitpolling.GitPollingTaskType;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.delegate.task.gitpolling.request.GitPollingTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstDeployTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstSwapRoutesTaskParameters;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import software.wings.beans.AwsConfig;
import software.wings.beans.CustomArtifactServerConfig;
import software.wings.beans.EcrConfig;
import software.wings.beans.ElasticLoadBalancerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.PhysicalDataCenterConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.SlackConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.StringValue;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CEAzureConfig;
import software.wings.beans.ce.CEGcpConfig;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.config.ArtifactSourceable;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitDiffRequest;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.ecs.request.EcsBGListenerUpdateRequest;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsBGRoute53ServiceSetupResponse;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsListenerUpdateCommandResponse;
import software.wings.helpers.ext.ecs.response.EcsRunTaskDeployResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceSetupResponse;
import software.wings.helpers.ext.helm.response.HelmInstallCommandResponse;
import software.wings.sm.states.JenkinsExecutionResponse;
import software.wings.sm.states.KubernetesSteadyStateCheckResponse;
import software.wings.sm.states.KubernetesSwapServiceSelectorsResponse;

import java.util.Set;

@OwnedBy(HarnessTeam.DEL)
public class DelegateTasksBeansMorphiaRegistrar implements MorphiaRegistrar {
  private String cf = "helpers.ext.cloudformation.";

  @Override
  public void registerClasses(Set<Class> set) {
    set.add(HDelegateTask.class);
    set.add(ExecutionCapabilityDemander.class);
    set.add(ArtifactSourceable.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("delegate.beans.ErrorNotifyResponseData", ErrorNotifyResponseData.class);
    h.put("delegate.beans.executioncapability.AlwaysFalseValidationCapability", AlwaysFalseValidationCapability.class);
    h.put("delegate.beans.executioncapability.AwsRegionCapability", AwsRegionCapability.class);
    h.put("delegate.beans.executioncapability.ChartMuseumCapability", ChartMuseumCapability.class);
    h.put("delegate.beans.executioncapability.GitInstallationCapability", GitInstallationCapability.class);
    h.put("delegate.beans.executioncapability.HelmInstallationCapability", HelmInstallationCapability.class);
    h.put("delegate.beans.executioncapability.HttpConnectionExecutionCapability",
        HttpConnectionExecutionCapability.class);
    h.put("delegate.beans.executioncapability.LiteEngineConnectionCapability", LiteEngineConnectionCapability.class);
    h.put("delegate.beans.executioncapability.KustomizeCapability", KustomizeCapability.class);
    h.put("delegate.beans.executioncapability.PcfAutoScalarCapability", PcfAutoScalarCapability.class);
    h.put("delegate.beans.executioncapability.PcfInstallationCapability", PcfInstallationCapability.class);
    h.put("delegate.beans.executioncapability.ProcessExecutorCapability", ProcessExecutorCapability.class);
    h.put("delegate.beans.executioncapability.SmbConnectionCapability", SmbConnectionCapability.class);
    h.put("delegate.beans.executioncapability.SmtpCapability", SmtpCapability.class);
    h.put("delegate.beans.executioncapability.SocketConnectivityExecutionCapability",
        SocketConnectivityExecutionCapability.class);
    h.put("delegate.beans.executioncapability.SocketConnectivityBulkOrExecutionCapability",
        SocketConnectivityBulkOrExecutionCapability.class);
    h.put(
        "delegate.beans.executioncapability.ServerlessInstallationCapability", ServerlessInstallationCapability.class);
    h.put("delegate.beans.executioncapability.AwsCliInstallationCapability", AwsCliInstallationCapability.class);
    h.put("delegate.beans.executioncapability.SystemEnvCheckerCapability", SystemEnvCheckerCapability.class);
    h.put("delegate.beans.executioncapability.SelectorCapability", SelectorCapability.class);
    h.put("delegate.beans.executioncapability.WinrmConnectivityExecutionCapability",
        WinrmConnectivityExecutionCapability.class);
    h.put("delegate.command.CommandExecutionResult", CommandExecutionResult.class);
    h.put("delegate.task.spotinst.request.SpotInstDeployTaskParameters", SpotInstDeployTaskParameters.class);
    h.put("delegate.task.spotinst.request.SpotInstSetupTaskParameters", SpotInstSetupTaskParameters.class);
    h.put("delegate.task.spotinst.request.SpotInstSwapRoutesTaskParameters", SpotInstSwapRoutesTaskParameters.class);
    h.put("waiter.ErrorNotifyResponseData", ErrorNotifyResponseData.class);
    h.put("delegate.task.artifacts.docker.DockerArtifactDelegateResponse", DockerArtifactDelegateResponse.class);
    h.put("delegate.task.artifacts.jenkins.JenkinsArtifactDelegateResponse", JenkinsArtifactDelegateResponse.class);
    h.put("delegate.task.artifacts.jenkins.BambooArtifactDelegateResponse", BambooArtifactDelegateResponse.class);
    h.put("delegate.task.artifacts.response.ArtifactTaskResponse", ArtifactTaskResponse.class);
    h.put("delegate.task.artifacts.request.ArtifactTaskParameters", ArtifactTaskParameters.class);
    h.put("delegate.task.artifacts.docker.DockerArtifactDelegateRequest", DockerArtifactDelegateRequest.class);
    h.put("delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest", JenkinsArtifactDelegateRequest.class);
    h.put("delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest", BambooArtifactDelegateRequest.class);
    h.put("delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest", AzureArtifactsDelegateRequest.class);
    h.put(
        "delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateResponse", AzureArtifactsDelegateResponse.class);
    h.put("delegate.task.artifacts.ami.AMIArtifactDelegateRequest", AMIArtifactDelegateRequest.class);
    h.put("delegate.task.artifacts.ami.AMIArtifactDelegateResponse", AMIArtifactDelegateResponse.class);
    h.put("delegate.task.artifacts.ami.AMITag", AMITag.class);
    h.put("delegate.task.artifacts.ami.AMIFilter", AMIFilter.class);
    h.put("delegate.task.artifacts.custom.CustomArtifactDelegateRequest", CustomArtifactDelegateRequest.class);
    h.put("delegate.task.artifacts.custom.CustomArtifactDelegateResponse", CustomArtifactDelegateResponse.class);
    h.put("delegate.task.artifacts.gcr.GcrArtifactDelegateRequest", GcrArtifactDelegateRequest.class);
    h.put("delegate.task.artifacts.gcr.GcrArtifactDelegateResponse", GcrArtifactDelegateResponse.class);
    h.put("delegate.task.gcp.request.GcpValidationRequest", GcpValidationRequest.class);
    h.put("delegate.task.gcp.response.GcpValidationTaskResponse", GcpValidationTaskResponse.class);
    h.put("software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse", EcsServiceDeployResponse.class);
    w.put("helpers.ext.helm.response.HelmInstallCommandResponse", HelmInstallCommandResponse.class);
    h.put("delegate.task.gitpolling.request.GitPollingTaskParameters", GitPollingTaskParameters.class);
    h.put("delegate.task.gitpolling.github", GitHubPollingDelegateRequest.class);
    h.put("delegate.task.gitpolling.GitPollingSourceType", GitPollingSourceType.class);
    h.put("delegate.task.gitpolling.GitPollingTaskType", GitPollingTaskType.class);
    w.put("beans.AwsConfig", AwsConfig.class);
    w.put("beans.GitConfig", GitConfig.class);
    w.put("beans.yaml.GitCommandExecutionResponse", GitCommandExecutionResponse.class);
    w.put("beans.StringValue", StringValue.class);
    w.put("beans.ElasticLoadBalancerConfig", ElasticLoadBalancerConfig.class);
    w.put("beans.SlackConfig", SlackConfig.class);
    w.put("beans.yaml.GitCommitAndPushResult", GitCommitAndPushResult.class);
    w.put("beans.yaml.GitCommitRequest", GitCommitRequest.class);
    w.put("beans.yaml.GitDiffRequest", GitDiffRequest.class);
    w.put("beans.yaml.GitDiffResult", GitDiffResult.class);
    w.put("beans.CustomArtifactServerConfig", CustomArtifactServerConfig.class);
    w.put("beans.ce.CEAzureConfig", CEAzureConfig.class);
    w.put("beans.ce.CEAwsConfig", CEAwsConfig.class);
    w.put("beans.ce.CEGcpConfig", CEGcpConfig.class);
    w.put("beans.JenkinsConfig", JenkinsConfig.class);
    w.put("beans.PhysicalDataCenterConfig", PhysicalDataCenterConfig.class);
    w.put("beans.command.ContainerSetupCommandUnitExecutionData", ContainerSetupCommandUnitExecutionData.class);
    w.put("sm.states.JenkinsExecutionResponse", JenkinsExecutionResponse.class);
    w.put("beans.settings.helm.HttpHelmRepoConfig", HttpHelmRepoConfig.class);
    w.put("beans.GcpConfig", GcpConfig.class);
    w.put("helpers.ext.ecs.response.EcsCommandExecutionResponse", EcsCommandExecutionResponse.class);
    w.put("helpers.ext.ecs.response.EcsServiceSetupResponse", EcsServiceSetupResponse.class);
    w.put("helpers.ext.ecs.response.EcsBGRoute53ServiceSetupResponse", EcsBGRoute53ServiceSetupResponse.class);
    w.put("beans.EcrConfig", EcrConfig.class);
    w.put("beans.SpotInstConfig", SpotInstConfig.class);
    w.put("beans.PrometheusConfig", PrometheusConfig.class);
    w.put("sm.states.KubernetesSteadyStateCheckResponse", KubernetesSteadyStateCheckResponse.class);
    w.put("sm.states.KubernetesSwapServiceSelectorsResponse", KubernetesSwapServiceSelectorsResponse.class);
    w.put("beans.infrastructure.instance.info.EcsContainerInfo", EcsContainerInfo.class);
    w.put("beans.infrastructure.instance.info.K8sPodInfo", K8sPodInfo.class);
    w.put("beans.infrastructure.instance.info.KubernetesContainerInfo", KubernetesContainerInfo.class);
    h.put("waiter.ListNotifyResponseData", ListNotifyResponseData.class);
    w.put(cf + "response.CloudFormationCommandExecutionResponse", CloudFormationCommandExecutionResponse.class);
    w.put(cf + "response.CloudFormationCreateStackResponse", CloudFormationCreateStackResponse.class);
    w.put("helpers.ext.ecs.response.EcsRunTaskDeployResponse", EcsRunTaskDeployResponse.class);
    w.put("helpers.ext.ecs.response.EcsRunTaskDeployRequest", EcsRunTaskDeployRequest.class);
    w.put("helpers.ext.ecs.request.EcsBGListenerUpdateRequest", EcsBGListenerUpdateRequest.class);
    w.put("helpers.ext.ecs.response.EcsListenerUpdateCommandResponse", EcsListenerUpdateCommandResponse.class);
    h.put("delegate.beans.executioncapability.AwsSamInstallationCapability", AwsSamInstallationCapability.class);
  }
}