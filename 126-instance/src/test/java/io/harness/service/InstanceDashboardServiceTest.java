/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.rule.OwnerRule.JASMEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.models.BuildsByEnvironment;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstanceDTOsByBuildId;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancedashboardservice.InstanceDashboardService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DX)
public class InstanceDashboardServiceTest extends InstancesTestBase {
  @Inject private InstanceDashboardService instanceDashboardService;
  @Inject @Mock private InstanceService instanceService;
  @Inject @Mock private InstanceRepository instanceRepository;
  private static final String ACCOUNT_IDENTIFIER = "ACCOUNT_IDENTIFIER";
  private static final String ORG_IDENTIFIER = "ORG_IDENTIFIER";
  private static final String PROJECT_IDENTIFIER = "PROJECT_IDENTIFIER";
  private static final String SERVICE_IDENTIFIER = "SERVICE_IDENTIFIER";
  private static final EnvironmentType defaultEnvType = EnvironmentType.PreProduction;

  private Instance createDummyInstance(String envId, String tag, EnvironmentType envType) {
    return Instance.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .serviceIdentifier(SERVICE_IDENTIFIER)
        .envIdentifier(envId)
        .envName("envName")
        .envType(envType)
        .infrastructureKind(KUBERNETES_DIRECT)
        .primaryArtifact(ArtifactDetails.builder().tag(tag).build())
        .createdAt(0L)
        .deletedAt(10L)
        .createdAt(0L)
        .lastModifiedAt(0L)
        .instanceInfo(K8sInstanceInfo.builder().podName("podName").releaseName("releaseName").build())
        .build();
  }

  @Test
  @Owner(developers = JASMEET)
  @Category(UnitTests.class)
  public void getActiveInstancesGroupedByEnvironmentAndBuild() {
    long currentTimestampInMs = 5L;
    for (int i = 0; i < 40; i++) {
      instanceRepository.save(createDummyInstance(String.valueOf(i % 5), String.valueOf((i / 5) % 4), defaultEnvType));
    }

    List<BuildsByEnvironment> environments =
        instanceDashboardService.getActiveInstancesByServiceIdGroupedByEnvironmentAndBuild(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, currentTimestampInMs);

    assertThat(environments.size()).isEqualTo(5);
    for (BuildsByEnvironment buildsByEnv : environments) {
      List<InstanceDTOsByBuildId> instanceByBuilds = buildsByEnv.getBuilds();
      assertThat(instanceByBuilds.size()).isEqualTo(4);
      for (InstanceDTOsByBuildId instanceByBuild : instanceByBuilds) {
        assertThat(instanceByBuild.getInstances().size()).isEqualTo(2);
      }
    }
  }

  @Test
  @Owner(developers = JASMEET)
  @Category(UnitTests.class)
  public void getUniqueEnvIdBuildIdCombinationsWithInstanceCount() {
    Map<String, Map<String, Integer>> mock = new HashMap<>();
    List<Pair<Pair<String, String>, Integer>> mockList = Arrays.asList(Pair.of(Pair.of("envId1", "buildId1"), 3),
        Pair.of(Pair.of("envId1", "buildId2"), 2), Pair.of(Pair.of("envId2", "buildId1"), 2),
        Pair.of(Pair.of("envId2", "buildId2"), 5), Pair.of(Pair.of("envId3", "buildId3"), 3));

    mockList.forEach(mockListItem -> {
      final String envId = mockListItem.getLeft().getLeft();
      final String buildId = mockListItem.getLeft().getRight();
      final Integer count = mockListItem.getRight();
      if (!mock.containsKey(envId)) {
        mock.put(envId, new HashMap<>());
      }
      mock.get(envId).put(buildId, count);
      for (int i = 0; i < count; i++) {
        instanceRepository.save(createDummyInstance(envId, buildId, defaultEnvType));
      }
    });

    List<EnvBuildInstanceCount> uniqueEnvIdBuildIdCombinationsWithInstanceCounts =
        instanceDashboardService.getEnvBuildInstanceCountByServiceId(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, 5);
    assertThat(uniqueEnvIdBuildIdCombinationsWithInstanceCounts.size()).isGreaterThan(0);
    uniqueEnvIdBuildIdCombinationsWithInstanceCounts.forEach(uniqueEnvIdBuildIdCombinationsWithInstanceCount -> {
      final String envId = uniqueEnvIdBuildIdCombinationsWithInstanceCount.getEnvIdentifier();
      final String buildId = uniqueEnvIdBuildIdCombinationsWithInstanceCount.getTag();
      final int count = uniqueEnvIdBuildIdCombinationsWithInstanceCount.getCount();
      final int expectedCount = mock.getOrDefault(envId, new HashMap<>()).getOrDefault(buildId, 0);
      assertThat(count).isEqualTo(expectedCount);
    });
  }

  @Test
  @Owner(developers = JASMEET)
  @Category(UnitTests.class)
  public void getInstancesByEnvIdAndBuildIds() {
    List<Pair<Pair<String, String>, Integer>> mockList =
        Arrays.asList(Pair.of(Pair.of("envId1", "buildId1"), 30), Pair.of(Pair.of("envId1", "buildId2"), 30),
            Pair.of(Pair.of("envId2", "buildId1"), 30), Pair.of(Pair.of("envId2", "buildId2"), 30));

    mockList.forEach(mockListItem -> {
      final String envId = mockListItem.getLeft().getLeft();
      final String buildId = mockListItem.getLeft().getRight();
      final Integer count = mockListItem.getRight();
      for (int i = 0; i < count; i++) {
        instanceRepository.save(createDummyInstance(envId, buildId, defaultEnvType));
      }
    });

    String inputEnvName = "envId1";
    List<String> inputBuildIds = Arrays.asList("buildId1", "buildId2");
    List<InstanceDetailsByBuildId> result;

    // Invalid cases that should return no results

    result = instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "randomServiceId", inputEnvName, inputBuildIds, 5);
    assertThat(result.size()).isEqualTo(0);

    result = instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, "randomEnvName", inputBuildIds, 5);
    assertThat(result.size()).isEqualTo(0);

    result = instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
        PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, inputEnvName, Arrays.asList("randomBuildId"), 5);
    assertThat(result.size()).isEqualTo(0);

    // Valid case

    result = instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, inputEnvName, inputBuildIds, 5);
    assertThat(result.size()).isEqualTo(2);

    for (int i = 0; i < inputBuildIds.size(); i++) {
      assertThat(result.get(i).getBuildId()).isEqualTo(inputBuildIds.get(i));
      assertThat(result.get(i).getInstances().size()).isEqualTo(20);
    }
  }

  @Test
  @Owner(developers = JASMEET)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceCountBreakdown() {
    List<Pair<Pair<String, String>, EnvironmentType>> mockList =
        Arrays.asList(Pair.of(Pair.of("envId1", "buildId1"), EnvironmentType.PreProduction),
            Pair.of(Pair.of("envId1", "buildId2"), EnvironmentType.PreProduction),
            Pair.of(Pair.of("envId2", "buildId1"), EnvironmentType.PreProduction),
            Pair.of(Pair.of("envId2", "buildId2"), EnvironmentType.Production));

    mockList.forEach(mockListItem -> {
      final String envId = mockListItem.getLeft().getLeft();
      final String buildId = mockListItem.getLeft().getRight();
      final EnvironmentType envType = mockListItem.getRight();
      instanceRepository.save(createDummyInstance(envId, buildId, envType));
    });

    InstanceCountDetailsByEnvTypeBase instanceCountDetailsByEnvTypeBase =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Arrays.asList(SERVICE_IDENTIFIER), 5)
            .getInstanceCountDetailsByEnvTypeBaseMap()
            .get(SERVICE_IDENTIFIER);
    assertThat(instanceCountDetailsByEnvTypeBase.getTotalInstances()).isEqualTo(mockList.size());
    assertThat(instanceCountDetailsByEnvTypeBase.getNonProdInstances()).isEqualTo(3);
    assertThat(instanceCountDetailsByEnvTypeBase.getProdInstances()).isEqualTo(1);
  }
}
