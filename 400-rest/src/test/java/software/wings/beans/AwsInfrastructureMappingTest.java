/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType.AWS_AUTOSCALING_GROUP;
import static software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType.AWS_ECS_EC2;
import static software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType.AWS_INSTANCE_FILTER;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsInfrastructureMapping.AwsInfrastructureMappingKeys;
import software.wings.beans.AwsInfrastructureMapping.Builder;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsInfrastructureMappingTest extends WingsBaseTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testInfrastructureMapping() {
    Map<String, Object> map = new HashMap<>();

    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping().build();

    assertThatThrownBy(() -> awsInfrastructureMapping.applyProvisionerVariables(map, AWS_INSTANCE_FILTER, false))
        .isInstanceOf(InvalidRequestException.class);

    map.put("region", "dummy-region");
    awsInfrastructureMapping.applyProvisionerVariables(map, AWS_INSTANCE_FILTER, false);
    assertThat(awsInfrastructureMapping.getRegion()).isEqualTo("dummy-region");
    assertThat(awsInfrastructureMapping.isProvisionInstances()).isFalse();

    map.put("vpcs", asList("dummy-vpc"));
    map.put("tags", ImmutableMap.<String, Object>of("key", "value"));

    awsInfrastructureMapping.applyProvisionerVariables(map, AWS_INSTANCE_FILTER, false);
    assertThat(awsInfrastructureMapping.isProvisionInstances()).isFalse();
    assertThat(awsInfrastructureMapping.getRegion()).isEqualTo("dummy-region");
    assertThat(awsInfrastructureMapping.getAwsInstanceFilter().getVpcIds()).containsExactly("dummy-vpc");
    assertThat(awsInfrastructureMapping.getAwsInstanceFilter().getTags().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testASGNameAndLBName() {
    Map<String, Object> map = new HashMap<>();
    map.put("region", "dummy-region");
    map.put("autoScalingGroup", "my-group");
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping().build();
    awsInfrastructureMapping.applyProvisionerVariables(map, AWS_AUTOSCALING_GROUP, false);
    assertThat(awsInfrastructureMapping.isProvisionInstances()).isTrue();
    assertThat(awsInfrastructureMapping.getRegion()).isEqualTo("dummy-region");
    assertThat(awsInfrastructureMapping.getAutoScalingGroupName()).isEqualTo("my-group");

    final Map<String, Object> map2 = new HashMap<>();
    final AwsInfrastructureMapping awsInfrastructureMapping2 = anAwsInfrastructureMapping().build();
    assertThatThrownBy(() -> awsInfrastructureMapping2.applyProvisionerVariables(map2, AWS_AUTOSCALING_GROUP, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testChangeOfFilterType() {
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping().build();
    Map<String, Object> map = new HashMap<>();
    map.put("region", "dummy-region");
    awsInfrastructureMapping.applyProvisionerVariables(map, AWS_INSTANCE_FILTER, false);
    assertThat(awsInfrastructureMapping.getAutoScalingGroupName()).isNull();
    assertThat(awsInfrastructureMapping.isProvisionInstances()).isFalse();
    map.put("autoScalingGroup", "my-group");
    awsInfrastructureMapping.applyProvisionerVariables(map, AWS_AUTOSCALING_GROUP, false);
    assertThat(awsInfrastructureMapping.getAwsInstanceFilter()).isNull();
    assertThat(awsInfrastructureMapping.isProvisionInstances()).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testEcs() {
    Map<String, Object> map = new HashMap<>();
    map.put("region", "dummy-region");
    map.put("ecsCluster", "my-cluster");
    EcsInfrastructureMapping ecsInfrastructureMapping = anEcsInfrastructureMapping().build();
    ecsInfrastructureMapping.applyProvisionerVariables(map, AWS_ECS_EC2, false);
    assertThat(ecsInfrastructureMapping.getLaunchType()).isEqualTo("EC2");
    assertThat(ecsInfrastructureMapping.getRegion()).isEqualTo("dummy-region");
    assertThat(ecsInfrastructureMapping.getClusterName()).isEqualTo("my-cluster");

    final Map<String, Object> map2 = new HashMap<>();
    final EcsInfrastructureMapping ecsInfrastructureMapping1 = anEcsInfrastructureMapping().build();
    assertThatThrownBy(() -> ecsInfrastructureMapping1.applyProvisionerVariables(map2, AWS_ECS_EC2, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testApplyProvisionerVariables() {
    AwsInfrastructureMapping infrastructureMapping = Builder.anAwsInfrastructureMapping().build();
    Map<String, Object> resolvedBlueprints = new HashMap<>();
    try {
      infrastructureMapping.applyProvisionerVariables(resolvedBlueprints);
      fail("Should have thrown exception");
    } catch (WingsException ex) {
      assertThat(ExceptionUtils.getMessage(ex).contains("Region")).isTrue();
    }

    resolvedBlueprints.put("randomKey", "val");
    try {
      infrastructureMapping.applyProvisionerVariables(resolvedBlueprints);
      fail("Should have thrown exception");
    } catch (WingsException ex) {
      assertThat(ExceptionUtils.getMessage(ex).contains("Unknown blueprint field ")).isTrue();
    }

    resolvedBlueprints.remove("randomKey");
    resolvedBlueprints.put(AwsInfrastructureMappingKeys.region, "us-east-1");
    infrastructureMapping.setProvisionInstances(true);
    try {
      infrastructureMapping.applyProvisionerVariables(resolvedBlueprints);
      fail("Should have thrown exception");
    } catch (WingsException ex) {
      assertThat(ExceptionUtils.getMessage(ex).contains("Auto scaling group ")).isTrue();
    }

    resolvedBlueprints.put(AwsInfrastructureMappingKeys.autoScalingGroupName, "asg");
    infrastructureMapping.applyProvisionerVariables(resolvedBlueprints);
    assertThat(infrastructureMapping.getAutoScalingGroupName().equals("asg")).isTrue();

    infrastructureMapping.setProvisionInstances(false);
    resolvedBlueprints.put(AwsInfrastructureMappingKeys.awsInstanceFilter, new HashMap<String, Object>() {
      {
        put("vpcIds", Arrays.asList("vpc1", "vpc2"));
        put("tags", new HashMap<String, String>() {
          { put("key", "val1"); }
        });
      }
    });
    infrastructureMapping.applyProvisionerVariables(resolvedBlueprints);
    assertThat(infrastructureMapping.getAwsInstanceFilter() != null
        && infrastructureMapping.getAwsInstanceFilter().getTags() != null
        && infrastructureMapping.getAwsInstanceFilter().getVpcIds() != null)
        .isTrue();
  }
}
