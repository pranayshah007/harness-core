/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANUBHAW;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.cloudprovider.aws.AwsClusterConfiguration;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.cloudprovider.aws.EcsContainerServiceImpl;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.regions.Regions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.text.StrSubstitutor;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by anubhaw on 12/29/16.
 */
@OwnedBy(CDP)
public class AwsClusterServiceIntegrationTest extends WingsBaseTest {
  @Inject private AwsClusterService awsClusterService;
  @Inject private EcsContainerService ecsContainerService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private ScmSecret scmSecret;
  private SettingAttribute awsConnectorSetting;

  private String serviceJson =
      "{\"cluster\":\"${CLUSTER_NAME}_${SERVICE_VERSION}\",\"desiredCount\":\"${CLUSTER_SIZE}\",\"serviceName\":\"${SERVICE_NAME}_${SERVICE_VERSION}\",\"taskDefinition\":\"${TASK_TEMPLATE}\"}";

  @Before
  public void setUp() {
    awsConnectorSetting =
        aSettingAttribute()
            .withValue(AwsConfig.builder()
                           .accessKey(scmSecret.decryptToCharArray(new SecretName("aws_config_access_key")))
                           .secretKey(scmSecret.decryptToCharArray(new SecretName("aws_setting_attribute_secret_key")))

                           .build())
            .build();
  }

  private String getServiceDefinition(String serviceJson, Map<String, Object> params) {
    return StrSubstitutor.replace(serviceJson, params);
  }

  private AwsClusterConfiguration getAwsClusterConfiguration(Map<String, Object> params) {
    String serviceDefinition = getServiceDefinition(serviceJson, params);
    return AwsClusterConfiguration.builder()
        .name((String) params.get("CLUSTER_NAME") + "_" + params.get("SERVICE_VERSION"))
        .size((Integer) params.get("CLUSTER_SIZE"))
        .serviceDefinition(serviceDefinition)
        .launcherConfiguration(params.get("LAUNCH_CONFIG") + "_" + params.get("SERVICE_VERSION"))
        .autoScalingGroupName(params.get("LAUNCH_CONFIG") + "Asg_" + params.get("SERVICE_VERSION"))
        .vpcZoneIdentifiers("subnet-9725a6bd,subnet-42ddaf34,subnet-64d99b59,subnet-fbe268a3")
        .availabilityZones(asList("us-east-1a", "us-east-1c", "us-east-1d", "us-east-1e")) // optional
        .build();
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldCreateCluster() {
    Map<String, Object> params = new HashMap<>();
    params.put("CLUSTER_NAME", "demo");
    params.put("CLUSTER_SIZE", 5);
    params.put("LAUNCH_CONFIG", "wins_demo_launchconfig");
    params.put("SERVICE_NAME", "Account");
    params.put("SERVICE_VERSION", "v1");
    params.put("TASK_TEMPLATE", "tomcat:7");

    AwsClusterConfiguration awsClusterConfiguration = getAwsClusterConfiguration(params);
    awsClusterService.createCluster(Regions.US_EAST_1.getName(), awsConnectorSetting.toDTO(), Collections.emptyList(),
        awsClusterConfiguration, null);
    // awsClusterService.destroyCluster(awsConnectorSetting, (String) params.get("CLUSTER_NAME"), (String)
    // params.get("SERVICE_NAME" + "_" + "SERVICE_VERSION"));
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldResizeCluster() {
    awsClusterService.resizeCluster(Regions.US_EAST_1.getName(), awsConnectorSetting.toDTO(), Collections.emptyList(),
        "demo_v1", "Account_v1", 0, 3, 10, null, false);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldDeleteCluster() {}

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldProvisionNode() {
    Map<String, Object> params1 = new HashMap<>();
    params1.put("CLUSTER_NAME", "demo");
    params1.put("CLUSTER_SIZE", 5);
    params1.put("LAUNCH_CONFIG", "wins_demo_launchconfig");
    params1.put("SERVICE_NAME", "Account");
    params1.put("SERVICE_VERSION", "v10");
    params1.put("TASK_TEMPLATE", "tomcat:7");

    AwsClusterConfiguration clusterConfiguration = getAwsClusterConfiguration(params1);

    Map<String, Object> params = new HashMap<>();
    params.put("availabilityZones", clusterConfiguration.getAvailabilityZones());
    params.put("vpcZoneIdentifiers", clusterConfiguration.getVpcZoneIdentifiers());
    params.put("clusterName", clusterConfiguration.getName());
    params.put("autoScalingGroupName", ((AwsClusterConfiguration) clusterConfiguration).getAutoScalingGroupName());

    ecsContainerService.provisionNodes(Regions.US_EAST_1.getName(), awsConnectorSetting.toDTO(),
        Collections.emptyList(), 5, "wins_demo_launchconfig_v1", params, null);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldCreateClusterFromCFTemplate() throws InterruptedException {
    ((EcsContainerServiceImpl) ecsContainerService).createCluster();
  }
}
