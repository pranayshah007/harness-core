/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.NOT_MANIFEST_YML_ELEMENT;
import static io.harness.delegate.cf.CfTestConstants.RELEASE_NAME;
import static io.harness.delegate.cf.CfTestConstants.RUNNING;
import static io.harness.pcf.model.PcfConstants.HARNESS__INACTIVE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.INSTANCE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.MEMORY_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.RANDOM_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.TIMEOUT_MANIFEST_YML_ELEMENT;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import io.harness.delegate.beans.pcf.CfAppRenameInfo;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.pcf.exception.InvalidPcfStateException;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCliDelegateResolver;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;
import io.harness.rule.Owner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

@OwnedBy(HarnessTeam.CDP)
public class PcfCommandTaskBaseHelperTest extends CategoryTest {
  public static final String MANIFEST_YAML = "  applications:\n"
      + "  - name: ${APPLICATION_NAME}\n"
      + "    memory: 350M\n"
      + "    instances: ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    routes:\n"
      + "      - route: ${ROUTE_MAP}\n";

  public static final String MANIFEST_YAML_LOCAL_RESOLVED = "---\n"
      + "applications:\n"
      + "- name: app1__1\n"
      + "  memory: 350M\n"
      + "  instances: 0\n"
      + "  buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "  path: /root/app\n"
      + "  routes:\n"
      + "  - route: app.harness.io\n"
      + "  - route: stage.harness.io\n";
  @Mock CfDeploymentManager pcfDeploymentManager;
  @Mock LogCallback executionLogCallback;
  @Mock CfCliDelegateResolver cfCliDelegateResolver;
  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private static ApplicationSummary getApplicationSummary(String name, String id) {
    return getApplicationSummary(name, id, 2);
  }

  private static ApplicationSummary getApplicationSummary(String name, String id, int instanceCount) {
    return ApplicationSummary.builder()
        .name(name)
        .diskQuota(1)
        .requestedState(RUNNING)
        .id(id)
        .urls(new String[] {"url" + id, "url4" + id})
        .instances(instanceCount)
        .memoryLimit(1)
        .runningInstances(0)
        .build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateManifestYamlFileLocally() throws Exception {
    File file = null;

    try {
      file = pcfCommandTaskHelper.createManifestYamlFileLocally(
          CfCreateApplicationRequestData.builder()
              .finalManifestYaml(MANIFEST_YAML_LOCAL_RESOLVED)
              .password("ABCD".toCharArray())
              .configPathVar(".")
              .newReleaseName(RELEASE_NAME + System.currentTimeMillis())
              .build());

      assertThat(file.exists()).isTrue();

      BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
      String line;
      StringBuilder stringBuilder = new StringBuilder(128);
      while ((line = bufferedReader.readLine()) != null) {
        stringBuilder.append(line).append('\n');
      }

      assertThat(stringBuilder.toString()).isEqualTo(MANIFEST_YAML_LOCAL_RESOLVED);
      pcfCommandTaskHelper.deleteCreatedFile(Arrays.asList(file));
      assertThat(file.exists()).isFalse();
    } finally {
      if (file != null && file.exists()) {
        FileIo.deleteFileIfExists(file.getAbsolutePath());
      }
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetRevisionFromReleaseName() throws Exception {
    Integer revision = pcfCommandTaskHelper.getRevisionFromReleaseName("app_serv_env__1");
    assertThat(1 == revision).isTrue();

    revision = pcfCommandTaskHelper.getRevisionFromReleaseName("app_serv_env__2");
    assertThat(2 == revision).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateManifestVarsYamlFileLocally() throws Exception {
    CfCreateApplicationRequestData requestData = CfCreateApplicationRequestData.builder()
                                                     .configPathVar(".")
                                                     .newReleaseName("app" + System.currentTimeMillis())
                                                     .build();

    File f = pcfCommandTaskHelper.createManifestVarsYamlFileLocally(requestData, "a:b", 1);
    assertThat(f).isNotNull();

    BufferedReader bufferedReader = new BufferedReader(new FileReader(f));
    String line;
    StringBuilder stringBuilder = new StringBuilder(128);
    while ((line = bufferedReader.readLine()) != null) {
      stringBuilder.append(line);
    }

    assertThat(stringBuilder.toString()).isEqualTo("a:b");
    pcfCommandTaskHelper.deleteCreatedFile(Arrays.asList(f));
    assertThat(f.exists()).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testDownSizeListOfInstances() throws Exception {
    reset(pcfDeploymentManager);
    ApplicationDetail detail = ApplicationDetail.builder()
                                   .diskQuota(1)
                                   .id("id")
                                   .name("app")
                                   .instances(0)
                                   .memoryLimit(1)
                                   .stack("stack")
                                   .runningInstances(2)
                                   .requestedState("RUNNING")
                                   .build();

    doReturn(detail).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(detail).when(pcfDeploymentManager).resizeApplication(any(), any());

    List<CfServiceData> cfServiceDataListToBeUpdated = new ArrayList<>();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    cfServiceDataList.add(CfServiceData.builder().name("test").desiredCount(2).build());
    CfCommandRollbackRequest commandRollbackRequest = CfCommandRollbackRequest.builder().useAppAutoscalar(true).build();
    String path = EMPTY;

    doReturn(true).when(pcfDeploymentManager).changeAutoscalarState(any(), any(), anyBoolean());
    pcfCommandTaskHelper.downSizeListOfInstances(executionLogCallback, cfServiceDataListToBeUpdated, cfRequestConfig,
        cfServiceDataList, commandRollbackRequest,
        CfAppAutoscalarRequestData.builder().applicationName(detail.getName()).applicationGuid(detail.getId()).build());
    verify(pcfDeploymentManager, times(1)).changeAutoscalarState(any(), any(), anyBoolean());
    assertThat(cfServiceDataListToBeUpdated.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownSizeListOfInstancesFailAutoscaler() throws Exception {
    reset(pcfDeploymentManager);
    ApplicationDetail detail = ApplicationDetail.builder()
                                   .diskQuota(1)
                                   .id("id")
                                   .name("app")
                                   .instances(0)
                                   .memoryLimit(1)
                                   .stack("stack")
                                   .runningInstances(2)
                                   .requestedState("RUNNING")
                                   .build();

    doReturn(detail).when(pcfDeploymentManager).getApplicationByName(any());
    doReturn(detail).when(pcfDeploymentManager).resizeApplication(any(), any());

    List<CfServiceData> cfServiceDataListToBeUpdated = new ArrayList<>();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    cfServiceDataList.add(CfServiceData.builder().name("test").desiredCount(2).build());
    CfCommandRollbackRequest commandRollbackRequest = CfCommandRollbackRequest.builder().useAppAutoscalar(true).build();

    doThrow(new PivotalClientApiException("#Throwing exception to test if flow stops or not"))
        .when(pcfCommandTaskHelper)
        .disableAutoscalar(any(), any());

    pcfCommandTaskHelper.downSizeListOfInstances(executionLogCallback, cfServiceDataListToBeUpdated, cfRequestConfig,
        cfServiceDataList, commandRollbackRequest,
        CfAppAutoscalarRequestData.builder().applicationName(detail.getName()).applicationGuid(detail.getId()).build());
    verify(pcfDeploymentManager, times(0)).changeAutoscalarState(any(), any(), anyBoolean());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalar(any(), any());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalarSafe(any(), any());
    assertThat(cfServiceDataListToBeUpdated.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testDownsizePreviousReleases() throws Exception {
    CfCommandDeployRequest request =
        CfCommandDeployRequest.builder().accountId(ACCOUNT_ID).downsizeAppDetail(null).build();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    // No old app exists
    pcfCommandTaskHelper.downsizePreviousReleases(request, cfRequestConfig, executionLogCallback, cfServiceDataList, 0,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().build());
    verify(pcfDeploymentManager, never()).getApplicationByName(any());

    InstanceDetail instanceDetail0 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("0")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();

    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("1")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();
    // old app exists, but downsize is not required.
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .diskQuota(1)
                                              .id("id")
                                              .name("app")
                                              .instanceDetails(instanceDetail0, instanceDetail1)
                                              .instances(2)
                                              .memoryLimit(1)
                                              .stack("stack")
                                              .runningInstances(2)
                                              .requestedState("RUNNING")
                                              .build();

    ApplicationDetail applicationDetailAfterDownsize = ApplicationDetail.builder()
                                                           .diskQuota(1)
                                                           .id("id")
                                                           .name("app")
                                                           .instanceDetails(instanceDetail0)
                                                           .instances(1)
                                                           .memoryLimit(1)
                                                           .stack("stack")
                                                           .runningInstances(1)
                                                           .requestedState("RUNNING")
                                                           .build();

    request.setDownsizeAppDetail(
        CfAppSetupTimeDetails.builder().applicationGuid("1").applicationName("app").initialInstanceCount(1).build());
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());

    // For BG, downsize should never happen.
    request.setStandardBlueGreen(true);
    pcfCommandTaskHelper.downsizePreviousReleases(request, cfRequestConfig, executionLogCallback, cfServiceDataList, 2,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().build());
    verify(pcfDeploymentManager, never()).getApplicationByName(any());

    // exptectedCount = cuurrentCount, no downsize should be called.
    request.setStandardBlueGreen(false);
    pcfCommandTaskHelper.downsizePreviousReleases(request, cfRequestConfig, executionLogCallback, cfServiceDataList, 2,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().applicationGuid("id").applicationName("app").build());
    verify(pcfDeploymentManager, times(1)).getApplicationByName(any());
    verify(pcfCommandTaskHelper, never()).downSize(any(), any(), any(), any());
    assertThat(cfServiceDataList.size()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");

    // Downsize application from 2 to 1
    doReturn(applicationDetailAfterDownsize).when(pcfDeploymentManager).resizeApplication(any(), any());
    pcfInstanceElements.clear();
    cfServiceDataList.clear();
    pcfCommandTaskHelper.downsizePreviousReleases(request, cfRequestConfig, executionLogCallback, cfServiceDataList, 1,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().build());
    verify(pcfDeploymentManager, times(2)).getApplicationByName(any());
    verify(pcfCommandTaskHelper, times(1)).downSize(any(), any(), any(), any());
    assertThat(cfServiceDataList.size()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");

    assertThat(pcfInstanceElements.size()).isEqualTo(1);
    assertThat(pcfInstanceElements.get(0).getApplicationId()).isEqualTo("id");
    assertThat(pcfInstanceElements.get(0).getDisplayName()).isEqualTo("app");
    assertThat(pcfInstanceElements.get(0).getInstanceIndex()).isEqualTo("0");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testDownsizePreviousReleases_autoscalar() throws Exception {
    CfCommandDeployRequest request =
        CfCommandDeployRequest.builder().accountId(ACCOUNT_ID).downsizeAppDetail(null).useAppAutoscalar(true).build();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    InstanceDetail instanceDetail0 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("0")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();

    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("1")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();
    // old app exists, but downsize is not required.
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .diskQuota(1)
                                              .id("id")
                                              .name("app")
                                              .instanceDetails(instanceDetail0, instanceDetail1)
                                              .instances(2)
                                              .memoryLimit(1)
                                              .stack("stack")
                                              .runningInstances(2)
                                              .requestedState("RUNNING")
                                              .build();

    ApplicationDetail applicationDetailAfterDownsize = ApplicationDetail.builder()
                                                           .diskQuota(1)
                                                           .id("id")
                                                           .name("app")
                                                           .instanceDetails(instanceDetail0)
                                                           .instances(1)
                                                           .memoryLimit(1)
                                                           .stack("stack")
                                                           .runningInstances(1)
                                                           .requestedState("RUNNING")
                                                           .build();

    request.setDownsizeAppDetail(
        CfAppSetupTimeDetails.builder().applicationGuid("1").applicationName("app").initialInstanceCount(1).build());
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());

    // Downsize application from 2 to 1
    doReturn(applicationDetailAfterDownsize).when(pcfDeploymentManager).resizeApplication(any(), any());
    doReturn(true).when(pcfDeploymentManager).changeAutoscalarState(any(), any(), anyBoolean());
    pcfInstanceElements.clear();
    cfServiceDataList.clear();
    pcfCommandTaskHelper.downsizePreviousReleases(request, cfRequestConfig, executionLogCallback, cfServiceDataList, 1,
        pcfInstanceElements,
        CfAppAutoscalarRequestData.builder()
            .applicationName(applicationDetail.getName())
            .applicationGuid(applicationDetail.getId())
            .build());
    verify(pcfDeploymentManager, times(1)).getApplicationByName(any());
    verify(pcfCommandTaskHelper, times(1)).downSize(any(), any(), any(), any());
    verify(pcfDeploymentManager, times(1)).changeAutoscalarState(any(), any(), anyBoolean());
    assertThat(cfServiceDataList.size()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");
    assertThat(cfServiceDataList.get(0).isDisableAutoscalarPerformed()).isTrue();

    assertThat(pcfInstanceElements.size()).isEqualTo(1);
    assertThat(pcfInstanceElements.get(0).getApplicationId()).isEqualTo("id");
    assertThat(pcfInstanceElements.get(0).getDisplayName()).isEqualTo("app");
    assertThat(pcfInstanceElements.get(0).getInstanceIndex()).isEqualTo("0");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownsizePreviousReleasesFailAutoscalar() throws Exception {
    CfCommandDeployRequest request =
        CfCommandDeployRequest.builder().accountId(ACCOUNT_ID).downsizeAppDetail(null).useAppAutoscalar(true).build();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    InstanceDetail instanceDetail0 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("0")
                                         .diskQuota(0L)
                                         .diskUsage(0L)
                                         .memoryQuota(0L)
                                         .memoryUsage(0L)
                                         .state("RUNNING")
                                         .build();

    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("1")
                                         .diskQuota(0L)
                                         .diskUsage(0L)
                                         .memoryQuota(0L)
                                         .memoryUsage(0L)
                                         .state("RUNNING")
                                         .build();
    // old app exists, but downsize is not required.
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .diskQuota(1)
                                              .id("id")
                                              .name("app")
                                              .instanceDetails(instanceDetail0, instanceDetail1)
                                              .instances(2)
                                              .memoryLimit(1)
                                              .stack("stack")
                                              .runningInstances(2)
                                              .requestedState("RUNNING")
                                              .build();

    ApplicationDetail applicationDetailAfterDownsize = ApplicationDetail.builder()
                                                           .diskQuota(1)
                                                           .id("id")
                                                           .name("app")
                                                           .instanceDetails(instanceDetail0)
                                                           .instances(1)
                                                           .memoryLimit(1)
                                                           .stack("stack")
                                                           .runningInstances(1)
                                                           .requestedState("RUNNING")
                                                           .build();

    request.setDownsizeAppDetail(
        CfAppSetupTimeDetails.builder().applicationGuid("1").applicationName("app").initialInstanceCount(1).build());
    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(any());

    // Downsize application from 2 to 1
    doReturn(applicationDetailAfterDownsize).when(pcfDeploymentManager).resizeApplication(any(), any());
    doThrow(new PivotalClientApiException("Throwing exception to test if flow stops or not"))
        .when(pcfCommandTaskHelper)
        .disableAutoscalar(any(), any());

    pcfInstanceElements.clear();
    cfServiceDataList.clear();
    pcfCommandTaskHelper.downsizePreviousReleases(request, cfRequestConfig, executionLogCallback, cfServiceDataList, 1,
        pcfInstanceElements,
        CfAppAutoscalarRequestData.builder()
            .applicationName(applicationDetail.getName())
            .applicationGuid(applicationDetail.getId())
            .build());
    verify(pcfDeploymentManager, times(1)).getApplicationByName(any());
    verify(pcfCommandTaskHelper, times(1)).downSize(any(), any(), any(), any());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalar(any(), any());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalarSafe(any(), any());
    verify(pcfDeploymentManager, times(0)).changeAutoscalarState(any(), any(), anyBoolean());
    assertThat(cfServiceDataList.size()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");
    assertThat(cfServiceDataList.get(0).isDisableAutoscalarPerformed()).isFalse();

    assertThat(pcfInstanceElements.size()).isEqualTo(1);
    assertThat(pcfInstanceElements.get(0).getApplicationId()).isEqualTo("id");
    assertThat(pcfInstanceElements.get(0).getDisplayName()).isEqualTo("app");
    assertThat(pcfInstanceElements.get(0).getInstanceIndex()).isEqualTo("0");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandleManifestWithNoRoute() {
    Map map = new HashMap<>();
    map.put(ROUTES_MANIFEST_YML_ELEMENT, new Object());
    pcfCommandTaskHelper.handleManifestWithNoRoute(map, false);
    assertThat(map.containsKey(ROUTES_MANIFEST_YML_ELEMENT)).isFalse();

    try {
      pcfCommandTaskHelper.handleManifestWithNoRoute(map, true);
      fail("Exception was expected, as no-route cant be used with BG");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("Invalid Config. \"no-route\" can not be used with BG deployment");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateYamlFileLocally() throws Exception {
    String data = "asd";
    File file = pcfCommandTaskHelper.createYamlFileLocally("./test" + System.currentTimeMillis(), data);
    assertThat(file.exists()).isTrue();
    String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
    assertThat(content).isEqualTo(data);
    FileIo.deleteFileIfExists(file.getAbsolutePath());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateDownsizeDetails() {
    List<CfAppSetupTimeDetails> details =
        pcfCommandTaskHelper.generateDownsizeDetails(ApplicationSummary.builder()
                                                         .name("a_s_e__4")
                                                         .diskQuota(1)
                                                         .requestedState(RUNNING)
                                                         .id("1")
                                                         .urls(new String[] {"url1", "url2"})
                                                         .instances(2)
                                                         .memoryLimit(1)
                                                         .runningInstances(0)
                                                         .build());
    assertThat(details).isNotNull();
    assertThat(details.size()).isEqualTo(1);
    assertThat(details.get(0).getApplicationName()).isEqualTo("a_s_e__4");
    assertThat(details.get(0).getUrls()).containsExactly("url1", "url2");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFindCurrentActiveApplication() throws Exception {
    ApplicationSummary currentActiveApplication = pcfCommandTaskHelper.findCurrentActiveApplication(null, null, null);
    assertThat(currentActiveApplication).isNull();

    doReturn(true).when(pcfDeploymentManager).isActiveApplication(any(), any());
    final List<ApplicationSummary> previousReleases1 = Arrays.asList(ApplicationSummary.builder()
                                                                         .name("a_s_e__6")
                                                                         .diskQuota(1)
                                                                         .requestedState(RUNNING)
                                                                         .id("1")
                                                                         .urls(new String[] {"url5", "url6"})
                                                                         .instances(2)
                                                                         .memoryLimit(1)
                                                                         .runningInstances(0)
                                                                         .build(),
        ApplicationSummary.builder()
            .name("a_s_e__7")
            .diskQuota(1)
            .requestedState(RUNNING)
            .id("1")
            .urls(new String[] {"url7", "url8"})
            .instances(2)
            .memoryLimit(1)
            .runningInstances(0)
            .build());

    assertThatThrownBy(()
                           -> pcfCommandTaskHelper.findCurrentActiveApplication(previousReleases1,
                               CfRequestConfig.builder().applicationName("a_s_e").build(), executionLogCallback))
        .isInstanceOf(InvalidPcfStateException.class);

    doReturn(false).doReturn(true).when(pcfDeploymentManager).isActiveApplication(any(), any());
    final List<ApplicationSummary> previousReleases2 = Arrays.asList(ApplicationSummary.builder()
                                                                         .name("a_s_e__6")
                                                                         .diskQuota(1)
                                                                         .requestedState(RUNNING)
                                                                         .id("1")
                                                                         .urls(new String[] {"url5", "url6"})
                                                                         .instances(2)
                                                                         .memoryLimit(1)
                                                                         .runningInstances(0)
                                                                         .build(),
        ApplicationSummary.builder()
            .name("a_s_e__7")
            .diskQuota(1)
            .requestedState(RUNNING)
            .id("1")
            .urls(new String[] {"url7", "url8"})
            .instances(2)
            .memoryLimit(1)
            .runningInstances(0)
            .build());

    currentActiveApplication = pcfCommandTaskHelper.findCurrentActiveApplication(
        previousReleases2, CfRequestConfig.builder().applicationName("a_s_e").build(), executionLogCallback);
    assertThat(currentActiveApplication).isNotNull();
    assertThat(currentActiveApplication.getName()).isEqualTo("a_s_e__6");
    assertThat(currentActiveApplication.getUrls()).containsExactly("url5", "url6");

    // when none of the releases have Active Env Variable set, then pick the application which has same name as release
    // name
    doReturn(false).doReturn(false).when(pcfDeploymentManager).isActiveApplication(any(), any());
    final List<ApplicationSummary> previousReleases3 = Arrays.asList(ApplicationSummary.builder()
                                                                         .name("a_s_e")
                                                                         .diskQuota(1)
                                                                         .requestedState(RUNNING)
                                                                         .id("1")
                                                                         .urls(new String[] {"url5", "url6"})
                                                                         .instances(2)
                                                                         .memoryLimit(1)
                                                                         .runningInstances(0)
                                                                         .build(),
        ApplicationSummary.builder()
            .name("a_s_e__7")
            .diskQuota(1)
            .requestedState(RUNNING)
            .id("1")
            .urls(new String[] {"url7", "url8"})
            .instances(2)
            .memoryLimit(1)
            .runningInstances(0)
            .build());

    currentActiveApplication = pcfCommandTaskHelper.findCurrentActiveApplication(
        previousReleases3, CfRequestConfig.builder().applicationName("a_s_e").build(), executionLogCallback);
    assertThat(currentActiveApplication).isNotNull();
    assertThat(currentActiveApplication.getName()).isEqualTo("a_s_e");
    assertThat(currentActiveApplication.getUrls()).containsExactly("url5", "url6");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPrintInstanceDetails() throws Exception {
    String output = "Instance Details:\n"
        + "Index: 0\n"
        + "State: RUNNING\n"
        + "Disk Usage: 1\n"
        + "CPU: 0.0\n"
        + "Memory Usage: 1\n"
        + "\n"
        + "Index: 1\n"
        + "State: RUNNING\n"
        + "Disk Usage: 2\n"
        + "CPU: 0.0\n"
        + "Memory Usage: 2\n";
    InstanceDetail detail0 = InstanceDetail.builder()
                                 .cpu(0.0)
                                 .index("0")
                                 .diskQuota(1l)
                                 .diskUsage(1l)
                                 .memoryQuota(1l)
                                 .memoryUsage(1l)
                                 .state("RUNNING")
                                 .build();

    InstanceDetail detail1 = InstanceDetail.builder()
                                 .cpu(0.0)
                                 .index("1")
                                 .diskQuota(2l)
                                 .diskUsage(2l)
                                 .memoryQuota(2l)
                                 .memoryUsage(2l)
                                 .state("RUNNING")
                                 .build();

    pcfCommandTaskHelper.printInstanceDetails(executionLogCallback, Arrays.asList(detail0, detail1));
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback).saveExecutionLog(captor.capture());
    String val = captor.getValue();
    assertThat(output).isEqualTo(val);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetCfCliPathOnDelegate() {
    String defaultCfPath = "cf";
    doReturn(Optional.of(defaultCfPath)).when(cfCliDelegateResolver).getAvailableCfCliPathOnDelegate(CfCliVersion.V6);
    String cfCliPathOnDelegate = pcfCommandTaskHelper.getCfCliPathOnDelegate(true, CfCliVersion.V6);

    assertThat(cfCliPathOnDelegate).isNotEmpty();
    assertThat(cfCliPathOnDelegate).isEqualTo(defaultCfPath);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetCfCliPathOnDelegateWithNullVersion() {
    assertThatThrownBy(() -> pcfCommandTaskHelper.getCfCliPathOnDelegate(true, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Requested CF CLI version on delegate cannot be null");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetCfCliPathOnDelegateWithNotInstalledCliOnDelegate() {
    doReturn(Optional.empty()).when(cfCliDelegateResolver).getAvailableCfCliPathOnDelegate(CfCliVersion.V7);

    assertThatThrownBy(() -> pcfCommandTaskHelper.getCfCliPathOnDelegate(true, CfCliVersion.V7))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Unable to find CF CLI version on delegate, requested version: V7");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testConstructAppName() {
    assertThat(PcfCommandTaskBaseHelper.constructActiveAppName("P", 12, true)).isEqualTo("P");
    assertThat(PcfCommandTaskBaseHelper.constructActiveAppName("P", 12, false)).isEqualTo("P__14");
    assertThat(PcfCommandTaskBaseHelper.constructInActiveAppName("P", 12, true))
        .isEqualTo("P__" + HARNESS__INACTIVE__IDENTIFIER);
    assertThat(PcfCommandTaskBaseHelper.constructInActiveAppName("P", 12, false)).isEqualTo("P__13");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetVersionChangeMessage() {
    assertThat(PcfCommandTaskBaseHelper.getVersionChangeMessage(true)).contains("Versioned to Non-versioned");
    assertThat(PcfCommandTaskBaseHelper.getVersionChangeMessage(false)).contains("on-versioned to Versioned");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRenameApp() throws Exception {
    ApplicationSummary summary1 = getApplicationSummary("app1", "1");
    ApplicationSummary summary2 = getApplicationSummary("app2", "2");
    List<ApplicationSummary> summaries = Arrays.asList(summary1, summary2);

    CfRequestConfig cfRequestConfig = Mockito.mock(CfRequestConfig.class);
    ApplicationSummary app = getApplicationSummary("app3", "3");

    ArgumentCaptor<CfRenameRequest> captor = ArgumentCaptor.forClass(CfRenameRequest.class);
    doNothing().when(pcfDeploymentManager).renameApplication(captor.capture(), eq(executionLogCallback));
    pcfCommandTaskHelper.renameApp(app, cfRequestConfig, executionLogCallback, "app");

    CfRenameRequest value = captor.getValue();
    assertThat(value.getName()).isEqualTo("app3");
    assertThat(value.getNewName()).isEqualTo("app");
    assertThat(value.getGuid()).isEqualTo("3");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetMaxVersion() throws Exception {
    ApplicationSummary summary1 = getApplicationSummary("app__1", "1");
    ApplicationSummary summary2 = getApplicationSummary("app__2", "2");
    List<ApplicationSummary> summaries = new ArrayList<>();
    assertThat(PcfCommandTaskBaseHelper.getMaxVersion(summaries)).isEqualTo(-1);
    summaries.add(summary1);
    assertThat(PcfCommandTaskBaseHelper.getMaxVersion(summaries)).isEqualTo(1);
    summaries.add(summary2);
    assertThat(PcfCommandTaskBaseHelper.getMaxVersion(summaries)).isEqualTo(2);
    summaries.add(getApplicationSummary("app", "3"));
    summaries.add(getApplicationSummary("app__INACTIVE", "4"));
    assertThat(PcfCommandTaskBaseHelper.getMaxVersion(summaries)).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testResetState() throws Exception {
    ApplicationSummary prevInactive = getApplicationSummary("app__1", "1");
    ApplicationSummary inactive = getApplicationSummary("app__2", "2");
    ApplicationSummary active = getApplicationSummary("app__3", "3");
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
    List<ApplicationSummary> summaries = new ArrayList<>();
    CfRequestConfig cfRequestConfig = Mockito.mock(CfRequestConfig.class);
    pcfCommandTaskHelper.resetState(
        summaries, null, null, "app", cfRequestConfig, true, null, -1, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(0)).renameApplication(any(), any());

    summaries.add(active);
    pcfCommandTaskHelper.resetState(
        summaries, active, null, "app", cfRequestConfig, true, null, -1, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(1)).renameApplication(any(), any());

    Mockito.reset(pcfDeploymentManager);
    summaries.add(inactive);
    summaries.add(prevInactive);
    pcfCommandTaskHelper.resetState(
        summaries, active, inactive, "app", cfRequestConfig, true, null, -1, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(2)).renameApplication(any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testResetState2() throws Exception {
    ApplicationSummary prevInactive = getApplicationSummary("app__1", "1");
    ApplicationSummary inactive = getApplicationSummary("app__INACTIVE", "2");
    ApplicationSummary active = getApplicationSummary("app", "3");
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
    List<ApplicationSummary> summaries = new ArrayList<>();
    CfRequestConfig cfRequestConfig = Mockito.mock(CfRequestConfig.class);
    pcfCommandTaskHelper.resetState(
        summaries, null, null, "app", cfRequestConfig, false, null, -1, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(0)).renameApplication(any(), any());

    summaries.add(active);
    pcfCommandTaskHelper.resetState(
        summaries, active, null, "app", cfRequestConfig, false, null, -1, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(1)).renameApplication(any(), any());

    Mockito.reset(pcfDeploymentManager);
    summaries.add(inactive);
    summaries.add(prevInactive);
    pcfCommandTaskHelper.resetState(
        summaries, active, inactive, "app", cfRequestConfig, false, null, -1, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(2)).renameApplication(any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testResetStateWithActiveAppRevision() throws Exception {
    ApplicationSummary active = getApplicationSummary("app", "3");
    ApplicationSummary inactiveApp = null;
    List<ApplicationSummary> summaries = new ArrayList<>();
    summaries.add(active);

    CfRequestConfig cfRequestConfig = Mockito.mock(CfRequestConfig.class);

    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 1, "app__1", null, -1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 1, "app__0", null, 0);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 1, "app__1", null, 1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 1, "app__10", null, 10);

    inactiveApp = getApplicationSummary("app__inactive", "3");
    summaries.add(inactiveApp);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__1", "app__0", -1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__0", "app__-1", 0);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__1", "app__0", 1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__10", "app__9", 10);

    summaries.add(getApplicationSummary("app__0", "3"));
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__2", "app__1", -1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__2", "app__1", 0);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__2", "app__1", 1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__2", "app__1", 10);

    summaries.add(getApplicationSummary("app__1", "3"));
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__3", "app__2", -1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__3", "app__2", 0);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__3", "app__2", 1);
    verifyReset(active, inactiveApp, summaries, cfRequestConfig, 2, "app__3", "app__2", 10);
  }

  private void verifyReset(ApplicationSummary active, ApplicationSummary inactiveApp,
      List<ApplicationSummary> summaries, CfRequestConfig cfRequestConfig, int renames, String activeAppName,
      String inactiveAppName, int activeAppRevision) throws PivotalClientApiException {
    ArgumentCaptor<CfRenameRequest> captor = ArgumentCaptor.forClass(CfRenameRequest.class);
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
    pcfCommandTaskHelper.resetState(summaries, active, inactiveApp, "app", cfRequestConfig, false, null,
        activeAppRevision, executionLogCallback, updateValues);
    verify(pcfDeploymentManager, times(renames)).renameApplication(captor.capture(), eq(executionLogCallback));
    List<CfRenameRequest> values = captor.getAllValues();
    assertThat(values.size()).isEqualTo(renames);
    for (int i = 0; i < renames; i++) {
      CfRenameRequest request = values.get(i);
      if (i == 0) {
        assertThat(request.getNewName()).isEqualTo(activeAppName);
      } else {
        assertThat(request.getNewName()).isEqualTo(inactiveAppName);
      }
    }
    reset(pcfDeploymentManager);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetMostRecentInactiveApplication() throws Exception {
    ApplicationSummary prevInactive = getApplicationSummary("app__1", "1", 0);
    ApplicationSummary inactive = getApplicationSummary("app__INACTIVE", "2", 5);
    ApplicationSummary active = getApplicationSummary("app", "3", 4);
    CfRequestConfig cfRequestConfig = Mockito.mock(CfRequestConfig.class);
    List<ApplicationSummary> summaries = new ArrayList<>();
    assertThat(
        pcfCommandTaskHelper.getMostRecentInactiveApplication(executionLogCallback, false, null, null, cfRequestConfig))
        .isNull();
    assertThat(pcfCommandTaskHelper.getMostRecentInactiveApplication(
                   executionLogCallback, false, null, summaries, cfRequestConfig))
        .isNull();
    assertThat(pcfCommandTaskHelper.getMostRecentInactiveApplication(
                   executionLogCallback, false, active, null, cfRequestConfig))
        .isNull();
    assertThat(pcfCommandTaskHelper.getMostRecentInactiveApplication(
                   executionLogCallback, false, active, summaries, cfRequestConfig))
        .isNull();

    summaries.add(prevInactive);
    summaries.add(inactive);
    summaries.add(active);
    assertThat(pcfCommandTaskHelper.getMostRecentInactiveApplication(
                   executionLogCallback, false, active, summaries, cfRequestConfig))
        .isEqualTo(inactive);
    inactive = getApplicationSummary("app__INACTIVE", "2", 0);
    summaries.set(1, inactive);
    assertThat(pcfCommandTaskHelper.getMostRecentInactiveApplication(
                   executionLogCallback, false, active, summaries, cfRequestConfig))
        .isEqualTo(inactive);
    summaries.remove(0);
    summaries.remove(0);
    assertThat(pcfCommandTaskHelper.getMostRecentInactiveApplication(
                   executionLogCallback, false, active, summaries, cfRequestConfig))
        .isNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testFindActiveApplicationNonBG() throws Exception {
    CfRequestConfig config = Mockito.mock(CfRequestConfig.class);
    List<ApplicationSummary> summaries = new ArrayList<>();
    assertThat(pcfCommandTaskHelper.findActiveApplication(executionLogCallback, false, config, summaries)).isNull();
    ApplicationSummary summary1 = getApplicationSummary("app__1", "1", 0);
    ApplicationSummary summary2 = getApplicationSummary("app__2", "2", 0);
    ApplicationSummary summary3 = getApplicationSummary("app__3", "3", 10);
    summaries.add(summary1);
    summaries.add(summary2);
    assertThat(pcfCommandTaskHelper.findActiveApplication(executionLogCallback, false, config, summaries))
        .isEqualTo(summary2);

    summaries.clear();
    summaries.add(summary1);
    summaries.add(summary2);
    summaries.add(summary3);
    assertThat(pcfCommandTaskHelper.findActiveApplication(executionLogCallback, false, config, summaries))
        .isEqualTo(summary3);

    summaries.clear();
    summaries.add(summary3);
    summaries.add(summary1);
    summaries.add(summary2);
    assertThat(pcfCommandTaskHelper.findActiveApplication(executionLogCallback, false, config, summaries))
        .isEqualTo(summary3);

    summaries.clear();
    summaries.add(summary1);
    summaries.add(summary3);
    summaries.add(summary2);
    assertThat(pcfCommandTaskHelper.findActiveApplication(executionLogCallback, false, config, summaries))
        .isEqualTo(summary3);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testShouldUseRandomRoute() {
    Map<String, Object> applicationToBeUpdated = new HashMap<>();
    List<String> routeMaps = new ArrayList<>();
    applicationToBeUpdated.put(RANDOM_ROUTE_MANIFEST_YML_ELEMENT, Boolean.TRUE);
    boolean shouldUseRandomRoute = pcfCommandTaskHelper.shouldUseRandomRoute(applicationToBeUpdated, routeMaps);
    assertThat(shouldUseRandomRoute).isTrue();

    applicationToBeUpdated = Collections.emptyMap();
    routeMaps.add("app.random.route");
    shouldUseRandomRoute = pcfCommandTaskHelper.shouldUseRandomRoute(applicationToBeUpdated, routeMaps);
    assertThat(shouldUseRandomRoute).isFalse();

    routeMaps = Collections.emptyList();
    shouldUseRandomRoute = pcfCommandTaskHelper.shouldUseRandomRoute(applicationToBeUpdated, routeMaps);
    assertThat(shouldUseRandomRoute).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateFinalManifestFilePath() {
    String manifestFilePath = "path-to-manifests/manifest_file.yml";
    String updatedManifestFilePath = "path-to-manifests/manifest_file_1.yml";
    String notManifestFilePath = "path-to-manifests/manifest_file";

    manifestFilePath = pcfCommandTaskHelper.generateFinalManifestFilePath(manifestFilePath);
    assertThat(manifestFilePath).isEqualTo(updatedManifestFilePath);

    String updatedNotManifestFilePath = pcfCommandTaskHelper.generateFinalManifestFilePath(notManifestFilePath);
    assertThat(updatedNotManifestFilePath).isEqualTo(notManifestFilePath);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateFinalMapForYamlDump() {
    Map<String, Object> applicationToBeUpdated = new TreeMap<>();
    applicationToBeUpdated.put(NAME_MANIFEST_YML_ELEMENT, "NAME_MANIFEST_YML_ELEMENT");
    applicationToBeUpdated.put(MEMORY_MANIFEST_YML_ELEMENT, "MEMORY_MANIFEST_YML_ELEMENT");
    applicationToBeUpdated.put(INSTANCE_MANIFEST_YML_ELEMENT, "INSTANCE_MANIFEST_YML_ELEMENT");
    applicationToBeUpdated.put(TIMEOUT_MANIFEST_YML_ELEMENT, "TIMEOUT_MANIFEST_YML_ELEMENT");
    applicationToBeUpdated.put(NOT_MANIFEST_YML_ELEMENT, NOT_MANIFEST_YML_ELEMENT);

    Map<String, Object> finalMapForYamlDump = pcfCommandTaskHelper.generateFinalMapForYamlDump(applicationToBeUpdated);

    assertThat(finalMapForYamlDump.size()).isEqualTo(4);
    assertThat(finalMapForYamlDump.get(NAME_MANIFEST_YML_ELEMENT)).isEqualTo("NAME_MANIFEST_YML_ELEMENT");
    assertThat(finalMapForYamlDump.get(MEMORY_MANIFEST_YML_ELEMENT)).isEqualTo("MEMORY_MANIFEST_YML_ELEMENT");
    assertThat(finalMapForYamlDump.get(INSTANCE_MANIFEST_YML_ELEMENT)).isEqualTo("INSTANCE_MANIFEST_YML_ELEMENT");
    assertThat(finalMapForYamlDump.get(TIMEOUT_MANIFEST_YML_ELEMENT)).isEqualTo("TIMEOUT_MANIFEST_YML_ELEMENT");
    assertThat(finalMapForYamlDump.get(NOT_MANIFEST_YML_ELEMENT)).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpsizeListOfInstances() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    List<InstanceDetail> instancesAfterUpsize = new ArrayList<>();
    instancesAfterUpsize.add(InstanceDetail.builder().index("idx1").state("RUNNING").build());
    instancesAfterUpsize.add(InstanceDetail.builder().index("idx1").state("RUNNING").build());

    List<CfServiceData> upszeList = new ArrayList<>();
    upszeList.add(CfServiceData.builder().desiredCount(5).previousCount(2).build());

    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    doReturn(getApplicationDetail(Collections.emptyList()))
        .when(pcfDeploymentManager)
        .getApplicationByName(cfRequestConfig);
    doReturn(getApplicationDetail(instancesAfterUpsize))
        .when(pcfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);

    pcfCommandTaskHelper.upsizeListOfInstances(
        executionLogCallback, pcfDeploymentManager, new ArrayList<>(), cfRequestConfig, upszeList, pcfInstanceElements);

    assertThat(pcfInstanceElements.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpsizeNewApplication() throws PivotalClientApiException {
    int previousCount = 2;
    int desiredCount = 5;
    CfCommandDeployRequest cfCommandDeployRequest =
        CfCommandDeployRequest.builder().newReleaseName("releaseName").updateCount(previousCount).build();
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    List<InstanceDetail> instancesAfterUpsize = new ArrayList<>();
    instancesAfterUpsize.add(InstanceDetail.builder().index("idx1").state("RUNNING").build());
    instancesAfterUpsize.add(InstanceDetail.builder().index("idx1").state("RUNNING").build());

    List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();
    cfServiceDataUpdated.add(CfServiceData.builder().desiredCount(desiredCount).previousCount(previousCount).build());

    ApplicationDetail applicationDetail = getApplicationDetail(Collections.emptyList());
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    doReturn(applicationDetail).when(pcfDeploymentManager).getApplicationByName(cfRequestConfig);
    doReturn(getApplicationDetail(instancesAfterUpsize))
        .when(pcfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);

    pcfCommandTaskHelper.upsizeNewApplication(executionLogCallback, cfCommandDeployRequest, cfServiceDataUpdated,
        cfRequestConfig, applicationDetail, pcfInstanceElements);

    assertThat(pcfInstanceElements.size()).isEqualTo(previousCount);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + "__2";
    final String activeAppCurrentName = releaseName + "__3";

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    Deque<CfAppRenameInfo> renames = new ArrayDeque<>();

    CfAppSetupTimeDetails cfAppSetupTimeDetails =
        pcfCommandTaskHelper.renameInActiveAppDuringBGDeployment(previousReleases, cfRequestConfig, releaseName,
            executionLogCallback, AppNamingStrategy.VERSIONING.name(), renames);
    assertThat(cfAppSetupTimeDetails.getOldName()).isNull();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToNonVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + "__2";
    final String activeAppCurrentName = releaseName + "__3";

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    Deque<CfAppRenameInfo> renames = new ArrayDeque<>();

    CfAppSetupTimeDetails cfAppSetupTimeDetails =
        pcfCommandTaskHelper.renameInActiveAppDuringBGDeployment(previousReleases, cfRequestConfig, releaseName,
            executionLogCallback, AppNamingStrategy.VERSIONING.name(), renames);
    assertThat(cfAppSetupTimeDetails.getOldName()).isNull();

    previousReleases.remove(0);
    cfAppSetupTimeDetails = pcfCommandTaskHelper.renameInActiveAppDuringBGDeployment(previousReleases, cfRequestConfig,
        releaseName, executionLogCallback, AppNamingStrategy.VERSIONING.name(), renames);
    assertThat(cfAppSetupTimeDetails.getOldName()).isNull();

    previousReleases = Collections.emptyList(); // no apps, i.e. first deployment in non-version mode
    cfAppSetupTimeDetails = pcfCommandTaskHelper.renameInActiveAppDuringBGDeployment(previousReleases, cfRequestConfig,
        releaseName, executionLogCallback, AppNamingStrategy.VERSIONING.name(), renames);
    assertThat(cfAppSetupTimeDetails.getOldName()).isNull();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToNonVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    final String releaseName = "PaymentApp";
    final String inActiveAppCurrentName = releaseName + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    final String activeAppCurrentName = releaseName;

    List<ApplicationSummary> previousReleases =
        getPreviousReleasesBeforeDeploymentStart(activeAppCurrentName, inActiveAppCurrentName);

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    Deque<CfAppRenameInfo> renames = new ArrayDeque<>();

    when(pcfDeploymentManager.isActiveApplication(any(), any())).thenAnswer((Answer<Boolean>) invocationOnMock -> {
      Object[] arguments = invocationOnMock.getArguments();
      CfRequestConfig requestConfig = (CfRequestConfig) arguments[0];
      return requestConfig.getApplicationName().equals(activeAppCurrentName);
    });

    when(pcfDeploymentManager.isInActiveApplication(any())).thenAnswer((Answer<Boolean>) invocationOnMock -> {
      Object[] arguments = invocationOnMock.getArguments();
      CfRequestConfig requestConfig = (CfRequestConfig) arguments[0];
      return requestConfig.getApplicationName().equals(inActiveAppCurrentName);
    });

    CfAppSetupTimeDetails inActiveAppDetails =
        pcfCommandTaskHelper.renameInActiveAppDuringBGDeployment(previousReleases, cfRequestConfig, releaseName,
            executionLogCallback, AppNamingStrategy.APP_NAME_WITH_VERSIONING.name(), renames);
    assertThat(inActiveAppDetails.getOldName()).isEqualTo(inActiveAppCurrentName);

    ArgumentCaptor<CfRenameRequest> renamingAppCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    ApplicationSummary inActiveAppSummary = previousReleases.get(1);
    verify(pcfDeploymentManager, times(1)).renameApplication(renamingAppCaptor.capture(), any());
    CfRenameRequest renamingRequest = renamingAppCaptor.getValue();
    assertThat(renamingRequest.getGuid()).isEqualTo(inActiveAppSummary.getId());
    assertThat(renamingRequest.getNewName()).isEqualTo(releaseName + PcfConstants.DELIMITER + "2");
  }

  private List<ApplicationSummary> getPreviousReleasesBeforeDeploymentStart(String activeApp, String inActiveApp) {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("PaymentApp__1")
                             .diskQuota(1)
                             .requestedState("STOPPED")
                             .id("c9e34660-bf25-43dd-9f75-2b0ef5445354")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(inActiveApp)
                             .diskQuota(1)
                             .requestedState("RUNNING")
                             .id("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(activeApp)
                             .diskQuota(1)
                             .requestedState("RUNNING")
                             .id("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                             .instances(1)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    return previousReleases;
  }

  private ApplicationDetail getApplicationDetail(List<InstanceDetail> instances) {
    return ApplicationDetail.builder()
        .diskQuota(1)
        .id("appId")
        .name("appName")
        .memoryLimit(1)
        .stack("stack")
        .runningInstances(1)
        .requestedState("RUNNING")
        .instances(2)
        .instanceDetails(instances)
        .build();
  }
}
