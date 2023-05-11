/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.pcf.pcftaskhandler;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.RISHABH;

import static software.wings.delegatetasks.pcf.PcfTestConstants.ACCOUNT_ID;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML;
import static software.wings.delegatetasks.pcf.PcfTestConstants.ORG;
import static software.wings.delegatetasks.pcf.PcfTestConstants.RUNNING;
import static software.wings.delegatetasks.pcf.PcfTestConstants.SPACE;
import static software.wings.delegatetasks.pcf.PcfTestConstants.STOPPED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.getPcfConfig;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.cf.CfTestConstants;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfSetupCommandResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfManifestFileData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.pcf.PcfCommandTaskHelper;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;
import software.wings.service.intfc.security.EncryptionService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class PcfSetupCommandTaskHandlerTest extends WingsBaseTest {
  @Mock EncryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock CfDeploymentManager pcfDeploymentManager;
  @Mock SecretDecryptionService secretDecryptionService;

  @InjectMocks @Spy PcfCommandTaskHelper pcfCommandTaskHelper;
  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @InjectMocks PcfSetupCommandTaskHandler pcfSetupCommandTaskHandler;

  @Test
  @Owner(developers = {ADWAIT, IVAN})
  @Category(UnitTests.class)
  public void testPerformSetup() throws PivotalClientApiException, IOException, ExecutionException {
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().metadataOnly(false).build();

    CfCommandRequest cfCommandRequest = CfCommandSetupRequest.builder()
                                            .pcfCommandType(CfCommandRequest.PcfCommandType.SETUP)
                                            .pcfConfig(getPcfConfig())
                                            .artifactFiles(Collections.emptyList())
                                            .artifactStreamAttributes(artifactStreamAttributes)
                                            .manifestYaml(MANIFEST_YAML)
                                            .organization(ORG)
                                            .space(SPACE)
                                            .accountId(ACCOUNT_ID)
                                            .routeMaps(Arrays.asList("ab.rc", "ab.ty/asd"))
                                            .timeoutIntervalInMin(5)
                                            .releaseNamePrefix("a_s_e")
                                            .olderActiveVersionCountToKeep(2)
                                            .maxCount(2)
                                            .build();

    // mocking
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__1")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__2")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__3")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__4")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__5")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());
    doReturn(previousReleases).when(pcfDeploymentManager).getDeployedServicesWithNonZeroInstances(any(), anyString());
    doNothing().when(pcfDeploymentManager).unmapRouteMapForApplication(any(), anyList(), any());
    doReturn("PASSWORD".toCharArray()).when(pcfCommandTaskHelper).getPassword(any());
    doReturn(CfInternalConfig.builder().build()).when(secretDecryptionService).decrypt(any(), any(), anyBoolean());
    doNothing().when(pcfDeploymentManager).deleteApplication(any());
    ApplicationDetail appDetails = ApplicationDetail.builder()
                                       .id("10")
                                       .diskQuota(1)
                                       .instances(0)
                                       .memoryLimit(1)
                                       .name("a_s_e__4")
                                       .requestedState(STOPPED)
                                       .stack("")
                                       .runningInstances(0)
                                       .urls(Arrays.asList("1.com", "2.com"))
                                       .build();
    doReturn(appDetails).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(appDetails).when(pcfDeploymentManager).resizeApplication(any());
    File f1 = new File("./test1");
    File f2 = new File("./test2");
    doReturn(f1).when(pcfCommandTaskHelper).downloadArtifactFromManager(any(), any(), any());
    doReturn(f2).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());
    doNothing().when(pcfCommandTaskBaseHelper).deleteCreatedFile(anyList());

    doReturn(ApplicationDetail.builder()
                 .id("10")
                 .diskQuota(1)
                 .instances(0)
                 .memoryLimit(1)
                 .name("a_s_e__6")
                 .requestedState(STOPPED)
                 .stack("")
                 .runningInstances(0)
                 .build())
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    verify(pcfDeploymentManager, times(1)).createApplication(any(), any());
    verify(pcfDeploymentManager, times(3)).deleteApplication(any());
    verify(pcfDeploymentManager, times(1)).resizeApplication(any());
    verify(pcfDeploymentManager, times(1)).unmapRouteMapForApplication(any(), anyList(), any());

    CfSetupCommandResponse cfSetupCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(CommandExecutionStatus.SUCCESS).isEqualTo(cfCommandExecutionResponse.getCommandExecutionStatus());
    assertThat(cfSetupCommandResponse.getNewApplicationDetails()).isNotNull();
    assertThat("a_s_e__6").isEqualTo(cfSetupCommandResponse.getNewApplicationDetails().getApplicationName());
    assertThat("10").isEqualTo(cfSetupCommandResponse.getNewApplicationDetails().getApplicationGuid());

    assertThat(cfSetupCommandResponse.getDownsizeDetails()).isNotNull();
    assertThat(cfSetupCommandResponse.getDownsizeDetails()).hasSize(1);
    Set<String> appsToBeDownsized = new HashSet<>(cfSetupCommandResponse.getDownsizeDetails()
                                                      .stream()
                                                      .map(CfAppSetupTimeDetails::getApplicationName)
                                                      .collect(toList()));
    assertThat(appsToBeDownsized.contains("a_s_e__4")).isTrue();
  }

  @NotNull
  private ApplicationDetail getApplicationByName() {
    return ApplicationDetail.builder()
        .id("10")
        .diskQuota(1)
        .instances(1)
        .memoryLimit(1)
        .name("app1")
        .requestedState("STOPPED")
        .stack("")
        .runningInstances(1)
        .instanceDetails(Collections.singletonList(InstanceDetail.builder()
                                                       .cpu(1.0)
                                                       .diskQuota((long) 1.23)
                                                       .diskUsage((long) 1.23)
                                                       .index("2")
                                                       .memoryQuota((long) 1)
                                                       .memoryUsage((long) 1)
                                                       .build()))
        .diskQuota(1)
        .instances(1)
        .memoryLimit(1)
        .name("app1")
        .requestedState(CfTestConstants.RUNNING)
        .stack("")
        .urls("prodRoute.com")
        .runningInstances(1)
        .build();
  }

  @Test
  @Owner(developers = {ADWAIT, IVAN})
  @Category(UnitTests.class)
  public void testCheckIfVarsFilePresent() {
    PcfManifestsPackage manifestsPackage = PcfManifestsPackage.builder().build();
    CfCommandSetupRequest setupRequest = CfCommandSetupRequest.builder().pcfManifestsPackage(manifestsPackage).build();
    assertThat(pcfSetupCommandTaskHandler.checkIfVarsFilePresent(setupRequest)).isFalse();

    manifestsPackage.setVariableYmls(emptyList());
    assertThat(pcfSetupCommandTaskHandler.checkIfVarsFilePresent(setupRequest)).isFalse();

    String str = null;
    manifestsPackage.setVariableYmls(Arrays.asList(str));
    assertThat(pcfSetupCommandTaskHandler.checkIfVarsFilePresent(setupRequest)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPrepareVarsYamlFile() throws Exception {
    File f1 = mock(File.class);
    File f2 = mock(File.class);
    doReturn(f1)
        .doReturn(f2)
        .when(pcfCommandTaskBaseHelper)
        .createManifestVarsYamlFileLocally(any(), anyString(), anyInt());

    CfCommandSetupRequest setupRequest =
        CfCommandSetupRequest.builder()
            .pcfManifestsPackage(PcfManifestsPackage.builder().variableYmls(Arrays.asList("a:b", "c:d")).build())
            .releaseNamePrefix("abc")
            .build();
    CfCreateApplicationRequestData requestData =
        CfCreateApplicationRequestData.builder()
            .password("ABCD".toCharArray())
            .varsYmlFilePresent(true)
            .pcfManifestFileData(CfManifestFileData.builder().varFiles(new ArrayList<>()).build())
            .build();

    pcfSetupCommandTaskHandler.prepareVarsYamlFile(requestData, setupRequest);

    assertThat(requestData.getPcfManifestFileData()).isNotNull();
    assertThat(requestData.getPcfManifestFileData().getVarFiles()).isNotEmpty();
    assertThat(requestData.getPcfManifestFileData().getVarFiles().size()).isEqualTo(2);
    assertThat(requestData.getPcfManifestFileData().getVarFiles()).containsExactly(f1, f2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testDownsizeApplicationToZero() throws Exception {
    reset(pcfDeploymentManager);
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .name("a_s_e__1")
                                                .diskQuota(1)
                                                .requestedState(RUNNING)
                                                .id("10")
                                                .instances(0)
                                                .memoryLimit(1)
                                                .runningInstances(0)
                                                .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(applicationDetail).when(pcfDeploymentManager).resizeApplication(any());

    CfCommandSetupRequest cfCommandSetupRequest = CfCommandSetupRequest.builder().useAppAutoscalar(true).build();

    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData =
        CfAppAutoscalarRequestData.builder().configPathVar("path").build();

    doReturn(true).when(pcfCommandTaskBaseHelper).disableAutoscalar(any(), any());
    ArgumentCaptor<CfAppAutoscalarRequestData> argumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    pcfSetupCommandTaskHandler.downsizeApplicationToZero(applicationSummary, CfRequestConfig.builder().build(),
        cfCommandSetupRequest, pcfAppAutoscalarRequestData, executionLogCallback);

    verify(pcfCommandTaskBaseHelper, times(1)).disableAutoscalar(argumentCaptor.capture(), any());
    pcfAppAutoscalarRequestData = argumentCaptor.getValue();
    assertThat(pcfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("10");
    assertThat(pcfAppAutoscalarRequestData.getApplicationName()).isEqualTo("a_s_e__1");
    assertThat(pcfAppAutoscalarRequestData.isExpectedEnabled()).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownsizeApplicationToZeroFailAutoscaler() throws Exception {
    reset(pcfDeploymentManager);
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .name("a_s_e__1")
                                                .diskQuota(1)
                                                .requestedState(RUNNING)
                                                .id("10")
                                                .instances(0)
                                                .memoryLimit(1)
                                                .runningInstances(0)
                                                .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(applicationDetail).when(pcfDeploymentManager).resizeApplication(any());

    CfCommandSetupRequest cfCommandSetupRequest = CfCommandSetupRequest.builder().useAppAutoscalar(true).build();

    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData =
        CfAppAutoscalarRequestData.builder().configPathVar("path").build();

    doThrow(new PivotalClientApiException("#Throwing exception to test if flow stops or not"))
        .when(pcfCommandTaskBaseHelper)
        .disableAutoscalar(any(), any());

    ArgumentCaptor<CfAppAutoscalarRequestData> argumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    pcfSetupCommandTaskHandler.downsizeApplicationToZero(applicationSummary, CfRequestConfig.builder().build(),
        cfCommandSetupRequest, pcfAppAutoscalarRequestData, executionLogCallback);

    verify(pcfCommandTaskBaseHelper, times(1)).disableAutoscalar(argumentCaptor.capture(), any());
    verify(pcfCommandTaskBaseHelper, times(1)).disableAutoscalarSafe(argumentCaptor.capture(), any());
    verify(pcfDeploymentManager, times(0)).changeAutoscalarState(any(), any(), anyBoolean());

    pcfAppAutoscalarRequestData = argumentCaptor.getValue();
    assertThat(pcfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("10");
    assertThat(pcfAppAutoscalarRequestData.getApplicationName()).isEqualTo("a_s_e__1");
    assertThat(pcfAppAutoscalarRequestData.isExpectedEnabled()).isTrue();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToVersionBasicCanaryDisableFF()
      throws PivotalClientApiException, IOException, ExecutionException {
    testVersionToVersionBasicCanary(false);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToVersionBasicCanaryEnableFF()
      throws PivotalClientApiException, IOException, ExecutionException {
    testVersionToVersionBasicCanary(true);
  }

  // for version -> version deployment there should not be any effect of feature flag
  private void testVersionToVersionBasicCanary(boolean featureFlag)
      throws PivotalClientApiException, IOException, ExecutionException {
    final String releaseName = "PaymentApp";
    final String activeAppName = releaseName + "__3";
    final String inActiveAppName = releaseName + "__2";

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppName, inActiveAppName);

    mockSetupBehaviour(releaseName, previousReleases);

    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, false, featureFlag, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    // since version changed did not happen so resetState() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .resetState(eq(previousReleases), any(ApplicationSummary.class), any(ApplicationSummary.class), anyString(),
            any(CfRequestConfig.class), anyBoolean(), any(Deque.class), anyInt(), any(LogCallback.class), any());

    // since we are in version -> version deployment mode, renameApp() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .renameApp(any(ApplicationSummary.class), any(CfRequestConfig.class), eq(executionLogCallback), anyString());

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy()).isEqualTo(AppNamingStrategy.VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isFalse();
    assertThat(pcfCommandResponse.isVersioningChanged()).isFalse();
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveApplication.getName());
    // since this is version -> version, this variable should not be initialized as it not required
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToNonVersionBasicCanary() throws PivotalClientApiException, IOException, ExecutionException {
    final String releaseName = "PaymentApp";
    final String inActiveAppName = releaseName + "__2";
    final String activeAppName = releaseName + "__3";
    final String inActiveAppAfterRename = releaseName + "__2";
    final String activeAppAfterRename = releaseName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppName, inActiveAppName);

    mockSetupBehaviour(releaseName, previousReleases);

    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenAnswer(new Answer<List<ApplicationSummary>>() {
          private int count = 0;
          @Override
          public List<ApplicationSummary> answer(InvocationOnMock invocationOnMock) {
            count++;
            if (count <= 2) {
              return previousReleases;
            } else if (count == 3) {
              return getPreviousReleasesAfterVersionChangedRenaming(previousReleases);
            }
            return getPreviousReleasesAfterPreDeploymentRenaming(
                previousReleases, inActiveAppAfterRename, activeAppAfterRename);
          }
        });

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    ArgumentCaptor<ApplicationSummary> activeApplicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<ApplicationSummary> inActiveApplicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> releaseNamePrefix = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Boolean> nonVersion = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Integer> activeAppVersion = ArgumentCaptor.forClass(Integer.class);

    verify(pcfCommandTaskBaseHelper, times(1))
        .resetState(eq(previousReleases), activeApplicationCaptor.capture(), inActiveApplicationCaptor.capture(),
            releaseNamePrefix.capture(), any(CfRequestConfig.class), nonVersion.capture(), any(Deque.class),
            activeAppVersion.capture(), any(), any());

    assertThat(activeApplicationCaptor.getValue().getName()).isEqualTo(activeAppName);
    assertThat(inActiveApplicationCaptor.getValue().getName()).isEqualTo(inActiveAppName);
    assertThat(releaseNamePrefix.getValue()).isEqualTo(releaseName);
    assertThat(nonVersion.getValue()).isTrue();
    assertThat(activeAppVersion.getValue()).isEqualTo(-1);

    assertAppRenamingVersionToNonVersion(releaseName, activeApplication, inActiveApplication);
    assertSetupResponse(cfCommandExecutionResponse, inActiveAppAfterRename, activeAppAfterRename, inActiveApplication,
        activeApplication);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToNonVersionBasicCanary()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + "__INACTIVE";
    final String inActiveAppNameAfterRename = releaseName + "__2";
    final String activeAppNameAfterRename = inActiveAppCurrentName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(releaseName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);

    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenAnswer(new Answer<List<ApplicationSummary>>() {
          private int count = 0;
          @Override
          public List<ApplicationSummary> answer(InvocationOnMock invocationOnMock) {
            count++;
            if (count <= 2) {
              return previousReleases;
            }
            return getPreviousReleasesAfterPreDeploymentRenaming(
                previousReleases, inActiveAppNameAfterRename, activeAppNameAfterRename);
          }
        });

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    // since version changed did not happen so resetState() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .resetState(eq(previousReleases), any(ApplicationSummary.class), any(ApplicationSummary.class), anyString(),
            any(CfRequestConfig.class), anyBoolean(), any(Deque.class), anyInt(), any(LogCallback.class), any());

    ArgumentCaptor<ApplicationSummary> renamedAppCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskBaseHelper, times(2))
        .renameApp(
            renamedAppCaptor.capture(), any(CfRequestConfig.class), eq(executionLogCallback), newNameCaptor.capture());

    List<ApplicationSummary> allRenamedApps = renamedAppCaptor.getAllValues();
    List<String> allNewNames = newNameCaptor.getAllValues();
    assertThat(allRenamedApps.size()).isEqualTo(2);
    assertThat(allNewNames.size()).isEqualTo(2);

    // renaming due to new deployment in non-version to non-version mode
    assertThat(allRenamedApps.get(0).getId()).isEqualTo(inActiveApplication.getId());
    assertThat(allNewNames.get(0)).isEqualTo(inActiveAppNameAfterRename);
    assertThat(allRenamedApps.get(1).getId()).isEqualTo(activeApplication.getId());
    assertThat(allNewNames.get(1)).isEqualTo(activeAppNameAfterRename);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();

    // assert data required for rollbacks
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy())
        .isEqualTo(AppNamingStrategy.APP_NAME_WITH_VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isTrue();
    assertThat(pcfCommandResponse.isVersioningChanged()).isFalse();
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveAppNameAfterRename);
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeAppNameAfterRename);
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToVersionBasicCanary() throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + "__INACTIVE";
    final String inActiveAppNameAfterRename = releaseName + "__2";
    final String activeAppNameAfterRename = releaseName + "__3";

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(releaseName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);

    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenAnswer(new Answer<List<ApplicationSummary>>() {
          private int count = 0;
          @Override
          public List<ApplicationSummary> answer(InvocationOnMock invocationOnMock) {
            count++;
            if (count <= 2) {
              return previousReleases;
            }
            return getPreviousReleasesAfterPreDeploymentRenaming(
                previousReleases, inActiveAppNameAfterRename, activeAppNameAfterRename);
          }
        });

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, false, true, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    ArgumentCaptor<ApplicationSummary> activeApplicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<ApplicationSummary> inActiveApplicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> releaseNamePrefix = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Boolean> nonVersion = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Integer> activeAppVersion = ArgumentCaptor.forClass(Integer.class);

    verify(pcfCommandTaskBaseHelper, times(1))
        .resetState(eq(previousReleases), activeApplicationCaptor.capture(), inActiveApplicationCaptor.capture(),
            releaseNamePrefix.capture(), any(CfRequestConfig.class), nonVersion.capture(), any(Deque.class),
            activeAppVersion.capture(), any(), any());

    assertThat(activeApplicationCaptor.getValue().getName()).isEqualTo(releaseName);
    assertThat(inActiveApplicationCaptor.getValue().getName()).isEqualTo(inActiveAppCurrentName);
    assertThat(releaseNamePrefix.getValue()).isEqualTo(releaseName);
    assertThat(nonVersion.getValue()).isFalse();
    assertThat(activeAppVersion.getValue()).isEqualTo(-1);

    ArgumentCaptor<ApplicationSummary> renamedAppCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskBaseHelper, times(2))
        .renameApp(
            renamedAppCaptor.capture(), any(CfRequestConfig.class), eq(executionLogCallback), newNameCaptor.capture());

    List<ApplicationSummary> allRenamedApps = renamedAppCaptor.getAllValues();
    List<String> allNewNames = newNameCaptor.getAllValues();
    assertThat(allRenamedApps.size()).isEqualTo(2);
    assertThat(allNewNames.size()).isEqualTo(2);

    // renaming due to version change : non-version --> version
    assertThat(allRenamedApps.get(0).getId()).isEqualTo(activeApplication.getId());
    assertThat(allNewNames.get(0)).isEqualTo(activeAppNameAfterRename);
    assertThat(allRenamedApps.get(1).getId()).isEqualTo(inActiveApplication.getId());
    assertThat(allNewNames.get(1)).isEqualTo(inActiveAppNameAfterRename);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfCommandResponse.getExistingAppNamingStrategy())
        .isEqualTo(AppNamingStrategy.APP_NAME_WITH_VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isFalse();
    assertThat(pcfCommandResponse.isVersioningChanged()).isTrue();
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveAppNameAfterRename);
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());

    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeAppNameAfterRename);
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToVersionAppRenamingBlueGreen()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + "__2";
    final String activeAppCurrentName = releaseName + "__3";

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);
    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());
    doReturn(CfAppSetupTimeDetails.builder().build())
        .when(pcfCommandTaskBaseHelper)
        .renameInActiveAppDuringBGDeployment(
            eq(previousReleases), any(), eq(releaseName), eq(executionLogCallback), any(), any());

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, false, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    // since its blue green resetState() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .resetState(eq(previousReleases), any(ApplicationSummary.class), any(ApplicationSummary.class), anyString(),
            any(CfRequestConfig.class), anyBoolean(), any(Deque.class), anyInt(), any(LogCallback.class), any());

    verify(pcfCommandTaskBaseHelper, times(1))
        .renameInActiveAppDuringBGDeployment(eq(previousReleases), any(CfRequestConfig.class), eq(releaseName),
            eq(executionLogCallback), eq(AppNamingStrategy.VERSIONING.name()), any());

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy()).isEqualTo(AppNamingStrategy.VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isFalse();
    assertThat(pcfCommandResponse.isVersioningChanged()).isFalse();
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveApplication.getName());
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);
    assertThat(mostRecentInactiveAppVersion.getOldName()).isEqualTo(inActiveAppCurrentName);

    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeApplication.getName());
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    CfCreateApplicationRequestData requestData = requestDataCaptor.getValue();
    assertThat(requestData).isNotNull();
    assertThat(requestData.getNewReleaseName()).isEqualTo(releaseName + PcfConstants.DELIMITER + "4");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToNonVersionAppRenamingBlueGreen()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + "__2";
    final String activeAppCurrentName = releaseName + "__3";

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);
    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());
    doReturn(CfAppSetupTimeDetails.builder().build())
        .when(pcfCommandTaskBaseHelper)
        .renameInActiveAppDuringBGDeployment(
            eq(previousReleases), any(), eq(releaseName), eq(executionLogCallback), any(), any());

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    // since its blue green resetState() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .resetState(eq(previousReleases), any(ApplicationSummary.class), any(ApplicationSummary.class), anyString(),
            any(CfRequestConfig.class), anyBoolean(), any(Deque.class), anyInt(), any(LogCallback.class), any());

    verify(pcfCommandTaskBaseHelper, times(1))
        .renameInActiveAppDuringBGDeployment(eq(previousReleases), any(CfRequestConfig.class), eq(releaseName),
            eq(executionLogCallback), eq(AppNamingStrategy.VERSIONING.name()), any());

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy()).isEqualTo(AppNamingStrategy.VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isTrue();
    assertThat(pcfCommandResponse.isVersioningChanged()).isTrue();
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveApplication.getName());
    assertThat(mostRecentInactiveAppVersion.getOldName()).isEqualTo(inActiveAppCurrentName);
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);

    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeApplication.getName());
    assertThat(activeAppDetails.get(0).getOldName()).isEqualTo(activeApplication.getName());
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    CfCreateApplicationRequestData requestData = requestDataCaptor.getValue();
    assertThat(requestData).isNotNull();
    assertThat(requestData.getNewReleaseName()).isEqualTo(releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToNonVersionAppRenamingBlueGreen()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    final String activeAppCurrentName = releaseName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);
    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());
    doReturn(CfAppSetupTimeDetails.builder().oldName(inActiveAppCurrentName).build())
        .when(pcfCommandTaskBaseHelper)
        .renameInActiveAppDuringBGDeployment(
            eq(previousReleases), any(), eq(releaseName), eq(executionLogCallback), any(), any());

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    // since its blue green resetState() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .resetState(eq(previousReleases), any(ApplicationSummary.class), any(ApplicationSummary.class), anyString(),
            any(CfRequestConfig.class), anyBoolean(), any(Deque.class), anyInt(), any(LogCallback.class), any());

    verify(pcfCommandTaskBaseHelper, times(1))
        .renameInActiveAppDuringBGDeployment(eq(previousReleases), any(CfRequestConfig.class), eq(releaseName),
            eq(executionLogCallback), eq(AppNamingStrategy.APP_NAME_WITH_VERSIONING.name()), any());

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy())
        .isEqualTo(AppNamingStrategy.APP_NAME_WITH_VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isTrue();
    assertThat(pcfCommandResponse.isVersioningChanged()).isFalse();
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveApplication.getName());
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);
    assertThat(mostRecentInactiveAppVersion.getOldName()).isEqualTo(inActiveAppCurrentName);

    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeApplication.getName());
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    CfCreateApplicationRequestData requestData = requestDataCaptor.getValue();
    assertThat(requestData).isNotNull();
    assertThat(requestData.getNewReleaseName()).isEqualTo(releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonVersionAppCreationFailedBlueGreen()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    final String activeAppCurrentName = releaseName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    List<ApplicationSummary> previousReleasesAfterDeployment =
        getPreviousReleasesAfterDeployment(activeAppCurrentName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);
    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenReturn(previousReleases, previousReleasesAfterDeployment, previousReleases);

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doThrow(new PivotalClientApiException("#Throwing sample exception to fail creation"))
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    verify(pcfDeploymentManager, times(2)).renameApplication(any(), any());
    verify(pcfDeploymentManager, times(1)).deleteApplication(any());
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonVersionBlueGreenAppCreationFailedFirstDeploy()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String newReleaseName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    List<ApplicationSummary> previousReleasesAfterDeployment = new ArrayList<>();
    previousReleasesAfterDeployment.add(ApplicationSummary.builder()
                                            .name(newReleaseName)
                                            .diskQuota(1)
                                            .requestedState(STOPPED)
                                            .id("1")
                                            .instances(1)
                                            .memoryLimit(1)
                                            .runningInstances(2)
                                            .build());

    mockSetupBehaviour(releaseName, previousReleases);
    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenReturn(previousReleases, previousReleasesAfterDeployment, previousReleases);

    final ApplicationSummary activeApplication = null;

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doThrow(new PivotalClientApiException("#Throwing sample exception while creating application"))
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    verify(pcfDeploymentManager, times(0)).renameApplication(any(), any());
    verify(pcfDeploymentManager, times(0)).deleteApplication(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonVersionBlueGreenAppCreationFailedSecondDeploy()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String newReleaseName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(releaseName)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());

    List<ApplicationSummary> previousReleasesAfterDeployment = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(releaseName)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());

    previousReleasesAfterDeployment.add(ApplicationSummary.builder()
                                            .name(newReleaseName)
                                            .diskQuota(1)
                                            .requestedState(STOPPED)
                                            .id("2")
                                            .instances(1)
                                            .memoryLimit(1)
                                            .runningInstances(2)
                                            .build());

    previousReleases.add(ApplicationSummary.builder()
                             .name(releaseName)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());

    mockSetupBehaviour(releaseName, previousReleases);
    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenReturn(previousReleases, previousReleasesAfterDeployment, previousReleases);

    final ApplicationSummary activeApplication = previousReleases.get(0);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doThrow(new PivotalClientApiException("#Throwing sample exception while creating application"))
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    verify(pcfDeploymentManager, times(0)).renameApplication(any(), any());
    verify(pcfDeploymentManager, times(0)).deleteApplication(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonVersionAppCreationFailedBasic() throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    final String inActiveAppAfterRename = releaseName + "__2";
    final String activeAppCurrentName = releaseName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    List<ApplicationSummary> previousReleasesAfterDeployment =
        getPreviousReleasesAfterDeployment(inActiveAppCurrentName, inActiveAppAfterRename);

    mockSetupBehaviour(releaseName, previousReleases);
    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenReturn(previousReleases, previousReleasesAfterDeployment, previousReleases);

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doThrow(new PivotalClientApiException("#Throwing exception before creating the new application"))
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    verify(pcfDeploymentManager, times(4)).renameApplication(any(), any());
    verify(pcfDeploymentManager, times(1)).deleteApplication(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonVersionBasicAppCreationFailedFirstDeploy()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String newReleaseName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    List<ApplicationSummary> previousReleasesAfterDeployment = new ArrayList<>();
    previousReleasesAfterDeployment.add(ApplicationSummary.builder()
                                            .name(newReleaseName)
                                            .diskQuota(1)
                                            .requestedState(STOPPED)
                                            .id("1")
                                            .instances(1)
                                            .memoryLimit(1)
                                            .runningInstances(2)
                                            .build());

    mockSetupBehaviour(releaseName, previousReleases);
    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenReturn(previousReleases, previousReleasesAfterDeployment, previousReleases);

    final ApplicationSummary activeApplication = null;

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doThrow(new PivotalClientApiException("#Throwing sample exception while creating application"))
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    verify(pcfDeploymentManager, times(0)).renameApplication(any(), any());
    verify(pcfDeploymentManager, times(0)).deleteApplication(any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonVersionBasicAppCreationFailedSecondDeploy()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String activeAppNameAfterRename = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(releaseName)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());

    List<ApplicationSummary> previousReleasesAfterDeployment = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(activeAppNameAfterRename)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());

    previousReleasesAfterDeployment.add(ApplicationSummary.builder()
                                            .name(releaseName)
                                            .diskQuota(1)
                                            .requestedState(STOPPED)
                                            .id("2")
                                            .instances(1)
                                            .memoryLimit(1)
                                            .runningInstances(2)
                                            .build());

    mockSetupBehaviour(releaseName, previousReleases);
    when(pcfDeploymentManager.getPreviousReleases(any(), anyString()))
        .thenReturn(previousReleases, previousReleasesAfterDeployment, previousReleases);

    final ApplicationSummary activeApplication = previousReleases.get(0);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(null)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    doThrow(new PivotalClientApiException("#Throwing sample exception while creating application"))
        .when(pcfDeploymentManager)
        .createApplication(any(), any());

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, true, true, false);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    verify(pcfDeploymentManager, times(2)).renameApplication(any(), any());
    verify(pcfDeploymentManager, times(1)).deleteApplication(any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToVersionAppRenamingBlueGreen()
      throws ExecutionException, PivotalClientApiException, IOException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    final String activeAppCurrentName = releaseName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    mockSetupBehaviour(releaseName, previousReleases);
    doReturn(previousReleases).when(pcfDeploymentManager).getPreviousReleases(any(), anyString());
    doReturn(CfAppSetupTimeDetails.builder().oldName(inActiveAppCurrentName).build())
        .when(pcfCommandTaskBaseHelper)
        .renameInActiveAppDuringBGDeployment(
            eq(previousReleases), any(), eq(releaseName), eq(executionLogCallback), any(), any());

    final ApplicationSummary activeApplication = previousReleases.get(2);
    final ApplicationSummary inActiveApplication = previousReleases.get(1);

    doReturn(activeApplication)
        .when(pcfCommandTaskBaseHelper)
        .findActiveApplication(
            eq(executionLogCallback), anyBoolean(), any(CfRequestConfig.class), eq(previousReleases));

    doReturn(inActiveApplication)
        .when(pcfCommandTaskBaseHelper)
        .getMostRecentInactiveApplication(eq(executionLogCallback), anyBoolean(), eq(activeApplication),
            eq(previousReleases), any(CfRequestConfig.class));

    CfCommandSetupRequest setupRequest = getSetupRequest(releaseName, false, true, true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfSetupCommandTaskHandler.executeTaskInternal(setupRequest, null, logStreamingTaskClient, false);

    // since its blue green resetState() should not be called
    verify(pcfCommandTaskBaseHelper, times(0))
        .resetState(eq(previousReleases), any(ApplicationSummary.class), any(ApplicationSummary.class), anyString(),
            any(CfRequestConfig.class), anyBoolean(), any(Deque.class), anyInt(), any(LogCallback.class), any());

    verify(pcfCommandTaskBaseHelper, times(1))
        .renameInActiveAppDuringBGDeployment(eq(previousReleases), any(CfRequestConfig.class), eq(releaseName),
            eq(executionLogCallback), eq(AppNamingStrategy.APP_NAME_WITH_VERSIONING.name()), any());

    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy())
        .isEqualTo(AppNamingStrategy.APP_NAME_WITH_VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isFalse();
    assertThat(pcfCommandResponse.isVersioningChanged()).isTrue();
    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveApplication.getName());
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(-1);
    assertThat(mostRecentInactiveAppVersion.getOldName()).isEqualTo(inActiveAppCurrentName);

    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeApplication.getName());
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());

    ArgumentCaptor<CfCreateApplicationRequestData> requestDataCaptor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(pcfDeploymentManager, times(1)).createApplication(requestDataCaptor.capture(), eq(executionLogCallback));
    CfCreateApplicationRequestData requestData = requestDataCaptor.getValue();
    assertThat(requestData).isNotNull();
    assertThat(requestData.getNewReleaseName()).isEqualTo(releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVerifyMaxVersionToKeep() throws PivotalClientApiException {
    List<ApplicationSummary> previousReleases = getPreviousReleases();
    ApplicationSummary inActiveApplication = previousReleases.get(3);
    ApplicationSummary activeApplication = previousReleases.get(4);
    CfAppSetupTimeDetails inActiveApplicationDetails =
        CfAppSetupTimeDetails.builder()
            .applicationGuid(inActiveApplication.getId())
            .applicationName(inActiveApplication.getName())
            .initialInstanceCount(inActiveApplication.getRunningInstances())
            .urls(new ArrayList<>(inActiveApplication.getUrls()))
            .build();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder().build();
    CfCommandSetupRequest setupRequest = getSetupRequest("PaymentApp", false, false, false);
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("PaymentApp__1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(applicationDetail).when(pcfDeploymentManager).resizeApplication(eq(cfRequestConfig));
    ArgumentCaptor<String> appDeletedMsgCaptor = ArgumentCaptor.forClass(String.class);

    pcfSetupCommandTaskHandler.deleteOlderApplications(previousReleases, cfRequestConfig, setupRequest,
        cfAppAutoscalarRequestData, activeApplication, inActiveApplicationDetails, executionLogCallback);
    verify(pcfDeploymentManager, times(2)).deleteApplication(eq(cfRequestConfig));
    verify(executionLogCallback, times(7)).saveExecutionLog(appDeletedMsgCaptor.capture());
    List<String> appDeletedMsgCaptorAllValues = appDeletedMsgCaptor.getAllValues();
    assertThat(appDeletedMsgCaptorAllValues.get(3))
        .isEqualTo("# Older application being deleted: \u001B[0;96m\u001B[40mPaymentApp__2#==#");
    assertThat(appDeletedMsgCaptorAllValues.get(4))
        .isEqualTo("# Older application being deleted: \u001B[0;96m\u001B[40mPaymentApp__1#==#");
    assertThat(appDeletedMsgCaptorAllValues.get(5))
        .isEqualTo("# Done Deleting older applications. Deleted Total 2 applications");

    reset(pcfDeploymentManager);
    reset(executionLogCallback);
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());
    ArgumentCaptor<String> appDeletedMsgCaptor1 = ArgumentCaptor.forClass(String.class);
    doReturn(applicationDetail).when(pcfDeploymentManager).resizeApplication(eq(cfRequestConfig));
    pcfSetupCommandTaskHandler.deleteOlderApplications(previousReleases, cfRequestConfig, setupRequest,
        cfAppAutoscalarRequestData, activeApplication, null, executionLogCallback);
    verify(pcfDeploymentManager, times(2)).deleteApplication(eq(cfRequestConfig));
    verify(executionLogCallback, times(7)).saveExecutionLog(appDeletedMsgCaptor1.capture());
    appDeletedMsgCaptorAllValues = appDeletedMsgCaptor1.getAllValues();
    assertThat(appDeletedMsgCaptorAllValues.get(3))
        .isEqualTo("# Older application being deleted: \u001B[0;96m\u001B[40mPaymentApp__2#==#");
    assertThat(appDeletedMsgCaptorAllValues.get(4))
        .isEqualTo("# Older application being deleted: \u001B[0;96m\u001B[40mPaymentApp__1#==#");
    assertThat(appDeletedMsgCaptorAllValues.get(5))
        .isEqualTo("# Done Deleting older applications. Deleted Total 2 applications");

    reset(pcfDeploymentManager);
    reset(executionLogCallback);
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());
    previousReleases.remove(0);
    previousReleases.remove(1);
    ArgumentCaptor<String> appDeletedMsgCaptor2 = ArgumentCaptor.forClass(String.class);
    doReturn(applicationDetail).when(pcfDeploymentManager).resizeApplication(eq(cfRequestConfig));
    pcfSetupCommandTaskHandler.deleteOlderApplications(previousReleases, cfRequestConfig, setupRequest,
        cfAppAutoscalarRequestData, activeApplication, null, executionLogCallback);
    verify(pcfDeploymentManager, times(0)).deleteApplication(eq(cfRequestConfig));
    verify(executionLogCallback, times(4)).saveExecutionLog(appDeletedMsgCaptor2.capture());
    appDeletedMsgCaptorAllValues = appDeletedMsgCaptor2.getAllValues();
    assertThat(appDeletedMsgCaptorAllValues.get(3)).isEqualTo("# No older applications were eligible for deletion\n");

    reset(pcfDeploymentManager);
    reset(executionLogCallback);
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());
    previousReleases.remove(2);
    ArgumentCaptor<String> appDeletedMsgCaptor3 = ArgumentCaptor.forClass(String.class);
    doReturn(applicationDetail).when(pcfDeploymentManager).resizeApplication(eq(cfRequestConfig));
    pcfSetupCommandTaskHandler.deleteOlderApplications(previousReleases, cfRequestConfig, setupRequest,
        cfAppAutoscalarRequestData, activeApplication, null, executionLogCallback);
    verify(pcfDeploymentManager, times(0)).deleteApplication(eq(cfRequestConfig));
    verify(executionLogCallback, times(4)).saveExecutionLog(appDeletedMsgCaptor3.capture());
    appDeletedMsgCaptorAllValues = appDeletedMsgCaptor2.getAllValues();
    assertThat(appDeletedMsgCaptorAllValues.get(3)).isEqualTo("# No older applications were eligible for deletion\n");
  }

  private void assertAppRenamingVersionToNonVersion(String releaseName, ApplicationSummary activeApplication,
      ApplicationSummary inActiveApplication) throws PivotalClientApiException {
    ArgumentCaptor<ApplicationSummary> renamedAppCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskBaseHelper, times(4))
        .renameApp(
            renamedAppCaptor.capture(), any(CfRequestConfig.class), eq(executionLogCallback), newNameCaptor.capture());

    List<ApplicationSummary> allRenamedApps = renamedAppCaptor.getAllValues();
    List<String> allNewNames = newNameCaptor.getAllValues();
    assertThat(allRenamedApps.size()).isEqualTo(4);
    assertThat(allNewNames.size()).isEqualTo(4);

    // renaming due to version change : version --> non-version
    assertThat(allRenamedApps.get(0).getId()).isEqualTo(activeApplication.getId());
    assertThat(allNewNames.get(0)).isEqualTo(releaseName);
    assertThat(allRenamedApps.get(1).getId()).isEqualTo(inActiveApplication.getId());
    assertThat(allNewNames.get(1)).isEqualTo(releaseName + "__INACTIVE");

    // renaming due to new deployment in non-version mode
    assertThat(allRenamedApps.get(2).getId()).isEqualTo(inActiveApplication.getId());
    assertThat(allNewNames.get(2)).isEqualTo(releaseName + "__2");
    assertThat(allRenamedApps.get(3).getId()).isEqualTo(activeApplication.getId());
    assertThat(allNewNames.get(3)).isEqualTo(releaseName + "__INACTIVE");
  }

  private void assertSetupResponse(CfCommandExecutionResponse cfCommandExecutionResponse,
      String inActiveAppNameAfterRename, String activeAppNameAfterRename, ApplicationSummary inActiveApplication,
      ApplicationSummary activeApplication) {
    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isNotNull();
    assertThat(cfCommandExecutionResponse.getPcfCommandResponse()).isInstanceOf(CfSetupCommandResponse.class);
    CfSetupCommandResponse pcfCommandResponse =
        (CfSetupCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    CfAppSetupTimeDetails mostRecentInactiveAppVersion = pcfCommandResponse.getMostRecentInactiveAppVersion();
    assertThat(pcfCommandResponse.getExistingAppNamingStrategy()).isEqualTo(AppNamingStrategy.VERSIONING.name());
    assertThat(pcfCommandResponse.isNonVersioning()).isTrue();
    assertThat(pcfCommandResponse.isVersioningChanged()).isTrue();
    assertThat(pcfCommandResponse.getActiveAppRevision()).isEqualTo(3);

    assertThat(mostRecentInactiveAppVersion.getApplicationName()).isEqualTo(inActiveAppNameAfterRename);
    assertThat(mostRecentInactiveAppVersion.getApplicationGuid()).isEqualTo(inActiveApplication.getId());

    List<CfAppSetupTimeDetails> activeAppDetails = pcfCommandResponse.getDownsizeDetails();
    assertThat(activeAppDetails.size()).isEqualTo(1);
    assertThat(activeAppDetails.get(0).getApplicationName()).isEqualTo(activeAppNameAfterRename);
    assertThat(activeAppDetails.get(0).getApplicationGuid()).isEqualTo(activeApplication.getId());
  }

  private void mockSetupBehaviour(String releaseName, List<ApplicationSummary> previousReleases)
      throws PivotalClientApiException, IOException, ExecutionException {
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doNothing().when(pcfDeploymentManager).unmapRouteMapForApplication(any(), anyList(), any());
    doReturn("PASSWORD".toCharArray()).when(pcfCommandTaskHelper).getPassword(any());
    doReturn(CfInternalConfig.builder().build()).when(secretDecryptionService).decrypt(any(), any(), anyBoolean());
    doNothing().when(pcfDeploymentManager).deleteApplication(any());
    doReturn(new File("artifact.war")).when(pcfCommandTaskHelper).downloadArtifactFromManager(any(), any(), any());
    doReturn(new File("manifest.yml")).when(pcfCommandTaskBaseHelper).createManifestYamlFileLocally(any());
    doNothing().when(pcfCommandTaskBaseHelper).deleteCreatedFile(anyList());
    doReturn(previousReleases).when(pcfDeploymentManager).getDeployedServicesWithNonZeroInstances(any(), anyString());

    ApplicationDetail applicationDetails = ApplicationDetail.builder()
                                               .id("10")
                                               .diskQuota(1)
                                               .instances(0)
                                               .memoryLimit(1)
                                               .name("PaymentApp__1")
                                               .requestedState(STOPPED)
                                               .stack("")
                                               .runningInstances(0)
                                               .urls(Arrays.asList("harness-prod1-pcf", "harness-prod2-pcf.com"))
                                               .build();
    doReturn(applicationDetails).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(applicationDetails).when(pcfDeploymentManager).resizeApplication(any());
    doReturn(ApplicationDetail.builder()
                 .id("10")
                 .diskQuota(1)
                 .instances(0)
                 .memoryLimit(1)
                 .name(releaseName)
                 .requestedState(STOPPED)
                 .stack("")
                 .runningInstances(0)
                 .build())
        .when(pcfDeploymentManager)
        .createApplication(any(), any());
  }

  private CfCommandSetupRequest getSetupRequest(
      String releaseName, boolean isNonVersion, boolean featureFlagEnabled, boolean isBlueGreen) {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().metadataOnly(false).build();
    return CfCommandSetupRequest.builder()
        .pcfCommandType(CfCommandRequest.PcfCommandType.SETUP)
        .pcfConfig(getPcfConfig())
        .artifactFiles(Collections.emptyList())
        .artifactStreamAttributes(artifactStreamAttributes)
        .manifestYaml(MANIFEST_YAML)
        .organization(ORG)
        .space(SPACE)
        .accountId(ACCOUNT_ID)
        .routeMaps(Arrays.asList("harness-prod1-pcf", "harness-prod2-pcf.com"))
        .timeoutIntervalInMin(5)
        .releaseNamePrefix(releaseName)
        .maxCount(2)
        .isNonVersioning(isNonVersion)
        .nonVersioningInactiveRollbackEnabled(featureFlagEnabled)
        .blueGreen(isBlueGreen)
        .build();
  }

  private List<ApplicationSummary> getPreviousReleases() {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__1")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__2")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("2")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__3")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("3")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__4")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("4")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__5")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("5")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    return previousReleases;
  }

  private List<ApplicationSummary> getPreviousReleasesBeforeDeploymentStart(String activeApp, String inActiveApp) {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__1")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(inActiveApp)
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("2")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(activeApp)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("3")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    return previousReleases;
  }

  private List<ApplicationSummary> getPreviousReleasesAfterDeployment(String activeApp, String inActiveApp) {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__2")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(inActiveApp)
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("2")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(activeApp)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("3")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    return previousReleases;
  }

  private List<ApplicationSummary> getPreviousReleasesAfterVersionChangedRenaming(List<ApplicationSummary> releases) {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(releases.get(0).getName())
                             .diskQuota(releases.get(0).getDiskQuota())
                             .requestedState(releases.get(0).getRequestedState())
                             .id(releases.get(0).getId())
                             .instances(releases.get(0).getInstances())
                             .memoryLimit(releases.get(0).getMemoryLimit())
                             .runningInstances(releases.get(0).getRunningInstances())
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__INACTIVE")
                             .diskQuota(releases.get(1).getDiskQuota())
                             .requestedState(releases.get(1).getRequestedState())
                             .id(releases.get(1).getId())
                             .instances(releases.get(1).getInstances())
                             .memoryLimit(releases.get(1).getMemoryLimit())
                             .runningInstances(releases.get(1).getRunningInstances())
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp")
                             .diskQuota(releases.get(2).getDiskQuota())
                             .requestedState(releases.get(2).getRequestedState())
                             .id(releases.get(2).getId())
                             .instances(releases.get(2).getInstances())
                             .memoryLimit(releases.get(2).getMemoryLimit())
                             .runningInstances(releases.get(2).getRunningInstances())
                             .build());
    return previousReleases;
  }

  private List<ApplicationSummary> getPreviousReleasesAfterPreDeploymentRenaming(
      List<ApplicationSummary> releases, String inActiveAppName, String activeAppName) {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(releases.get(0).getName())
                             .diskQuota(releases.get(0).getDiskQuota())
                             .requestedState(releases.get(0).getRequestedState())
                             .id(releases.get(0).getId())
                             .instances(releases.get(0).getInstances())
                             .memoryLimit(releases.get(0).getMemoryLimit())
                             .runningInstances(releases.get(0).getRunningInstances())
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(inActiveAppName)
                             .diskQuota(releases.get(1).getDiskQuota())
                             .requestedState(releases.get(1).getRequestedState())
                             .id(releases.get(1).getId())
                             .instances(releases.get(1).getInstances())
                             .memoryLimit(releases.get(1).getMemoryLimit())
                             .runningInstances(releases.get(1).getRunningInstances())
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(activeAppName)
                             .diskQuota(releases.get(2).getDiskQuota())
                             .requestedState(releases.get(2).getRequestedState())
                             .id(releases.get(2).getId())
                             .instances(releases.get(2).getInstances())
                             .memoryLimit(releases.get(2).getMemoryLimit())
                             .runningInstances(releases.get(2).getRunningInstances())
                             .build());
    return previousReleases;
  }
}
