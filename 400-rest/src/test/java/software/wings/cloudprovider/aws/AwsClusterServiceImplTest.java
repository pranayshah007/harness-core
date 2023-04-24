/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.AUTO_SCALING_GROUP_NAME;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.LAUNCHER_TEMPLATE_NAME;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SERVICE_DEFINITION;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME_PREFIX;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;

import com.amazonaws.regions.Regions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 1/3/17.
 */
@OwnedBy(CDP)
public class AwsClusterServiceImplTest extends WingsBaseTest {
  @Mock private EcsContainerService ecsContainerService;

  @Inject @InjectMocks private AwsClusterService awsClusterService;

  private SettingAttribute cloudProviderSetting =
      aSettingAttribute()
          .withValue(AwsConfig.builder().accessKey(ACCESS_KEY.toCharArray()).secretKey(SECRET_KEY).build())
          .build();
  private AwsClusterConfiguration clusterConfiguration = AwsClusterConfiguration.builder()
                                                             .name(CLUSTER_NAME)
                                                             .size(5)
                                                             .serviceDefinition(SERVICE_DEFINITION)
                                                             .launcherConfiguration(LAUNCHER_TEMPLATE_NAME)
                                                             .autoScalingGroupName(AUTO_SCALING_GROUP_NAME)
                                                             .vpcZoneIdentifiers("VPC_ZONE_1, VPC_ZONE_2")
                                                             .availabilityZones(asList("AZ1", "AZ2"))
                                                             .build();

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCreateCluster() {
    awsClusterService.createCluster(
        Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), clusterConfiguration, null);

    ImmutableMap<String, Object> params =
        ImmutableMap.of("autoScalingGroupName", AUTO_SCALING_GROUP_NAME, "clusterName", CLUSTER_NAME,
            "availabilityZones", asList("AZ1", "AZ2"), "vpcZoneIdentifiers", "VPC_ZONE_1, VPC_ZONE_2");

    verify(ecsContainerService)
        .provisionNodes(Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), 5,
            LAUNCHER_TEMPLATE_NAME, params, null);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldResizeCluster() {
    awsClusterService.resizeCluster(Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(),
        CLUSTER_NAME, SERVICE_NAME, 0, 5, 10, new ExecutionLogCallback(), false);
    verify(ecsContainerService)
        .provisionTasks(eq(Regions.US_EAST_1.getName()), eq(cloudProviderSetting.toDTO()), eq(Collections.emptyList()),
            eq(CLUSTER_NAME), eq(SERVICE_NAME), eq(0), eq(5), eq(10), any(ExecutionLogCallback.class));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldDeleteService() {
    awsClusterService.deleteService(
        Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), CLUSTER_NAME, SERVICE_NAME);
    verify(ecsContainerService)
        .deleteService(Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), CLUSTER_NAME,
            SERVICE_NAME);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetService() {
    awsClusterService.getService(
        Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), CLUSTER_NAME, SERVICE_NAME);
    verify(ecsContainerService)
        .getService(Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), CLUSTER_NAME,
            SERVICE_NAME);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetServices() {
    awsClusterService.getServices(Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(),
        CLUSTER_NAME, SERVICE_NAME_PREFIX);
    verify(ecsContainerService)
        .getServices(Regions.US_EAST_1.getName(), cloudProviderSetting.toDTO(), Collections.emptyList(), CLUSTER_NAME,
            SERVICE_NAME_PREFIX);
  }
}
