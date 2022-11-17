package io.harness.ng.overview.service;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.models.ActiveServiceInstanceInfoWithoutEnvWithServiceDetails;
import io.harness.ng.overview.dto.InstanceGroupedByArtifactList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.rule.Owner;
import io.harness.service.instancedashboardservice.InstanceDashboardServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class CDOverviewDashboardServiceImplTest extends CategoryTest {
  @InjectMocks private CDOverviewDashboardServiceImpl cdOverviewDashboardService;
  @Mock private InstanceDashboardServiceImpl instanceDashboardService;

  InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure getSampleInstanceGroupedByInfrastructure(
      String infraId) {
    return InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure.builder()
        .infraIdentifier(infraId)
        .infraName(infraId)
        .lastPipelineExecutionId("id1")
        .lastPipelineExecutionName("id1")
        .lastDeployedAt("0")
        .count(2)
        .build();
  }

  Map<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>>
  getSampleServiceBuildInfraMap() {
    Map<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>> serviceBuildInfraMap =
        new HashMap<>();

    Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>> buildInfraMap = new HashMap<>();
    buildInfraMap.put("1",
        Arrays.asList(
            getSampleInstanceGroupedByInfrastructure("infra1"), getSampleInstanceGroupedByInfrastructure("infra2")));
    buildInfraMap.put("2",
        Arrays.asList(
            getSampleInstanceGroupedByInfrastructure("infra3"), getSampleInstanceGroupedByInfrastructure("infra2")));

    serviceBuildInfraMap.put("svc1", buildInfraMap);
    serviceBuildInfraMap.put("svc2", buildInfraMap);

    return serviceBuildInfraMap;
  }

  List<InstanceGroupedByServiceList.InstanceGroupedByService> getSampleListInstanceGroupedByService() {
    InstanceGroupedByArtifactList.InstanceGroupedByEnvironment instanceGroupedByEnvironment1 =
        InstanceGroupedByArtifactList.InstanceGroupedByEnvironment.builder()
            .instanceGroupedByInfraList(Arrays.asList(
                getSampleInstanceGroupedByInfrastructure("infra1"), getSampleInstanceGroupedByInfrastructure("infra2")))
            .build();
    InstanceGroupedByArtifactList.InstanceGroupedByEnvironment instanceGroupedByEnvironment2 =
        InstanceGroupedByArtifactList.InstanceGroupedByEnvironment.builder()
            .instanceGroupedByInfraList(Arrays.asList(
                getSampleInstanceGroupedByInfrastructure("infra3"), getSampleInstanceGroupedByInfrastructure("infra2")))
            .build();

    InstanceGroupedByArtifactList.InstanceGroupedByArtifact instanceGroupedByArtifact1 =
        InstanceGroupedByArtifactList.InstanceGroupedByArtifact.builder()
            .artifactPath("artifact1")
            .artifactVersion("1")
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment1))
            .build();
    InstanceGroupedByArtifactList.InstanceGroupedByArtifact instanceGroupedByArtifact2 =
        InstanceGroupedByArtifactList.InstanceGroupedByArtifact.builder()
            .artifactPath("artifact2")
            .artifactVersion("2")
            .instanceGroupedByEnvironmentList(Arrays.asList(instanceGroupedByEnvironment2))
            .build();

    InstanceGroupedByArtifactList instanceGroupedByArtifactList =
        InstanceGroupedByArtifactList.builder()
            .instanceGroupedByArtifactList(Arrays.asList(instanceGroupedByArtifact1, instanceGroupedByArtifact2))
            .build();

    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService1 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc1")
            .serviceName("svcN1")
            .instanceGroupedByArtifactList(instanceGroupedByArtifactList)
            .build();
    InstanceGroupedByServiceList.InstanceGroupedByService instanceGroupedByService2 =
        InstanceGroupedByServiceList.InstanceGroupedByService.builder()
            .serviceId("svc2")
            .serviceName("svcN2")
            .instanceGroupedByArtifactList(instanceGroupedByArtifactList)
            .build();

    return (Arrays.asList(instanceGroupedByService2, instanceGroupedByService1));
  }

  List<ActiveServiceInstanceInfoWithoutEnvWithServiceDetails>
  getSampleListActiveServiceInstanceInfoWithoutEnvWithServiceDetails() {
    List<ActiveServiceInstanceInfoWithoutEnvWithServiceDetails>
        activeServiceInstanceInfoWithoutEnvWithServiceDetailsList = new ArrayList<>();
    ActiveServiceInstanceInfoWithoutEnvWithServiceDetails instance1 =
        new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
            "svc1", "svcN1", "infra1", "infra1", "1", "a", "1", "1", "artifact1", 1);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc1", "svcN1", "infra2", "infra2", "2", "b", "2", "1", "artifact1", 2);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc1", "svcN1", "infra1", "infra1", "1", "a", "1", "2", "artifact2", 1);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc1", "svcN1", "infra2", "infra2", "2", "b", "2", "2", "artifact2", 2);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc2", "svcN2", "infra1", "infra1", "1", "a", "1", "1", "artifact1", 1);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc2", "svcN2", "infra2", "infra2", "2", "b", "2", "1", "artifact1", 2);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc2", "svcN2", "infra1", "infra1", "1", "a", "1", "2", "artifact2", 1);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);
    instance1 = new ActiveServiceInstanceInfoWithoutEnvWithServiceDetails(
        "svc2", "svcN2", "infra2", "infra2", "2", "b", "2", "2", "artifact2", 2);
    activeServiceInstanceInfoWithoutEnvWithServiceDetailsList.add(instance1);

    return activeServiceInstanceInfoWithoutEnvWithServiceDetailsList;
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_groupByServices() {
    Map<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>> serviceBuildInfraMap =
        getSampleServiceBuildInfraMap();
    Map<String, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> buildIdToArtifactPathMap = new HashMap<>();

    serviceIdToServiceNameMap.put("svc1", "svcN1");
    serviceIdToServiceNameMap.put("svc2", "svcN2");

    buildIdToArtifactPathMap.put("1", "artifact1");
    buildIdToArtifactPathMap.put("2", "artifact2");

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices =
        getSampleListInstanceGroupedByService();

    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServices1 =
        CDOverviewDashboardServiceImpl.groupedByServices(
            serviceBuildInfraMap, serviceIdToServiceNameMap, buildIdToArtifactPathMap);

    assertThat(instanceGroupedByServices1).isEqualTo(instanceGroupedByServices);
  }

  /*
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getInstanceGroupedByServiceList() {
      Mockito.when(instanceDashboardService.getActiveServiceInstanceInfoWithoutEnvWithServiceDetails(anyString(),anyString(),anyString(),anyString())).thenReturn(getSampleListActiveServiceInstanceInfoWithoutEnvWithServiceDetails());
      InstanceGroupedByServiceList instanceGroupedByServiceList =
  InstanceGroupedByServiceList.builder().instanceGroupedByServices(getSampleListInstanceGroupedByService()).build();
      assertThat(instanceGroupedByServiceList).isEqualTo(cdOverviewDashboardService.getInstanceGroupedByServiceList("","","",""));
  }
   */
}