/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.RUSHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.AutoScalingGroupInstanceInfo;
import software.wings.beans.infrastructure.instance.info.CodeDeployInstanceInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.graphql.datafetcher.instance.InstanceControllerManager;
import software.wings.graphql.schema.type.instance.QLAutoScalingGroupInstance;
import software.wings.graphql.schema.type.instance.QLCodeDeployInstance;
import software.wings.graphql.schema.type.instance.QLEcsContainerInstance;
import software.wings.graphql.schema.type.instance.QLInstance;
import software.wings.graphql.schema.type.instance.QLInstanceType;
import software.wings.graphql.schema.type.instance.QLK8SPodInstance;
import software.wings.graphql.schema.type.instance.QLPcfInstance;
import software.wings.graphql.schema.type.instance.QLPhysicalHostInstance;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InstanceControllerManagerTest extends GraphQLMockTestBase {
  @Inject InstanceControllerManager instanceControllerManager;

  private static final String TEST_ACCOUNT_ID = "TESTACCOUNTID";
  private static final String TEST_APPLICATION_ID = "TESTAPPID";
  private static final String TESTHOSTID = "TESTHOSTID";
  private static final String TESTHOSTNAME = "TESTHOSTNAME";
  private static final String TESTDNS = "TESTDNS";
  private static final String TESTENVID = "TESTENVID";
  private final String TESTSERVICEID = "TESTSERVICEID";

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testPhysicalHostInstance() {
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.PHYSICAL_HOST_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(PhysicalHostInstanceInfo.builder()
                                              .hostId(TESTHOSTID)
                                              .hostName(TESTHOSTNAME)
                                              .hostPublicDns(TESTDNS)
                                              .build())
                            .build();

    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLPhysicalHostInstance.class);
    QLPhysicalHostInstance qlPhysicalHostInstance = (QLPhysicalHostInstance) qlInstance;
    assertThat(qlPhysicalHostInstance.getType()).isEqualTo(QLInstanceType.PHYSICAL_HOST_INSTANCE);
    assertThat(qlPhysicalHostInstance.getHostId()).isEqualTo(TESTHOSTID);
    assertThat(qlPhysicalHostInstance.getHostName()).isEqualTo(TESTHOSTNAME);
    assertThat(qlPhysicalHostInstance.getHostPublicDns()).isEqualTo(TESTDNS);
    assertThat(qlPhysicalHostInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlPhysicalHostInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAutoScalingGroupInstanceInfo() {
    final String TESTAUTOSCALINGGROUP = "TESTAUTOSCALINGGROUP";
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(AutoScalingGroupInstanceInfo.builder()
                                              .hostId(TESTHOSTID)
                                              .hostName(TESTHOSTNAME)
                                              .hostPublicDns(TESTDNS)
                                              .autoScalingGroupName(TESTAUTOSCALINGGROUP)
                                              .build())
                            .build();

    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLAutoScalingGroupInstance.class);
    QLAutoScalingGroupInstance qlAutoScalingGroupInstance = (QLAutoScalingGroupInstance) qlInstance;
    assertThat(qlAutoScalingGroupInstance.getType()).isEqualTo(QLInstanceType.AUTOSCALING_GROUP_INSTANCE);
    assertThat(qlAutoScalingGroupInstance.getHostId()).isEqualTo(TESTHOSTID);
    assertThat(qlAutoScalingGroupInstance.getHostName()).isEqualTo(TESTHOSTNAME);
    assertThat(qlAutoScalingGroupInstance.getHostPublicDns()).isEqualTo(TESTDNS);
    assertThat(qlAutoScalingGroupInstance.getAutoScalingGroupName()).isEqualTo(TESTAUTOSCALINGGROUP);
    assertThat(qlAutoScalingGroupInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlAutoScalingGroupInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testCodeDeployInstanceInfo() {
    final String TESTDEPLOYMENTID = "TESTDEPLOYMENTID";
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(CodeDeployInstanceInfo.builder()
                                              .hostId(TESTHOSTID)
                                              .hostName(TESTHOSTNAME)
                                              .hostPublicDns(TESTDNS)
                                              .deploymentId(TESTDEPLOYMENTID)
                                              .build())
                            .build();

    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLCodeDeployInstance.class);
    QLCodeDeployInstance qlCodeDeployInstance = (QLCodeDeployInstance) qlInstance;

    assertThat(qlCodeDeployInstance.getType()).isEqualTo(QLInstanceType.CODE_DEPLOY_INSTANCE);
    assertThat(qlCodeDeployInstance.getHostId()).isEqualTo(TESTHOSTID);
    assertThat(qlCodeDeployInstance.getHostName()).isEqualTo(TESTHOSTNAME);
    assertThat(qlCodeDeployInstance.getHostPublicDns()).isEqualTo(TESTDNS);
    assertThat(qlCodeDeployInstance.getDeploymentId()).isEqualTo(TESTDEPLOYMENTID);
    assertThat(qlCodeDeployInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlCodeDeployInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testEcsContainerInfo() {
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(EcsContainerInfo.Builder.anEcsContainerInfo()
                                              .withClusterName("TESTCLUSTER")
                                              .withServiceName("TESTSERVICE")
                                              .withStartedAt(100)
                                              .withStartedBy("TESTUSER")
                                              .withTaskArn("TESTTASK")
                                              .withTaskDefinitionArn("TESTTASKDEF")
                                              .build())
                            .build();

    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLEcsContainerInstance.class);
    QLEcsContainerInstance qlEcsContainerInstance = (QLEcsContainerInstance) qlInstance;

    assertThat(qlEcsContainerInstance.getType()).isEqualTo(QLInstanceType.ECS_CONTAINER_INSTANCE);
    assertThat(qlEcsContainerInstance.getClusterName()).isEqualTo("TESTCLUSTER");
    assertThat(qlEcsContainerInstance.getServiceName()).isEqualTo("TESTSERVICE");
    assertThat(qlEcsContainerInstance.getStartedAt()).isEqualTo(Long.valueOf(100));
    assertThat(qlEcsContainerInstance.getStartedBy()).isEqualTo("TESTUSER");
    assertThat(qlEcsContainerInstance.getTaskArn()).isEqualTo("TESTTASK");
    assertThat(qlEcsContainerInstance.getTaskDefinitionArn()).isEqualTo("TESTTASKDEF");
    assertThat(qlEcsContainerInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlEcsContainerInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testK8sPodInfo() {
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(K8sPodInfo.builder()
                                              .ip("TESTIP")
                                              .namespace("TESTNAMESPACE")
                                              .podName("TESTPOD")
                                              .releaseName("TESTRELEASE")
                                              .clusterName("TESTCLUSTER")
                                              .containers(Arrays.asList(K8sContainerInfo.builder()
                                                                            .containerId("TESTCONTAINER")
                                                                            .image("TESTIMAGE")
                                                                            .name("TESTNAME")
                                                                            .build()))
                                              .build())
                            .build();

    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLK8SPodInstance.class);
    QLK8SPodInstance qlk8SPodInstance = (QLK8SPodInstance) qlInstance;

    assertThat(qlk8SPodInstance.getType()).isEqualTo(QLInstanceType.KUBERNETES_CONTAINER_INSTANCE);
    assertThat(qlk8SPodInstance.getClusterName()).isEqualTo("TESTCLUSTER");
    assertThat(qlk8SPodInstance.getIp()).isEqualTo("TESTIP");
    assertThat(qlk8SPodInstance.getNamespace()).isEqualTo("TESTNAMESPACE");
    assertThat(qlk8SPodInstance.getPodName()).isEqualTo("TESTPOD");
    assertThat(qlk8SPodInstance.getReleaseName()).isEqualTo("TESTRELEASE");
    assertThat(qlk8SPodInstance.getClusterName()).isEqualTo("TESTCLUSTER");
    assertThat(qlk8SPodInstance.getContainers().get(0).getContainerId()).isEqualTo("TESTCONTAINER");
    assertThat(qlk8SPodInstance.getContainers().get(0).getImage()).isEqualTo("TESTIMAGE");
    assertThat(qlk8SPodInstance.getContainers().get(0).getName()).isEqualTo("TESTNAME");
    assertThat(qlk8SPodInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlk8SPodInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testKubernetesContainerInfo() {
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(KubernetesContainerInfo.builder()
                                              .ip("TESTIP")
                                              .namespace("TESTNAMESPACE")
                                              .podName("TESTPOD")
                                              .clusterName("TESTCLUSTER")
                                              .build())
                            .build();

    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLK8SPodInstance.class);
    QLK8SPodInstance qlk8SPodInstance = (QLK8SPodInstance) qlInstance;

    assertThat(qlk8SPodInstance.getType()).isEqualTo(QLInstanceType.KUBERNETES_CONTAINER_INSTANCE);
    assertThat(qlk8SPodInstance.getClusterName()).isEqualTo("TESTCLUSTER");
    assertThat(qlk8SPodInstance.getIp()).isEqualTo("TESTIP");
    assertThat(qlk8SPodInstance.getNamespace()).isEqualTo("TESTNAMESPACE");
    assertThat(qlk8SPodInstance.getPodName()).isEqualTo("TESTPOD");
    assertThat(qlk8SPodInstance.getReleaseName()).isNull();
    assertThat(qlk8SPodInstance.getContainers()).isNull();
    assertThat(qlk8SPodInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlk8SPodInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testPcfInstanceInfo() {
    Instance instance = Instance.builder()
                            .accountId(TEST_ACCOUNT_ID)
                            .appId(TEST_APPLICATION_ID)
                            .instanceType(InstanceType.PCF_INSTANCE)
                            .envId(TESTENVID)
                            .serviceId(TESTSERVICEID)
                            .instanceInfo(PcfInstanceInfo.builder()
                                              .id("TESTID")
                                              .organization("TESTORG")
                                              .space("TESTSPACE")
                                              .pcfApplicationName("PCFAPPLICATIONNAME")
                                              .pcfApplicationGuid("PCFAPPLICATIONGUID")
                                              .instanceIndex("INSTANCEINDEX")
                                              .build())

                            .build();
    QLInstance qlInstance = instanceControllerManager.getQLInstance(instance);
    assertThat(qlInstance).isExactlyInstanceOf(QLPcfInstance.class);
    QLPcfInstance qlPcfInstance = (QLPcfInstance) qlInstance;

    assertThat(qlPcfInstance.getType()).isEqualTo(QLInstanceType.PCF_INSTANCE);
    assertThat(qlPcfInstance.getPcfId()).isEqualTo("TESTID");
    assertThat(qlPcfInstance.getOrganization()).isEqualTo("TESTORG");
    assertThat(qlPcfInstance.getSpace()).isEqualTo("TESTSPACE");
    assertThat(qlPcfInstance.getPcfApplicationName()).isEqualTo("PCFAPPLICATIONNAME");
    assertThat(qlPcfInstance.getPcfApplicationGuid()).isEqualTo("PCFAPPLICATIONGUID");
    assertThat(qlPcfInstance.getInstanceIndex()).isEqualTo("INSTANCEINDEX");
    assertThat(qlPcfInstance.getEnvironmentId()).isEqualTo(TESTENVID);
    assertThat(qlPcfInstance.getServiceId()).isEqualTo(TESTSERVICEID);
  }
}
