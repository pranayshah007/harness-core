/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf.cfsdk;

import static io.harness.pcf.model.PcfConstants.HARNESS__STAGE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STATUS__IDENTIFIER;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.doppler.MessageType;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationEnvironments;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.Applications;
import org.cloudfoundry.operations.applications.LogsRequest;
import org.cloudfoundry.operations.domains.Domain;
import org.cloudfoundry.operations.domains.Domains;
import org.cloudfoundry.operations.domains.Status;
import org.cloudfoundry.operations.organizations.OrganizationDetail;
import org.cloudfoundry.operations.organizations.OrganizationQuota;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
import org.cloudfoundry.operations.organizations.Organizations;
import org.cloudfoundry.operations.routes.CreateRouteRequest;
import org.cloudfoundry.operations.routes.Route;
import org.cloudfoundry.operations.routes.Routes;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfSdkClientImplWithOperationsWrapperMockedTest extends CategoryTest {
  @Mock private CloudFoundryOperationsWrapper wrapper;
  @Mock private CloudFoundryOperations operations;
  @Mock private Organizations organizations;
  @Mock private Applications applications;
  @Mock private Routes routes;
  @Mock private Domains domains;
  @Mock private CloudFoundryOperationsProvider cloudFoundryOperationsProvider;
  @Mock private LogCallback logCallback;
  @InjectMocks @Spy private CfSdkClientImpl cfSdkClient;

  @Before
  public void setupMocks() throws Exception {
    when(wrapper.getCloudFoundryOperations()).thenReturn(operations);
    when(operations.applications()).thenReturn(applications);
    when(operations.routes()).thenReturn(routes);
    doReturn(wrapper).when(cloudFoundryOperationsProvider).getCloudFoundryOperationsWrapper(any());

    clearProperties();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHandlePasswordForSpecialCharacters() throws Exception {
    // Single quotes cannot be part of the password due to limitations on escaping them.
    String password = "Ab1~!@#$%^&*()\"_c";
    ProcessExecutor processExecutor =
        new ProcessExecutor()
            .timeout(1, TimeUnit.MINUTES)
            .command("/bin/bash", "-c",
                new StringBuilder(128).append("echo ").append(password).append(" | cat ").toString());
    ProcessResult processResult = processExecutor.execute();
    assertThat(processResult.getExitValue()).isNotEqualTo(0);

    password = cfSdkClient.handlePwdForSpecialCharsForShell("Ab1~!@#$%^&*()\"_c");
    processExecutor = new ProcessExecutor()
                          .timeout(1, TimeUnit.MINUTES)
                          .command("/bin/bash", "-c",
                              new StringBuilder(128).append("echo ").append(password).append(" | cat ").toString());
    processResult = processExecutor.execute();
    assertThat(processResult.getExitValue()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetOrganizations() throws Exception {
    OrganizationSummary summary1 = OrganizationSummary.builder().id("1").name("org1").build();
    OrganizationSummary summary2 = OrganizationSummary.builder().id("2").name("org2").build();

    Flux<OrganizationSummary> result = Flux.create(sink -> {
      sink.next(summary1);
      sink.next(summary2);
      sink.complete();
    });

    when(operations.organizations()).thenReturn(organizations);
    when(organizations.list()).thenReturn(result);

    List<OrganizationSummary> organizationSummaries = cfSdkClient.getOrganizations(getCfRequestConfig());
    assertThat(organizationSummaries).isNotNull();
    assertThat(organizationSummaries).containsExactly(summary1, summary2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetSpacesForOrganization() throws Exception {
    String[] spaces = new String[] {"space1", "space2", "space3"};
    when(organizations.get(any()))
        .thenReturn(Mono.just(OrganizationDetail.builder()
                                  .spaces(spaces)
                                  .id("id")
                                  .name("org")
                                  .quota(OrganizationQuota.builder()
                                             .id("1")
                                             .instanceMemoryLimit(1)
                                             .name("name")
                                             .organizationId("id")
                                             .paidServicePlans(true)
                                             .totalMemoryLimit(1)
                                             .totalRoutes(1)
                                             .totalServiceInstances(1)
                                             .build())
                                  .build()));
    when(operations.organizations()).thenReturn(organizations);

    List<String> spaceResult = cfSdkClient.getSpacesForOrganization(getCfRequestConfig());

    assertThat(spaceResult).isNotNull();
    assertThat(spaceResult).containsExactly(spaces);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetApplications() throws Exception {
    ApplicationSummary applicationSummary1 = ApplicationSummary.builder()
                                                 .id("id1")
                                                 .name("app1")
                                                 .diskQuota(1)
                                                 .instances(1)
                                                 .memoryLimit(1)
                                                 .requestedState("RUNNING")
                                                 .runningInstances(0)
                                                 .build();
    ApplicationSummary applicationSummary2 = ApplicationSummary.builder()
                                                 .id("id2")
                                                 .name("app2")
                                                 .diskQuota(1)
                                                 .instances(1)
                                                 .memoryLimit(1)
                                                 .requestedState("RUNNING")
                                                 .runningInstances(0)
                                                 .build();

    Flux<ApplicationSummary> result = Flux.create(sink -> {
      sink.next(applicationSummary1);
      sink.next(applicationSummary2);
      sink.complete();
    });
    when(applications.list()).thenReturn(result);

    List<ApplicationSummary> applicationSummaries = cfSdkClient.getApplications(getCfRequestConfig());

    assertThat(applicationSummaries).isNotNull();
    assertThat(applicationSummaries).containsExactly(applicationSummary1, applicationSummary2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetApplicationByName() throws Exception {
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("id")
                                              .name("app")
                                              .diskQuota(1)
                                              .stack("stack")
                                              .instances(1)
                                              .memoryLimit(1)
                                              .requestedState("RUNNING")
                                              .runningInstances(1)
                                              .build();
    when(applications.get(any())).thenReturn(Mono.just(applicationDetail));

    ApplicationDetail applicationDetail1 = cfSdkClient.getApplicationByName(getCfRequestConfig());
    assertThat(applicationDetail1).isNotNull();
    assertThat(applicationDetail).isEqualTo(applicationDetail1);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetApplicationEnvironmentsByName() throws Exception {
    ApplicationEnvironments applicationEnvironments =
        ApplicationEnvironments.builder()
            .putAllUserProvided(Collections.singletonMap(HARNESS__STATUS__IDENTIFIER, HARNESS__STAGE__IDENTIFIER))
            .build();
    when(applications.getEnvironments(any())).thenReturn(Mono.just(applicationEnvironments));

    ApplicationEnvironments environment = cfSdkClient.getApplicationEnvironmentsByName(getCfRequestConfig());
    assertThat(environment).isNotNull();
    assertThat(environment.getUserProvided()).isNotNull();
    assertThat(environment.getUserProvided().get(HARNESS__STATUS__IDENTIFIER)).isEqualTo(HARNESS__STAGE__IDENTIFIER);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetAllRoutesForSpace() throws Exception {
    Route route1 = Route.builder().application("app").host("stage").domain("harness.io").id("1").space("space").build();
    Route route2 =
        Route.builder().application("app").host("qa").domain("harness.io").path("/api").id("2").space("space").build();

    Flux<Route> result = Flux.create(sink -> {
      sink.next(route1);
      sink.next(route2);
      sink.complete();
    });

    when(routes.list(any())).thenReturn(result);

    List<String> routeMaps = cfSdkClient.getRoutesForSpace(getCfRequestConfig());
    assertThat(routeMaps).isNotNull();
    assertThat(routeMaps).containsExactly("stage.harness.io", "qa.harness.io/api");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetRoutesMapsByName() throws Exception {
    Route route1 = Route.builder().application("app").host("stage").domain("harness.io").id("1").space("space").build();
    Route route2 =
        Route.builder().application("app").host("qa").domain("harness.io").path("/api").id("2").space("space").build();

    Flux<Route> result = Flux.create(sink -> {
      sink.next(route1);
      sink.next(route2);
      sink.complete();
    });

    when(routes.list(any())).thenReturn(result);

    List<Route> routeMaps =
        cfSdkClient.getRouteMapsByNames(asList("stage.harness.io", "qa.harness.io/api"), getCfRequestConfig());
    assertThat(routeMaps).isNotNull();
    List<String> routes = routeMaps.stream().map(route -> cfSdkClient.getPathFromRouteMap(route)).collect(toList());
    assertThat(routes).containsExactly("stage.harness.io", "qa.harness.io/api");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetRouteMap() throws Exception {
    Route route_1 =
        Route.builder().application("app").host("stage").domain("harness.io").id("1").space("space").build();
    Route route_2 =
        Route.builder().application("app").host("qa").domain("harness.io").path("/api").id("2").space("space").build();

    Flux<Route> routeResult = Flux.create(sink -> {
      sink.next(route_1);
      sink.next(route_2);
      sink.complete();
    });

    when(routes.list(any())).thenReturn(routeResult);
    Optional<Route> routeOptional = cfSdkClient.getRouteMap(getCfRequestConfig(), "qa.harness.io/api");
    assertThat(routeOptional.isPresent()).isTrue();
    assertThat(cfSdkClient.getPathFromRouteMap(routeOptional.get())).isEqualTo("qa.harness.io/api");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFindRoutesNeedToBeCreated() throws Exception {
    List<String> routes = new ArrayList<>();
    String path1 = "myapp.cfapps.io";
    String path2 = "myapp.cfapps.io/path";
    String path3 = "myapp2.cfapps.io";
    String space = "space";

    routes.add(path1);
    Route route = Route.builder().id("id").host("myapp").domain("cfapps.io").space(space).build();

    List<String> routeToBeCreated = cfSdkClient.findRoutesNeedToBeCreated(routes, asList(route));
    assertThat(routeToBeCreated).isNotNull();
    assertThat(routeToBeCreated).isEmpty();

    routes.clear();
    routes.add(path2);
    Route route1 = Route.builder().id("id").host("myapp").domain("cfapps.io").path("/path").space(space).build();
    routeToBeCreated = cfSdkClient.findRoutesNeedToBeCreated(routes, asList(route1));
    assertThat(routeToBeCreated).isNotNull();
    assertThat(routeToBeCreated).isEmpty();

    routes.clear();
    routes.add(path1);
    routes.add(path2);
    routeToBeCreated = cfSdkClient.findRoutesNeedToBeCreated(routes, asList(route1, route));
    assertThat(routeToBeCreated).isNotNull();
    assertThat(routeToBeCreated).isEmpty();

    routes.add(path3);
    routeToBeCreated = cfSdkClient.findRoutesNeedToBeCreated(routes, asList(route1, route));
    assertThat(routeToBeCreated).isNotNull();
    assertThat(routeToBeCreated).isNotEmpty();
    assertThat(routeToBeCreated.size()).isEqualTo(1);
    assertThat(routeToBeCreated.get(0)).isEqualTo(path3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testMapRoutesForApplication() throws Exception {
    String space = "space1";
    Route route = Route.builder().id("id").host("myapp").domain("cfapps.io").space(space).build();
    Route route1 = Route.builder().id("id").host("myapp").domain("cfapps.io").path("/path").space(space).build();

    doReturn(asList(route, route1)).when(cfSdkClient).getRouteMapsByNames(anyList(), any());
    doNothing().when(cfSdkClient).mapRouteMapForApp(any(), any());
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().spaceName(space).build();

    List<String> routes = new ArrayList<>();
    String path1 = "myapp.cfapps.io";
    String path2 = "myapp.cfapps.io/path";
    String path3 = "myapp2.cfapps.io/P1";
    routes.add(path1);
    routes.add(path2);
    // All mapping routes exists
    cfSdkClient.mapRoutesForApplication(cfRequestConfig, routes);
    verify(cfSdkClient, never()).getAllDomainsForSpace(any());

    routes.add(path3);
    doNothing().when(cfSdkClient).createRouteMap(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    doNothing().when(cfSdkClient).mapRouteMapForApp(any(), any());
    doReturn(asList(Domain.builder().id("id1").name("cfapps.io").status(Status.SHARED).build()))
        .when(cfSdkClient)
        .getAllDomainsForSpace(any());

    // 1 mapping route needs to be created
    cfSdkClient.mapRoutesForApplication(cfRequestConfig, routes);
    ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> domainCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(cfSdkClient)
        .createRouteMap(any(), hostCaptor.capture(), domainCaptor.capture(), pathCaptor.capture(), anyBoolean(),
            anyBoolean(), any());
    assertThat(hostCaptor.getValue()).isEqualTo("myapp2");
    assertThat(domainCaptor.getValue()).isEqualTo("cfapps.io");
    assertThat(pathCaptor.getValue()).isEqualTo("/P1");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testGetRecentLogs() throws PivotalClientApiException {
    final CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                                .applicationName("app_name")
                                                .orgName("org_name")
                                                .spaceName("space_name")
                                                .timeOutIntervalInMins(5)
                                                .build();
    final LogMessage logMessage1 =
        LogMessage.builder().message("msg1").messageType(MessageType.OUT).timestamp(1574924788770064207L).build();
    final LogMessage logMessage2 =
        LogMessage.builder().message("msg2").messageType(MessageType.OUT).timestamp(1574924788770064307L).build();
    Flux<LogMessage> result = Flux.create(sink -> {
      sink.next(logMessage1);
      sink.next(logMessage2);
      sink.complete();
    });
    when(applications.logs(any(LogsRequest.class))).thenReturn(result);
    final List<LogMessage> recentLogs = cfSdkClient.getRecentLogs(cfRequestConfig, 0);
    assertThat(recentLogs).containsExactly(logMessage1, logMessage2);
    assertThat(cfSdkClient.getRecentLogs(cfRequestConfig, 1574924788770064207L)).containsExactly(logMessage2);
  }

  @Test(expected = PivotalClientApiException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testGetRecentLogsFail() throws PivotalClientApiException {
    final CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                                .applicationName("app_name")
                                                .timeOutIntervalInMins(5)
                                                .spaceName("space_name")
                                                .orgName("org_name")
                                                .build();
    final LogMessage logMessage1 =
        LogMessage.builder().message("msg1").messageType(MessageType.OUT).timestamp(1574924788770064207L).build();
    Flux<LogMessage> result = Flux.create(sink -> {
      sink.next(logMessage1);
      sink.error(new RuntimeException("error while fetching"));
    });
    when(applications.logs(any(LogsRequest.class))).thenReturn(result);
    cfSdkClient.getRecentLogs(cfRequestConfig, 0);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateRouteFromPath() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(anyString());

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    Set<String> domains = new HashSet<>();
    domains.add("apps.io");
    domains.add("harness.io");
    domains.add("z.harness.io");

    try {
      cfSdkClient.createRouteFromPath("app1.cfapps1.io", cfRequestConfig, domains);
      fail("Exception was expected");
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
      assertThat(e.getMessage().contains("used domain not present in this space")).isTrue();
    }

    doNothing().when(cfSdkClient).createRouteMap(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    cfSdkClient.createRouteFromPath("app1.apps.io", cfRequestConfig, domains);
    ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> domainCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);

    verify(cfSdkClient)
        .createRouteMap(any(), hostCaptor.capture(), domainCaptor.capture(), pathCaptor.capture(), anyBoolean(),
            anyBoolean(), any());
    assertThat(hostCaptor.getValue()).isEqualTo("app1");
    assertThat(domainCaptor.getValue()).isEqualTo("apps.io");
    assertThat(isBlank(pathCaptor.getValue())).isTrue();

    reset(cfSdkClient);
    doNothing()
        .when(cfSdkClient)
        .createRouteMap(any(), any(), anyString(), anyString(), anyBoolean(), anyBoolean(), any());
    cfSdkClient.createRouteFromPath("app1.apps.io/inside/display.jsp", cfRequestConfig, domains);
    hostCaptor = ArgumentCaptor.forClass(String.class);
    domainCaptor = ArgumentCaptor.forClass(String.class);
    pathCaptor = ArgumentCaptor.forClass(String.class);

    verify(cfSdkClient)
        .createRouteMap(any(), hostCaptor.capture(), domainCaptor.capture(), pathCaptor.capture(), anyBoolean(),
            anyBoolean(), any());
    assertThat(hostCaptor.getValue()).isEqualTo("app1");
    assertThat(domainCaptor.getValue()).isEqualTo("apps.io");
    assertThat(pathCaptor.getValue()).isEqualTo("/inside/display.jsp");

    reset(cfSdkClient);
    doNothing().when(cfSdkClient).createRouteMap(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    cfSdkClient.createRouteFromPath("cdp-10128.z.harness.io", cfRequestConfig, domains);
    hostCaptor = ArgumentCaptor.forClass(String.class);
    domainCaptor = ArgumentCaptor.forClass(String.class);
    pathCaptor = ArgumentCaptor.forClass(String.class);

    verify(cfSdkClient)
        .createRouteMap(any(), hostCaptor.capture(), domainCaptor.capture(), pathCaptor.capture(), anyBoolean(),
            anyBoolean(), any());
    assertThat(hostCaptor.getValue()).isEqualTo("cdp-10128");
    assertThat(domainCaptor.getValue()).isEqualTo("z.harness.io");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetAllDomainsForSpace() throws Exception {
    when(cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(getCfRequestConfig())
             .getCloudFoundryOperations()
             .domains())
        .thenReturn(domains);

    Domain domain1 = Domain.builder().id("id1").name("cfapps.io").status(Status.SHARED).build();
    Domain domain2 = Domain.builder().id("id2").name("harness.io").status(Status.SHARED).build();

    Flux<Domain> result = Flux.create(sink -> {
      sink.next(domain1);
      sink.next(domain2);
      sink.complete();
    });

    when(domains.list()).thenReturn(result);
    List<String> domainList = cfSdkClient.getAllDomainsForSpace(getCfRequestConfig())
                                  .stream()
                                  .map(domainElement -> domainElement.getName())
                                  .collect(toList());

    assertThat(domainList).isNotNull();
    assertThat(domainList).containsExactly("cfapps.io", "harness.io");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateRouteMap() throws PivotalClientApiException, InterruptedException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                          .applicationName("app")
                                          .userName("username")
                                          .password("password")
                                          .spaceName("space")
                                          .orgName("org")
                                          .spaceName("space")
                                          .build();

    CreateRouteRequest createRouteRequest = CreateRouteRequest.builder()
                                                .host("host")
                                                .domain("domain")
                                                .path("path")
                                                .randomPort(null)
                                                .port(null)
                                                .space(cfRequestConfig.getSpaceName())
                                                .build();
    try {
      cfSdkClient.createRouteMap(cfRequestConfig, "host", "domain", "path", false, false, null);
    } catch (Exception e) {
      ArgumentCaptor<CreateRouteRequest> createRouteRequestArgumentCaptor =
          ArgumentCaptor.forClass(CreateRouteRequest.class);
      verify(wrapper.getCloudFoundryOperations().routes()).create(createRouteRequestArgumentCaptor.capture());
      assertThat(createRouteRequestArgumentCaptor.getValue()).isEqualTo(createRouteRequest);
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testAddRouteMapsToManifestRoutesArePresent() throws Exception {
    List<String> routes = Arrays.asList("qa.harness.io/api", "app.harness.io");
    List<org.cloudfoundry.operations.applications.Route> routesList =
        routes.stream()
            .map(routeMap -> org.cloudfoundry.operations.applications.Route.builder().route(routeMap).build())
            .collect(toList());
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                          .timeOutIntervalInMins(1)
                                          .orgName("org")
                                          .applicationName("app")
                                          .routeMaps(routes)
                                          .build();
    ApplicationManifest.Builder builder = ApplicationManifest.builder();
    builder.name("builder");

    cfSdkClient.addRouteMapsToManifest(cfRequestConfig, builder);

    ApplicationManifest applicationManifest = builder.build();
    assertThat(applicationManifest.getRoutes()).isEqualTo(routesList);
  }

  private CfRequestConfig getCfRequestConfig() {
    return CfRequestConfig.builder().timeOutIntervalInMins(1).orgName("org").applicationName("app").build();
  }

  private void clearProperties() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("http.nonProxyHosts");
  }
}
