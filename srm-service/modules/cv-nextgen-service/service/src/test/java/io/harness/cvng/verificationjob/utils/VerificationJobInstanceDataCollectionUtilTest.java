/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.utils;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.verificationjob.entities.ServiceInstanceDetails;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationJobInstanceDataCollectionUtilTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void shouldCollectPreDeploymentData_canaryWithNonValidServiceInstance() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(ServiceInstanceDetails.builder()
                                              .valid(false)
                                              .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                              .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                              .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "c1", "c2"))
                                              .build())
            .build();
    assertThat(VerificationJobInstanceDataCollectionUtils.shouldCollectPreDeploymentData(verificationJobInstance))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void shouldCollectPreDeploymentData_canaryWithValidServiceInstance() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(ServiceInstanceDetails.builder()
                                              .valid(true)
                                              .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                              .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                              .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "c1", "c2"))
                                              .build())
            .build();
    assertThat(VerificationJobInstanceDataCollectionUtils.shouldCollectPreDeploymentData(verificationJobInstance))
        .isFalse();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void shouldCollectPreDeploymentData_autoWithValidCanaryServiceInstance() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.autoVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(ServiceInstanceDetails.builder()
                                              .valid(true)
                                              .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                              .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                              .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "c1", "c2"))
                                              .build())
            .build();
    assertThat(VerificationJobInstanceDataCollectionUtils.shouldCollectPreDeploymentData(verificationJobInstance))
        .isFalse();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void shouldCollectPreDeploymentData_autoWithValidRollingServiceInstance() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.autoVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(ServiceInstanceDetails.builder()
                                              .valid(true)
                                              .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                              .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                              .serviceInstancesAfterDeployment(Arrays.asList("c1", "c2"))
                                              .build())
            .build();
    assertThat(VerificationJobInstanceDataCollectionUtils.shouldCollectPreDeploymentData(verificationJobInstance))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void shouldCollectPreDeploymentData_blueGreen() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.blueGreenVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(ServiceInstanceDetails.builder()
                                              .valid(true)
                                              .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                              .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                              .serviceInstancesAfterDeployment(Arrays.asList("c1", "c2"))
                                              .build())
            .build();
    assertThat(VerificationJobInstanceDataCollectionUtils.shouldCollectPreDeploymentData(verificationJobInstance))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void shouldCollectUsingNodesFromCD_nonValidServiceInstance() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.blueGreenVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(ServiceInstanceDetails.builder()
                                              .valid(false)
                                              .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                              .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                              .serviceInstancesAfterDeployment(Arrays.asList("c1", "c2"))
                                              .build())
            .build();
    assertThat(VerificationJobInstanceDataCollectionUtils.shouldCollectPreDeploymentData(verificationJobInstance))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getPreDeploymentNodesToCollect_autoRolling() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.autoVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(
                ServiceInstanceDetails.builder()
                    .valid(true)
                    .deployedServiceInstances(
                        IntStream.range(0, 200).boxed().map(i -> "c" + i).collect(Collectors.toList()))
                    .serviceInstancesBeforeDeployment(
                        IntStream.range(0, 200).boxed().map(i -> "p" + i).collect(Collectors.toList()))
                    .serviceInstancesAfterDeployment(
                        IntStream.range(0, 200).boxed().map(i -> "c" + i).collect(Collectors.toList()))
                    .build())
            .build();

    List<String> preDeploymentNodes =
        VerificationJobInstanceDataCollectionUtils.getPreDeploymentNodesToCollect(verificationJobInstance);
    assertThat(preDeploymentNodes).hasSize(VerificationJobInstanceServiceInstanceUtils.MAX_CONTROL_NODE_COUNT);
    assertThat(preDeploymentNodes)
        .allMatch(
            tn -> VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance).contains(tn));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getPostDeploymentNodesToCollect_canary() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(
                ServiceInstanceDetails.builder()
                    .valid(true)
                    .deployedServiceInstances(
                        IntStream.range(0, 200).boxed().map(i -> "c" + i).collect(Collectors.toList()))
                    .serviceInstancesBeforeDeployment(
                        IntStream.range(0, 200).boxed().map(i -> "p" + i).collect(Collectors.toList()))
                    .serviceInstancesAfterDeployment(Stream
                                                         .concat(IntStream.range(0, 200).boxed().map(i -> "p" + i),
                                                             IntStream.range(0, 200).boxed().map(i -> "c" + i))
                                                         .collect(Collectors.toList()))
                    .build())
            .build();

    List<String> postDeploymentNodes =
        VerificationJobInstanceDataCollectionUtils.getPostDeploymentNodesToCollect(verificationJobInstance);
    assertThat(postDeploymentNodes)
        .hasSize(VerificationJobInstanceServiceInstanceUtils.MAX_CONTROL_NODE_COUNT
            + VerificationJobInstanceServiceInstanceUtils.MAX_TEST_NODE_COUNT);
    assertThat(postDeploymentNodes)
        .allMatch(tn
            -> VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance).contains(tn)
                || VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance).contains(tn));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getPostDeploymentNodesToCollect_blueGreen() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.blueGreenVerificationJobBuilder().build())
            .serviceInstanceDetailsFromCD(
                ServiceInstanceDetails.builder()
                    .valid(true)
                    .deployedServiceInstances(
                        IntStream.range(0, 200).boxed().map(i -> "g" + i).collect(Collectors.toList()))
                    .serviceInstancesBeforeDeployment(
                        IntStream.range(0, 200).boxed().map(i -> "b" + i).collect(Collectors.toList()))
                    .serviceInstancesAfterDeployment(Stream
                                                         .concat(IntStream.range(0, 200).boxed().map(i -> "g" + i),
                                                             IntStream.range(0, 200).boxed().map(i -> "b" + i))
                                                         .collect(Collectors.toList()))
                    .build())
            .build();

    List<String> postDeploymentNodes =
        VerificationJobInstanceDataCollectionUtils.getPostDeploymentNodesToCollect(verificationJobInstance);
    assertThat(postDeploymentNodes).hasSize(VerificationJobInstanceServiceInstanceUtils.MAX_TEST_NODE_COUNT);
    assertThat(postDeploymentNodes)
        .allMatch(tn -> VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance).contains(tn));
  }
}
