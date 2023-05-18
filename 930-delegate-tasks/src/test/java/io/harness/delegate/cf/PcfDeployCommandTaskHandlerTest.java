/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.cf.CfTestConstants.APP_ID;
import static io.harness.delegate.cf.CfTestConstants.APP_NAME;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PcfDeployCommandTaskHandlerTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;

  @InjectMocks @Inject PcfDeployCommandTaskHandler pcfDeployCommandTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroStandardBG() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    CfCommandDeployRequest cfCommandDeployRequest = CfCommandDeployRequest.builder().isStandardBlueGreen(true).build();
    pcfDeployCommandTaskHandler.unmapRoutesIfAppDownsizedToZero(
        cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroEmptyAppDetails() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    CfCommandDeployRequest cfCommandDeployRequest =
        CfCommandDeployRequest.builder().isStandardBlueGreen(false).downsizeAppDetail(null).build();
    pcfDeployCommandTaskHandler.unmapRoutesIfAppDownsizedToZero(
        cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroEmptyAppName() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    CfCommandDeployRequest cfCommandDeployRequest = CfCommandDeployRequest.builder()
                                                        .isStandardBlueGreen(false)
                                                        .downsizeAppDetail(CfAppSetupTimeDetails.builder().build())
                                                        .build();
    pcfDeployCommandTaskHandler.unmapRoutesIfAppDownsizedToZero(
        cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroEmptyAppNameNumberOfInstancesNotZero()
      throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    String appName = "appName";
    CfCommandDeployRequest cfCommandDeployRequest =
        CfCommandDeployRequest.builder()
            .isStandardBlueGreen(false)
            .downsizeAppDetail(CfAppSetupTimeDetails.builder().applicationName(appName).build())
            .build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .urls(Arrays.asList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    pcfDeployCommandTaskHandler.unmapRoutesIfAppDownsizedToZero(
        cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZero() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    String appName = "appName";
    CfCommandDeployRequest cfCommandDeployRequest =
        CfCommandDeployRequest.builder()
            .isStandardBlueGreen(false)
            .downsizeAppDetail(CfAppSetupTimeDetails.builder().applicationName(appName).build())
            .build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .urls(Arrays.asList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    doNothing()
        .when(pcfCommandTaskHelper)
        .unmapExistingRouteMaps(applicationDetail, cfRequestConfig, executionLogCallback);
    pcfDeployCommandTaskHandler.unmapRoutesIfAppDownsizedToZero(
        cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper, times(1))
        .unmapExistingRouteMaps(eq(applicationDetail), eq(cfRequestConfig), eq(executionLogCallback));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInvalidArgumentsException() {
    try {
      pcfDeployCommandTaskHandler.executeTaskInternal(
          CfCommandRollbackRequest.builder().build(), null, logStreamingTaskClient, false);
    } catch (Exception e) {
      assertThatExceptionOfType(InvalidArgumentsException.class);
      InvalidArgumentsException invalidArgumentsException = (InvalidArgumentsException) e;
      assertThat(invalidArgumentsException.getParams())
          .containsValue("CfCommandRequest: Must be instance of CfCommandDeployRequest");
    }
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testconfigureAutoscalarIfNeeded() throws PivotalClientApiException, IOException {
    CfCommandDeployRequest cfCommandDeployRequest = CfCommandDeployRequest.builder().build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .urls(Arrays.asList("url1"))
                                              .runningInstances(1)
                                              .build();
    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder().build();

    // don't use autoscalar
    cfCommandDeployRequest.setUseAppAutoscalar(false);
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        cfCommandDeployRequest, applicationDetail, pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // empty PcfManifestsPackage
    cfCommandDeployRequest.setUseAppAutoscalar(true);
    cfCommandDeployRequest.setPcfManifestsPackage(null);
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        cfCommandDeployRequest, applicationDetail, pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // empty autoscalarManifestsYaml
    cfCommandDeployRequest.setPcfManifestsPackage(PcfManifestsPackage.builder().build());
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        cfCommandDeployRequest, applicationDetail, pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // max count bigger than update count
    cfCommandDeployRequest.setPcfManifestsPackage(
        PcfManifestsPackage.builder().autoscalarManifestYml("autoscalarManifestYml").build());
    cfCommandDeployRequest.setMaxCount(2);
    cfCommandDeployRequest.setUpdateCount(1);
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        cfCommandDeployRequest, applicationDetail, pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGeneratePcfInstancesElementsForExistingApp() throws PivotalClientApiException {
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .runningInstances(1)
                                              .instanceDetails(Arrays.asList(InstanceDetail.builder()
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
                                              .requestedState("RUNNING")
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());

    CfCommandDeployRequest request = CfCommandDeployRequest.builder().build();
    pcfDeployCommandTaskHandler.generatePcfInstancesElementsForExistingApp(
        pcfInstanceElements, CfRequestConfig.builder().build(), request, executionLogCallback);

    request.setDownsizeAppDetail(CfAppSetupTimeDetails.builder().applicationName("app").build());
    pcfDeployCommandTaskHandler.generatePcfInstancesElementsForExistingApp(
        pcfInstanceElements, CfRequestConfig.builder().build(), request, executionLogCallback);
    assertThat(pcfInstanceElements.size()).isEqualTo(1);
    assertThat(pcfInstanceElements.get(0).getInstanceIndex()).isEqualTo("2");

    doThrow(new PivotalClientApiException("e")).when(cfDeploymentManager).getApplicationByName(any());
    pcfDeployCommandTaskHandler.generatePcfInstancesElementsForExistingApp(
        pcfInstanceElements, CfRequestConfig.builder().build(), request, executionLogCallback);
  }

  @Test
  @Owner(developers = {ADWAIT, IVAN})
  @Category(UnitTests.class)
  public void testConfigureAutoscalarIfNeeded() throws IOException, PivotalClientApiException {
    CfCommandDeployRequest pcfCommandRequest = CfCommandDeployRequest.builder()
                                                   .downSizeCount(1)
                                                   .totalPreviousInstanceCount(2)
                                                   .timeoutIntervalInMin(2)
                                                   .useAppAutoscalar(false)
                                                   .maxCount(1)
                                                   .updateCount(1)
                                                   .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .runningInstances(0)
                                              .build();

    reset(cfDeploymentManager);
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());

    // Autoscalar is false
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        pcfCommandRequest, applicationDetail, null, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // Autoscalar is true, but no autosaclar file
    pcfCommandRequest.setUseAppAutoscalar(true);
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        pcfCommandRequest, applicationDetail, null, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // Autoscalar is true, autoscalar file is present
    String path = "./test" + System.currentTimeMillis();
    FileIo.createDirectoryIfDoesNotExist(path);

    pcfCommandRequest.setPcfManifestsPackage(PcfManifestsPackage.builder().autoscalarManifestYml("abc").build());
    CfAppAutoscalarRequestData autoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                           .applicationName(APP_NAME)
                                                           .applicationGuid(APP_ID)
                                                           .configPathVar(path)
                                                           .build();

    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        pcfCommandRequest, applicationDetail, autoscalarRequestData, executionLogCallback);
    ArgumentCaptor<CfAppAutoscalarRequestData> captor = ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    verify(cfDeploymentManager, times(1)).performConfigureAutoscalar(captor.capture(), any());
    autoscalarRequestData = captor.getValue();
    assertThat(autoscalarRequestData.getApplicationName()).isEqualTo(APP_NAME);
    assertThat(autoscalarRequestData.getApplicationGuid()).isEqualTo(APP_ID);
    String filePath = autoscalarRequestData.getAutoscalarFilePath();

    String content = new String(Files.readAllBytes(Paths.get(filePath)));
    assertThat(content).isEqualTo("abc");
    FileIo.deleteDirectoryAndItsContentIfExists(path);
  }
}
