/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.change.ChangeTimeline.TimeRangeDetail;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeEventResourceTest extends CvNextGenTestBase {
  private static ChangeEventResource changeEventResource = new ChangeEventResource();
  @Inject private Injector injector;
  @Inject private HPersistence hPersistence;
  @Inject private MonitoredServiceService monitoredServiceService;
  BuilderFactory builderFactory = BuilderFactory.getDefault();

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(changeEventResource).build();

  @SneakyThrows
  @Before
  public void setup() {
    injector.injectMembers(changeEventResource);
    monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetPaginated() {
    hPersistence.save(getActivities());
    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()))
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("serviceIdentifiers", builderFactory.getContext().getServiceIdentifier())
                            .queryParam("envIdentifiers", builderFactory.getContext().getEnvIdentifier())
                            .queryParam("changeCategories", "Deployment", "Alert")
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 2)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    PageResponse<ChangeEventDTO> firstPage =
        response.readEntity(new GenericType<RestResponse<PageResponse<ChangeEventDTO>>>() {}).getResource();
    assertThat(firstPage.getPageIndex()).isEqualTo(0);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getTotalItems()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getContent().size()).isEqualTo(2);
    assertThat(firstPage.getContent().get(0).getEventTime()).isEqualTo(300000);
    assertThat(firstPage.getContent().get(1).getEventTime()).isEqualTo(101000);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPaginatedWithMonitoredServiceIdentifier() {
    hPersistence.save(getActivities());
    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()))
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("monitoredServiceIdentifiers",
                                builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())
                            .queryParam("changeCategories", "Deployment", "Alert")
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 2)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    PageResponse<ChangeEventDTO> firstPage =
        response.readEntity(new GenericType<RestResponse<PageResponse<ChangeEventDTO>>>() {}).getResource();
    assertThat(firstPage.getPageIndex()).isEqualTo(0);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getTotalItems()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getContent().size()).isEqualTo(2);
    assertThat(firstPage.getContent().get(0).getEventTime()).isEqualTo(300000);
    assertThat(firstPage.getContent().get(1).getEventTime()).isEqualTo(101000);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPaginatedInvalidQueryParams() {
    hPersistence.save(getActivities());
    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()))
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("monitoredServiceIdentifiers",
                                builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())
                            .queryParam("serviceIdentifiers", builderFactory.getContext().getServiceIdentifier())
                            .queryParam("changeCategories", "Deployment", "Alert")
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 2)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class))
        .contains(
            "java.lang.IllegalStateException: serviceIdentifier, envIdentifier filter can not be used with monitoredServiceIdentifier filter");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetChangeSummary() {
    hPersistence.save(Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build()));

    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()) + "/summary")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("serviceIdentifiers", builderFactory.getContext().getServiceIdentifier())
                            .queryParam("envIdentifiers", builderFactory.getContext().getEnvIdentifier())
                            .queryParam("changeCategories", "Deployment", "Alert")
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    ChangeSummaryDTO changeSummaryDTO =
        response.readEntity(new GenericType<RestResponse<ChangeSummaryDTO>>() {}).getResource();

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetTimeline() {
    hPersistence.save(Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(109)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build()));

    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()) + "/timeline")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("serviceIdentifiers", builderFactory.getContext().getServiceIdentifier())
                            .queryParam("envIdentifiers", builderFactory.getContext().getEnvIdentifier())
                            .queryParam("changeCategories", "Deployment", "Alert")
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 4900000)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    ChangeTimeline changeTimeline =
        response.readEntity(new GenericType<RestResponse<ChangeTimeline>>() {}).getResource();

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(100000);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(200000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(400000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges).isNull();
    List<TimeRangeDetail> alertChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.ALERTS);
    assertThat(alertChanges).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceChangeTimeline_withMonitoredServiceFilter() {
    hPersistence.save(Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(500))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(14398)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(14399500))
            .build()));

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/change-event/monitored-service-timeline")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("monitoredServiceIdentifier",
                                builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("duration", DurationDTO.FOUR_HOURS)
                            .queryParam("endTime", 14300000)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    ChangeTimeline changeTimeline =
        response.readEntity(new GenericType<RestResponse<ChangeTimeline>>() {}).getResource();

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(14100000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(14400000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.size()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(300000);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetChangeSummaryForMonitoredService() {
    hPersistence.save(Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build()));

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/change-event/monitored-service-summary")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("monitoredServiceIdentifier",
                                builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())
                            .queryParam("changeCategories", "Deployment", "Alert")
                            .queryParam("changeSourceTypes", "HarnessCDNextGen", "K8sCluster")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    ChangeSummaryDTO changeSummaryDTO =
        response.readEntity(new GenericType<RestResponse<ChangeSummaryDTO>>() {}).getResource();

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetChangeEventDetail_forK8_withDependentService() {
    hPersistence.save(getActivities());
    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()))
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("serviceIdentifiers", builderFactory.getContext().getServiceIdentifier())
                            .queryParam("envIdentifiers", builderFactory.getContext().getEnvIdentifier())
                            .queryParam("changeCategories", "Infrastructure")
                            .queryParam("changeSourceTypes", "KUBERNETES")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 2)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    PageResponse<ChangeEventDTO> firstPage =
        response.readEntity(new GenericType<RestResponse<PageResponse<ChangeEventDTO>>>() {}).getResource();

    String activityId = firstPage.getContent().get(0).getId();
    response = RESOURCES.client()
                   .target(getChangeEventPath(builderFactory.getContext().getProjectParams()) + "/" + activityId)
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .get();
    assertThat(response.getStatus()).isEqualTo(200);
    String jsonResponse = response.readEntity(String.class);
    assertThat(jsonResponse).contains("\"name\":\"K8 Activity\"");
    assertThat(jsonResponse).contains("\"dependentMonitoredService\":\"dependent_service\"");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetChangeEventDetail_forK8_withoutDependentService() {
    hPersistence.save(
        Arrays.asList(builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .relatedAppServices(Arrays.asList())
                .eventTime(Instant.ofEpochSecond(50))
                .build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
            builderFactory.getDeploymentActivityBuilder()
                .monitoredServiceIdentifier("service_env2")
                .eventTime(Instant.ofEpochSecond(200))
                .build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(101)).build(),
            builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .relatedAppServices(Arrays.asList())
                .eventTime(Instant.ofEpochSecond(300))
                .build()));
    Response response = RESOURCES.client()
                            .target(getChangeEventPath(builderFactory.getContext().getProjectParams()))
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("serviceIdentifiers", builderFactory.getContext().getServiceIdentifier())
                            .queryParam("envIdentifiers", builderFactory.getContext().getEnvIdentifier())
                            .queryParam("changeCategories", "Infrastructure")
                            .queryParam("changeSourceTypes", "KUBERNETES")
                            .queryParam("startTime", 100000)
                            .queryParam("endTime", 400000)
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 2)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    PageResponse<ChangeEventDTO> firstPage =
        response.readEntity(new GenericType<RestResponse<PageResponse<ChangeEventDTO>>>() {}).getResource();

    String activityId = firstPage.getContent().get(0).getId();
    response = RESOURCES.client()
                   .target(getChangeEventPath(builderFactory.getContext().getProjectParams()) + "/" + activityId)
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .get();
    assertThat(response.getStatus()).isEqualTo(200);
    String jsonResponse = response.readEntity(String.class);
    assertThat(jsonResponse).contains("\"name\":\"K8 Activity\"");
    assertThat(jsonResponse).contains("\"dependentMonitoredService\":");
  }

  private String getChangeEventPath(ProjectParams projectParams) {
    return "http://localhost:9998/account/" + projectParams.getAccountIdentifier() + "/org/"
        + projectParams.getOrgIdentifier() + "/project/" + projectParams.getProjectIdentifier() + "/change-event";
  }

  private List<Activity> getActivities() {
    return Arrays.asList(builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(101)).build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
  }
}
