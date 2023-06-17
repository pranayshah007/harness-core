/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.tas;

import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.pcf.CfRouteMappingCommandTaskHandlerNG;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.cf.artifact.TasRegistrySettingsAdapter;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.request.CfRouteMappingRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.File;
import java.util.Arrays;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfRouteMappingCommandTaskHandlerNGTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock private TasRegistrySettingsAdapter tasRegistrySettingsAdapter;

  @Mock TasTaskHelperBase tasTaskHelperBase;
  @Mock TasNgConfigMapper tasNgConfigMapper;
  @Mock protected CfCommandTaskHelperNG pcfCommandTaskHelper;
  @Mock PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @InjectMocks @Spy CfRouteMappingCommandTaskHandlerNG cfRouteMappingCommandTaskHandlerNG;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenMappingSucceeds() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.RouteMapping, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
    String variableYamls = "\n"
        + "\n"
        + "APP_MEMORY: 350M\n"
        + "INSTANCES: 1\n"
        + "APP_NAME: rolling_rollback_locallyyyyw_rediessre";
    String manifestsYaml = "applications:\n"
        + "- name: ((APP_NAME))\n"
        + "  instances: ((INSTANCES))\n"
        + "  memory: ((APP_MEMORY))\n"
        + "  routes:\n"
        + "    - route: rishabh_basic_app_addt.apps.pcf-harness.com\n"
        + "  services:\n"
        + "    - myautoscaler";
    String autoscalarYaml = "---\n"
        + "instance_limits:\n"
        + "  min: 1\n"
        + "  max: 2\n"
        + "rules: []\n"
        + "scheduled_limit_changes:\n"
        + "- recurrence: 10\n"
        + "  executes_at: \"2032-01-01T00:00:00Z\"\n"
        + "  instance_limits:\n"
        + "    min: 1\n"
        + "    max: 2";
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(manifestsYaml)
                                                  .autoscalarManifestYml(autoscalarYaml)
                                                  .variableYmls(Arrays.asList(variableYamls))
                                                  .build();

    String appName = "app";
    String routes = "routes";

    CfRouteMappingRequestNG cfRouteMappingRequestNG = CfRouteMappingRequestNG.builder()
                                                          .tasInfraConfig(tasInfraConfig)
                                                          .timeoutIntervalInMin(5)
                                                          .applicationName(appName)
                                                          .attachRoutes(true)
                                                          .routes(Arrays.asList(routes))
                                                          .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRouteMappingRequestNG.getTimeoutIntervalInMin())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRouteMappingRequestNG.getCfCliVersion())
            .useCFCLI(cfRouteMappingRequestNG.isUseCfCLI())
            .applicationName(cfRouteMappingRequestNG.getApplicationName())
            .build();

    String name = "name";
    String id = "id";
    String url = "url";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(Arrays.asList(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

    int instances = 1;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(id)
                                              .diskQuota(1)
                                              .instances(instances)
                                              .memoryLimit(1)
                                              .name(name)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(instances)
                                              .build();
    doReturn(applicationDetail).when(pcfCommandTaskHelper).getApplicationDetails(any(), any());

    doReturn(true).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    doReturn(applicationDetail).when(cfDeploymentManager).createRollingApplicationWithSteadyStateCheck(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRouteMappingCommandTaskHandlerNG.executeTaskInternal(
        cfRouteMappingRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenUnMappingSucceeds() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.RouteMapping, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
    String variableYamls = "\n"
        + "\n"
        + "APP_MEMORY: 350M\n"
        + "INSTANCES: 1\n"
        + "APP_NAME: rolling_rollback_locallyyyyw_rediessre";
    String manifestsYaml = "applications:\n"
        + "- name: ((APP_NAME))\n"
        + "  instances: ((INSTANCES))\n"
        + "  memory: ((APP_MEMORY))\n"
        + "  routes:\n"
        + "    - route: rishabh_basic_app_addt.apps.pcf-harness.com\n"
        + "  services:\n"
        + "    - myautoscaler";
    String autoscalarYaml = "---\n"
        + "instance_limits:\n"
        + "  min: 1\n"
        + "  max: 2\n"
        + "rules: []\n"
        + "scheduled_limit_changes:\n"
        + "- recurrence: 10\n"
        + "  executes_at: \"2032-01-01T00:00:00Z\"\n"
        + "  instance_limits:\n"
        + "    min: 1\n"
        + "    max: 2";
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(manifestsYaml)
                                                  .autoscalarManifestYml(autoscalarYaml)
                                                  .variableYmls(Arrays.asList(variableYamls))
                                                  .build();

    String appName = "app";
    String routes = "routes";

    CfRouteMappingRequestNG cfRouteMappingRequestNG = CfRouteMappingRequestNG.builder()
                                                          .tasInfraConfig(tasInfraConfig)
                                                          .timeoutIntervalInMin(5)
                                                          .applicationName(appName)
                                                          .attachRoutes(false)
                                                          .routes(Arrays.asList(routes))
                                                          .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRouteMappingRequestNG.getTimeoutIntervalInMin())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRouteMappingRequestNG.getCfCliVersion())
            .useCFCLI(cfRouteMappingRequestNG.isUseCfCLI())
            .applicationName(cfRouteMappingRequestNG.getApplicationName())
            .build();

    String name = "name";
    String id = "id";
    String url = "url";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(Arrays.asList(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

    int instances = 1;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(id)
                                              .diskQuota(1)
                                              .instances(instances)
                                              .memoryLimit(1)
                                              .name(name)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(instances)
                                              .build();
    doReturn(applicationDetail).when(pcfCommandTaskHelper).getApplicationDetails(any(), any());

    doReturn(true).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    doReturn(applicationDetail).when(cfDeploymentManager).createRollingApplicationWithSteadyStateCheck(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRouteMappingCommandTaskHandlerNG.executeTaskInternal(
        cfRouteMappingRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenNoPreviousReleases() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.RouteMapping, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
    String variableYamls = "\n"
        + "\n"
        + "APP_MEMORY: 350M\n"
        + "INSTANCES: 1\n"
        + "APP_NAME: rolling_rollback_locallyyyyw_rediessre";
    String manifestsYaml = "applications:\n"
        + "- name: ((APP_NAME))\n"
        + "  instances: ((INSTANCES))\n"
        + "  memory: ((APP_MEMORY))\n"
        + "  routes:\n"
        + "    - route: rishabh_basic_app_addt.apps.pcf-harness.com\n"
        + "  services:\n"
        + "    - myautoscaler";
    String autoscalarYaml = "---\n"
        + "instance_limits:\n"
        + "  min: 1\n"
        + "  max: 2\n"
        + "rules: []\n"
        + "scheduled_limit_changes:\n"
        + "- recurrence: 10\n"
        + "  executes_at: \"2032-01-01T00:00:00Z\"\n"
        + "  instance_limits:\n"
        + "    min: 1\n"
        + "    max: 2";
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(manifestsYaml)
                                                  .autoscalarManifestYml(autoscalarYaml)
                                                  .variableYmls(Arrays.asList(variableYamls))
                                                  .build();

    String appName = "app";
    String routes = "routes";

    CfRouteMappingRequestNG cfRouteMappingRequestNG = CfRouteMappingRequestNG.builder()
                                                          .tasInfraConfig(tasInfraConfig)
                                                          .timeoutIntervalInMin(5)
                                                          .applicationName(appName)
                                                          .attachRoutes(false)
                                                          .routes(Arrays.asList(routes))
                                                          .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRouteMappingRequestNG.getTimeoutIntervalInMin())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRouteMappingRequestNG.getCfCliVersion())
            .useCFCLI(cfRouteMappingRequestNG.isUseCfCLI())
            .applicationName(cfRouteMappingRequestNG.getApplicationName())
            .build();

    String name = "name";
    String id = "id";
    String url = "url";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(Arrays.asList()).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

    int instances = 1;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(id)
                                              .diskQuota(1)
                                              .instances(instances)
                                              .memoryLimit(1)
                                              .name(name)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(instances)
                                              .build();
    doReturn(applicationDetail).when(pcfCommandTaskHelper).getApplicationDetails(any(), any());

    doReturn(true).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    doReturn(applicationDetail).when(cfDeploymentManager).createRollingApplicationWithSteadyStateCheck(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRouteMappingCommandTaskHandlerNG.executeTaskInternal(
        cfRouteMappingRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenMappingFails() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.RouteMapping, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
    String variableYamls = "\n"
        + "\n"
        + "APP_MEMORY: 350M\n"
        + "INSTANCES: 1\n"
        + "APP_NAME: rolling_rollback_locallyyyyw_rediessre";
    String manifestsYaml = "applications:\n"
        + "- name: ((APP_NAME))\n"
        + "  instances: ((INSTANCES))\n"
        + "  memory: ((APP_MEMORY))\n"
        + "  routes:\n"
        + "    - route: rishabh_basic_app_addt.apps.pcf-harness.com\n"
        + "  services:\n"
        + "    - myautoscaler";
    String autoscalarYaml = "---\n"
        + "instance_limits:\n"
        + "  min: 1\n"
        + "  max: 2\n"
        + "rules: []\n"
        + "scheduled_limit_changes:\n"
        + "- recurrence: 10\n"
        + "  executes_at: \"2032-01-01T00:00:00Z\"\n"
        + "  instance_limits:\n"
        + "    min: 1\n"
        + "    max: 2";
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(manifestsYaml)
                                                  .autoscalarManifestYml(autoscalarYaml)
                                                  .variableYmls(Arrays.asList(variableYamls))
                                                  .build();

    String appName = "app";
    String routes = "routes";

    CfRouteMappingRequestNG cfRouteMappingRequestNG = CfRouteMappingRequestNG.builder()
                                                          .tasInfraConfig(tasInfraConfig)
                                                          .timeoutIntervalInMin(5)
                                                          .applicationName(appName)
                                                          .attachRoutes(true)
                                                          .routes(Arrays.asList(routes))
                                                          .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRouteMappingRequestNG.getTimeoutIntervalInMin())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRouteMappingRequestNG.getCfCliVersion())
            .useCFCLI(cfRouteMappingRequestNG.isUseCfCLI())
            .applicationName(cfRouteMappingRequestNG.getApplicationName())
            .build();

    String name = "name";
    String id = "id";
    String url = "url";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doThrow(new PivotalClientApiException("exception"))
        .when(cfDeploymentManager)
        .getPreviousReleasesForRolling(any(), any());

    int instances = 1;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(id)
                                              .diskQuota(1)
                                              .instances(instances)
                                              .memoryLimit(1)
                                              .name(name)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(instances)
                                              .build();
    doReturn(applicationDetail).when(pcfCommandTaskHelper).getApplicationDetails(any(), any());

    doReturn(true).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRouteMappingCommandTaskHandlerNG.executeTaskInternal(
        cfRouteMappingRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenUnMappingFails() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.RouteMapping, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
    String variableYamls = "\n"
        + "\n"
        + "APP_MEMORY: 350M\n"
        + "INSTANCES: 1\n"
        + "APP_NAME: rolling_rollback_locallyyyyw_rediessre";
    String manifestsYaml = "applications:\n"
        + "- name: ((APP_NAME))\n"
        + "  instances: ((INSTANCES))\n"
        + "  memory: ((APP_MEMORY))\n"
        + "  routes:\n"
        + "    - route: rishabh_basic_app_addt.apps.pcf-harness.com\n"
        + "  services:\n"
        + "    - myautoscaler";
    String autoscalarYaml = "---\n"
        + "instance_limits:\n"
        + "  min: 1\n"
        + "  max: 2\n"
        + "rules: []\n"
        + "scheduled_limit_changes:\n"
        + "- recurrence: 10\n"
        + "  executes_at: \"2032-01-01T00:00:00Z\"\n"
        + "  instance_limits:\n"
        + "    min: 1\n"
        + "    max: 2";
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(manifestsYaml)
                                                  .autoscalarManifestYml(autoscalarYaml)
                                                  .variableYmls(Arrays.asList(variableYamls))
                                                  .build();

    String appName = "app";
    String routes = "routes";

    CfRouteMappingRequestNG cfRouteMappingRequestNG = CfRouteMappingRequestNG.builder()
                                                          .tasInfraConfig(tasInfraConfig)
                                                          .timeoutIntervalInMin(5)
                                                          .applicationName(appName)
                                                          .attachRoutes(false)
                                                          .routes(Arrays.asList(routes))
                                                          .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRouteMappingRequestNG.getTimeoutIntervalInMin())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRouteMappingRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRouteMappingRequestNG.getCfCliVersion())
            .useCFCLI(cfRouteMappingRequestNG.isUseCfCLI())
            .applicationName(cfRouteMappingRequestNG.getApplicationName())
            .build();

    String name = "name";
    String id = "id";
    String url = "url";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doThrow(new PivotalClientApiException("exception"))
        .when(cfDeploymentManager)
        .getPreviousReleasesForRolling(any(), any());

    int instances = 1;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(id)
                                              .diskQuota(1)
                                              .instances(instances)
                                              .memoryLimit(1)
                                              .name(name)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(instances)
                                              .build();
    doReturn(applicationDetail).when(pcfCommandTaskHelper).getApplicationDetails(any(), any());

    doReturn(true).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRouteMappingCommandTaskHandlerNG.executeTaskInternal(
        cfRouteMappingRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}