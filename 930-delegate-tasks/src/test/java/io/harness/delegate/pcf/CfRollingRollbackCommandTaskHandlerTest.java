/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.pcf.CfCommandUnitConstants.Rollback;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.cf.artifact.TasArtifactCreds;
import io.harness.delegate.task.cf.artifact.TasRegistrySettingsAdapter;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.request.CfRollingDeployRequestNG;
import io.harness.delegate.task.pcf.request.CfRollingRollbackRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.File;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfRollingRollbackCommandTaskHandlerTest extends CategoryTest {
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
  @InjectMocks @Spy CfRollingRollbackCommandTaskHandlerNG cfRollingRollbackCommandTaskHandlerNG;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenException() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

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
                                                  .variableYmls(List.of(variableYamls))
                                                  .build();

    CfRollingDeployRequestNG cfRollingDeployRequestNG = CfRollingDeployRequestNG.builder()
                                                            .tasInfraConfig(tasInfraConfig)
                                                            .tasArtifactConfig(tasArtifactConfig)
                                                            .tasManifestsPackage(tasManifestsPackage)
                                                            .timeoutIntervalInMin(5)
                                                            .routeMaps(emptyList())
                                                            .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingDeployRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size())
        .isEqualTo(1);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo()
                   .getDeployedServiceInstances()
                   .get(0)
                   .getInstanceName())
        .isEqualTo(id);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenPackageArtifact() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

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
                                                  .variableYmls(List.of(variableYamls))
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .isFirstDeployment(false)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(cfDeploymentManager, times(1)).createRollingApplicationWithSteadyStateCheck(any(), any());
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenDockerArtifact() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String image = "image";
    String username1 = "username";
    String password1 = "password";
    TasContainerArtifactConfig tasContainerArtifactConfig = TasContainerArtifactConfig.builder().image(image).build();
    TasArtifactCreds tasArtifactCreds = TasArtifactCreds.builder().username(username1).password(password1).build();
    doReturn(tasArtifactCreds).when(tasRegistrySettingsAdapter).getContainerSettings(tasContainerArtifactConfig);
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
                                                  .variableYmls(List.of(variableYamls))
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasContainerArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionOnGettingPreviousReleases() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

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
                                                  .variableYmls(List.of(variableYamls))
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    doThrow(PivotalClientApiException.class).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size()).isZero();
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size()).isZero();
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenPreviousReleasesEmptyAndVarsFileAbsent() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
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
                                                  .variableYmls(emptyList())
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    doReturn(emptyList()).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size()).isZero();
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size())
        .isEqualTo(1);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo()
                   .getDeployedServiceInstances()
                   .get(0)
                   .getInstanceName())
        .isEqualTo(id);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenCurrentProdInfoIsNotNullButAutoscalerDisabled() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
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
                                                  .variableYmls(emptyList())
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    doReturn(applicationDetail).when(cfDeploymentManager).createRollingApplicationWithSteadyStateCheck(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenCurrentProdInfoIsNotNullButCheckForAutoScalerEnabledThrowsException()
      throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
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
                                                  .variableYmls(emptyList())
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    doThrow(PivotalClientApiException.class).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    doReturn(applicationDetail).when(cfDeploymentManager).createRollingApplicationWithSteadyStateCheck(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenUseAutoscalerIsEnabledInRequestBody() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    TasArtifactConfig tasArtifactConfig = TasPackageArtifactConfig.builder().build();
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
                                                  .variableYmls(emptyList())
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .useAppAutoScalar(true)
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    doThrow(PivotalClientApiException.class).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());

    File fileArtifact = new File("/gs");
    TasArtifactDownloadResponse tasArtifactDownloadResponse =
        TasArtifactDownloadResponse.builder().artifactFile(fileArtifact).build();
    doReturn(tasArtifactDownloadResponse).when(pcfCommandTaskHelper).downloadPackageArtifact(any(), any());

    doReturn(applicationDetail).when(cfDeploymentManager).createRollingApplicationWithSteadyStateCheck(any(), any());

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollingRollbackForFirstTime() throws Exception {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Rollback, true, commandUnitsProgress);

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
                                                  .variableYmls(List.of(variableYamls))
                                                  .build();

    CfRollingRollbackRequestNG cfRollingRollbackRequestNG = CfRollingRollbackRequestNG.builder()
                                                                .tasInfraConfig(tasInfraConfig)
                                                                .tasArtifactConfig(tasArtifactConfig)
                                                                .tasManifestsPackage(tasManifestsPackage)
                                                                .isFirstDeployment(true)
                                                                .timeoutIntervalInMin(5)
                                                                .routeMaps(emptyList())
                                                                .build();

    doReturn(file).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());

    doReturn("cfCliPath")
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollingRollbackRequestNG.getCfCliVersion());

    String name = "name";
    String id = "id";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id(id)
                                                .diskQuota(1)
                                                .instances(0)
                                                .memoryLimit(1)
                                                .name(name)
                                                .requestedState(STOPPED)
                                                .runningInstances(0)
                                                .build();
    doReturn(List.of(applicationSummary)).when(cfDeploymentManager).getPreviousReleasesForRolling(any(), any());

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

    LogCallback logCallback = mock(LogCallback.class);
    doReturn(logCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(eq(logStreamingTaskClient), eq(Rollback), eq(true), eq(commandUnitsProgress));

    CfCommandResponseNG cfCommandExecutionResponse = cfRollingRollbackCommandTaskHandlerNG.executeTaskInternal(
        cfRollingRollbackRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ArgumentCaptor<String> logCallbackCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback, times(2)).saveExecutionLog(logCallbackCaptor.capture(), any(), any());
    List<String> allValues = logCallbackCaptor.getAllValues();
    assertThat(allValues.size()).isEqualTo(2);
    assertThat(allValues.get(0)).contains("This is first rolling deployment hence skipping rollback");
    assertThat(allValues.get(1)).contains("PCF Rolling Rollback completed successfully");
    verify(cfDeploymentManager, times(0)).createRollingApplicationWithSteadyStateCheck(any(), any());
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesBefore().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().size())
        .isEqualTo(1);
    assertThat(
        cfCommandExecutionResponse.getStepExecutionInstanceInfo().getServiceInstancesAfter().get(0).getInstanceName())
        .isEqualTo(id);
    assertThat(cfCommandExecutionResponse.getStepExecutionInstanceInfo().getDeployedServiceInstances().size()).isZero();
  }
}
