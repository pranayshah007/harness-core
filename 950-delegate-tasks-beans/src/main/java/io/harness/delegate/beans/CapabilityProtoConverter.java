/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.capability.AwsRegionParameters;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.ChartMuseumParameters;
import io.harness.capability.GitInstallationParameters;
import io.harness.capability.HelmInstallationParameters;
import io.harness.capability.HttpConnectionParameters;
import io.harness.capability.KustomizeParameters;
import io.harness.capability.LiteEngineConnectionParameters;
import io.harness.capability.PcfAutoScalarParameters;
import io.harness.capability.PcfConnectivityParameters;
import io.harness.capability.PcfInstallationParameters;
import io.harness.capability.ProcessExecutorParameters;
import io.harness.capability.SftpCapabilityParameters;
import io.harness.capability.SmbConnectionParameters;
import io.harness.capability.SmtpParameters;
import io.harness.capability.SocketConnectivityBulkOrParameters;
import io.harness.capability.SocketConnectivityParameters;
import io.harness.capability.SystemEnvParameters;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.AwsRegionCapability;
import io.harness.delegate.beans.executioncapability.ChartMuseumCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.delegate.beans.executioncapability.LiteEngineConnectionCapability;
import io.harness.delegate.beans.executioncapability.PcfAutoScalarCapability;
import io.harness.delegate.beans.executioncapability.PcfConnectivityCapability;
import io.harness.delegate.beans.executioncapability.PcfInstallationCapability;
import io.harness.delegate.beans.executioncapability.ProcessExecutorCapability;
import io.harness.delegate.beans.executioncapability.SftpCapability;
import io.harness.delegate.beans.executioncapability.SmbConnectionCapability;
import io.harness.delegate.beans.executioncapability.SmtpCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityBulkOrExecutionCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;
import io.harness.delegate.beans.executioncapability.SystemEnvCheckerCapability;
import io.harness.k8s.model.HelmVersion;

import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.DEL)
public class CapabilityProtoConverter {
  public static boolean shouldCompareResults(CapabilityParameters parameters) {
    if (parameters == null) {
      return false;
    }
    switch (parameters.getCapabilityCase()) {
      case AWS_REGION_PARAMETERS:
      case CHART_MUSEUM_PARAMETERS:
      case GIT_INSTALLATION_PARAMETERS:
      case HELM_INSTALLATION_PARAMETERS:
      case HTTP_CONNECTION_PARAMETERS:
      case PCF_AUTO_SCALAR_PARAMETERS:
      case PCF_CONNECTIVITY_PARAMETERS:
      case PCF_INSTALLATION_PARAMETERS:
      case KUSTOMIZE_PARAMETERS:
      case PROCESS_EXECUTOR_PARAMETERS:
      case SFTP_CAPABILITY_PARAMETERS:
      case SMB_CONNECTION_PARAMETERS:
      case SOCKET_CONNECTIVITY_PARAMETERS:
      case SOCKET_CONNECTIVITY_BULK_OR_PARAMETERS:
      case SYSTEM_ENV_PARAMETERS:
        return true;
      case SMTP_PARAMETERS:
      default:
        return false;
    }
  }

  public static CapabilityParameters toProto(ExecutionCapability executionCapability) {
    CapabilityParameters.Builder builder = CapabilityParameters.newBuilder();

    switch (executionCapability.getCapabilityType()) {
      case AWS_REGION:
        AwsRegionCapability capability = (AwsRegionCapability) executionCapability;
        return builder.setAwsRegionParameters(AwsRegionParameters.newBuilder().setRegion(capability.getRegion()))
            .build();
      case CHART_MUSEUM:
        boolean useLatestChartMuseumVersion =
            ((ChartMuseumCapability) executionCapability).isUseLatestChartMuseumVersion();
        return builder
            .setChartMuseumParameters(
                ChartMuseumParameters.newBuilder().setUseLatestChartMuseumVersion(useLatestChartMuseumVersion))
            .build();
      case GIT_INSTALLATION:
        return builder.setGitInstallationParameters(GitInstallationParameters.getDefaultInstance()).build();
      case HELM_INSTALL:
        HelmInstallationCapability helmInstallationCapability = (HelmInstallationCapability) executionCapability;

        HelmInstallationParameters.HelmVersion helmVersion = HelmInstallationParameters.HelmVersion.V2;
        if (helmInstallationCapability.getVersion().equals(HelmVersion.V3)) {
          helmVersion = HelmInstallationParameters.HelmVersion.V3;
        } else if (helmInstallationCapability.getVersion().equals(HelmVersion.V380)) {
          helmVersion = HelmInstallationParameters.HelmVersion.V380;
        }

        return builder
            .setHelmInstallationParameters(HelmInstallationParameters.newBuilder().setHelmVersion(helmVersion))
            .build();
      case HTTP:
        HttpConnectionExecutionCapability httpConnectionExecutionCapability =
            (HttpConnectionExecutionCapability) executionCapability;
        if (httpConnectionExecutionCapability.getHeaders() != null) {
          return builder
              .setHttpConnectionParameters(
                  HttpConnectionParameters.newBuilder()
                      .setUrl(httpConnectionExecutionCapability.fetchConnectableUrl())
                      .addAllHeaders(httpConnectionExecutionCapability.getHeaders()
                                         .stream()
                                         .map(entry
                                             -> HttpConnectionParameters.Header.newBuilder()
                                                    .setKey(entry.getKey())
                                                    .setValue(entry.getValue())
                                                    .build())
                                         .collect(Collectors.toList()))
                      .setIgnoreResponseCode(httpConnectionExecutionCapability.isIgnoreResponseCode()))
              .build();
        } else {
          return builder
              .setHttpConnectionParameters(
                  HttpConnectionParameters.newBuilder()
                      .setUrl(httpConnectionExecutionCapability.fetchCapabilityBasis())
                      .setIgnoreResponseCode(httpConnectionExecutionCapability.isIgnoreResponseCode()))
              .build();
        }
      case LITE_ENGINE:
        LiteEngineConnectionCapability liteEngineConnectionCapability =
            (LiteEngineConnectionCapability) executionCapability;
        return builder
            .setLiteEngineConnectionParameters(LiteEngineConnectionParameters.newBuilder()
                                                   .setIp(liteEngineConnectionCapability.getIp())
                                                   .setPort(liteEngineConnectionCapability.getPort()))
            .build();
      case KUSTOMIZE:
        KustomizeCapability kustomizeCapability = (KustomizeCapability) executionCapability;
        return builder
            .setKustomizeParameters(
                KustomizeParameters.newBuilder().setPluginRootDir(kustomizeCapability.getPluginRootDir()))
            .build();
      case PCF_AUTO_SCALAR:
        PcfAutoScalarCapability pcfAutoScalarCapability = (PcfAutoScalarCapability) executionCapability;
        return builder
            .setPcfAutoScalarParameters(PcfAutoScalarParameters.newBuilder().setCfCliVersion(
                PcfAutoScalarParameters.CfCliVersion.valueOf(pcfAutoScalarCapability.getVersion().name())))
            .build();
      case PCF_CONNECTIVITY:
        PcfConnectivityCapability pcfConnectivityCapability = (PcfConnectivityCapability) executionCapability;
        return builder
            .setPcfConnectivityParameters(
                PcfConnectivityParameters.newBuilder().setEndpointUrl(pcfConnectivityCapability.getEndpointUrl()))
            .build();
      case PCF_INSTALL:
        PcfInstallationCapability pcfInstallationCapability = (PcfInstallationCapability) executionCapability;
        return builder
            .setPcfInstallationParameters(PcfInstallationParameters.newBuilder()
                                              .setCfCliVersion(PcfInstallationParameters.CfCliVersion.valueOf(
                                                  pcfInstallationCapability.getVersion().name()))
                                              .build())
            .build();
      case PROCESS_EXECUTOR:
        ProcessExecutorCapability processExecutorCapability = (ProcessExecutorCapability) executionCapability;
        return builder
            .setProcessExecutorParameters(ProcessExecutorParameters.newBuilder().addAllArgs(
                processExecutorCapability.getProcessExecutorArguments()))
            .build();
      case SMTP:
        SmtpCapability smtpCapability = (SmtpCapability) executionCapability;
        return builder
            .setSmtpParameters(SmtpParameters.newBuilder()
                                   .setUseSsl(smtpCapability.isUseSSL())
                                   .setStartTls(smtpCapability.isStartTLS())
                                   .setHost(smtpCapability.getHost())
                                   .setPort(smtpCapability.getPort())
                                   .setUsername(smtpCapability.getUsername()))
            .build();
      case SFTP:
        SftpCapability sftpCapability = (SftpCapability) executionCapability;
        return builder
            .setSftpCapabilityParameters(SftpCapabilityParameters.newBuilder().setSftpUrl(sftpCapability.getSftpUrl()))
            .build();
      case SOCKET:
        SocketConnectivityExecutionCapability socketConnectivityExecutionCapability =
            (SocketConnectivityExecutionCapability) executionCapability;
        SocketConnectivityParameters.Builder socketConnectivityParametersBuilder =
            SocketConnectivityParameters.newBuilder();
        if (socketConnectivityExecutionCapability.getHostName() != null) {
          socketConnectivityParametersBuilder.setHostName(socketConnectivityExecutionCapability.getHostName());
        }
        if (socketConnectivityExecutionCapability.getPort() != null) {
          socketConnectivityParametersBuilder.setPort(
              Integer.parseInt(socketConnectivityExecutionCapability.getPort()));
        }
        if (socketConnectivityExecutionCapability.getUrl() != null) {
          socketConnectivityParametersBuilder.setUrl(socketConnectivityExecutionCapability.getUrl());
        }
        return builder.setSocketConnectivityParameters(socketConnectivityParametersBuilder).build();
      case SOCKET_BULK_OR:
        SocketConnectivityBulkOrExecutionCapability socketConnectivityBulkOrExecutionCapability =
            (SocketConnectivityBulkOrExecutionCapability) executionCapability;
        SocketConnectivityBulkOrParameters.Builder socketConnectivityBulkOrParametersBuilder =
            SocketConnectivityBulkOrParameters.newBuilder();

        if (EmptyPredicate.isNotEmpty(socketConnectivityBulkOrExecutionCapability.getHostNames())) {
          socketConnectivityBulkOrParametersBuilder.addAllHostNames(
              socketConnectivityBulkOrExecutionCapability.getHostNames());
        }
        socketConnectivityBulkOrParametersBuilder.setPort(socketConnectivityBulkOrExecutionCapability.getPort());

        return builder.setSocketConnectivityBulkOrParameters(socketConnectivityBulkOrParametersBuilder).build();
      case SYSTEM_ENV:
        SystemEnvCheckerCapability systemEnvCheckerCapability = (SystemEnvCheckerCapability) executionCapability;
        return builder
            .setSystemEnvParameters(SystemEnvParameters.newBuilder()
                                        .setProperty(systemEnvCheckerCapability.getSystemPropertyName())
                                        .setComparate(systemEnvCheckerCapability.getComparate()))
            .build();
      case SMB:
        SmbConnectionCapability smbConnectionCapability = (SmbConnectionCapability) executionCapability;
        return builder
            .setSmbConnectionParameters(
                SmbConnectionParameters.newBuilder().setSmbUrl(smbConnectionCapability.getSmbUrl()))
            .build();
      default:
        return null;
    }
  }
}
