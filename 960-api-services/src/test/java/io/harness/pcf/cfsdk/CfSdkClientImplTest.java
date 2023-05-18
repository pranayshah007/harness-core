/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf.cfsdk;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.routes.Route;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfSdkClientImplTest extends CategoryTest {
  @Spy private ConnectionContextProvider connectionContextProvider;
  @Spy private CloudFoundryClientProvider cloudFoundryClientProvider;
  @InjectMocks @Spy private CloudFoundryOperationsProvider cloudFoundryOperationsProvider;
  @InjectMocks @Spy private CfSdkClientImpl cfSdkClient;

  private static ApplicationSummary getApplicationSummary(String name, String id, int instanceCount) {
    return ApplicationSummary.builder()
        .name(name)
        .diskQuota(1)
        .requestedState("RUNNING")
        .id(id)
        .urls(new String[] {"url" + id, "url4" + id})
        .instances(instanceCount)
        .memoryLimit(1)
        .runningInstances(0)
        .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetOrganizationsException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.getOrganizations(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while fetching Organizations, Error: unauthorized: Bad credentials");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetApplicationByNameException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.getApplicationByName(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while getting application: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetApplicationEnvironmentsByNameException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");
    cfRequestConfig.setApplicationName("app");

    try {
      cfSdkClient.getApplicationEnvironmentsByName(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
      assertThat(e.getMessage()).contains("Exception occurred while getting application Environments: app");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetRouteMapException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.getRouteMap(cfRequestConfig, "qa.harness.io/api");
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Exception occurred while getting routeMaps for Application: app, Error: unauthorized: Bad credentials");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetApplicationsException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.getApplications(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("Exception occurred while fetching Applications, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetSpacesForOrganizationException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.getSpacesForOrganization(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while fetching Spaces, Error: unauthorized: Bad credentials");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteApplicationException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.deleteApplication(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while deleting application: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testStopApplicationException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.stopApplication(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while stopping Application: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetTasksException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.getTasks(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while getting Tasks for Application: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testScaleApplicationsException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.scaleApplications(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred Scaling Applications: app, to count: 0, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testStartApplicationException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    try {
      cfSdkClient.startApplication(cfRequestConfig);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while starting application: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUnmapRouteMapForAppException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    Route route = Route.builder().application("app").host("stage").domain("harness.io").id("1").space("space").build();

    try {
      cfSdkClient.unmapRouteMapForApp(cfRequestConfig, route);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while unmapping routeMap for Application: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testMapRouteMapForAppException() {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    Route route = Route.builder().application("app").host("stage").domain("harness.io").id("1").space("space").build();

    try {
      cfSdkClient.mapRouteMapForApp(cfRequestConfig, route);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Exception occurred while mapping routeMap: Route{applications=[app], domain=harness.io, host=stage, id=1, path=null, port=null, service=null, space=space, type=null}, AppName: app, Error: No space targeted");
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUnmapRoutesForApplicationException() throws PivotalClientApiException, InterruptedException {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");
    String space = "space1";

    Route route = Route.builder().application("app").host("stage").domain("harness.io").id("1").space("space").build();
    List<String> routes = new ArrayList<>();
    String path1 = "stage.harness.io";
    doReturn(asList(route)).when(cfSdkClient).getRouteMapsByNames(anyList(), any());

    try {
      cfSdkClient.unmapRoutesForApplication(cfRequestConfig, routes);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while unmapping routeMap for Application: app, Error: No space targeted");
    }
  }

  private CfRequestConfig getCfRequestConfig() {
    return CfRequestConfig.builder().timeOutIntervalInMins(1).orgName("org").applicationName("app").build();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRenameApplication() throws PivotalClientApiException, InterruptedException {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    CfRenameRequest request = new CfRenameRequest(cfRequestConfig, "id", "app", "app_new");

    doReturn(new ArrayList<>()).when(cfSdkClient).getApplications(any());
    assertThatThrownBy(() -> cfSdkClient.renameApplication(request))
        .isInstanceOf(PivotalClientApiException.class)
        .hasMessageContaining("Failed to rename app");

    doReturn(Arrays.asList(getApplicationSummary("app", "id", 1))).when(cfSdkClient).getApplications(any());
    try {
      cfSdkClient.renameApplication(request);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while renaming Application: app, Error: No space targeted");
    }
  }
}
