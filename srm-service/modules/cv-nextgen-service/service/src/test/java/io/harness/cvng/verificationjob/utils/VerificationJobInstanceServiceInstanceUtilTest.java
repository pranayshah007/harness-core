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

public class VerificationJobInstanceServiceInstanceUtilTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getSampledTestNodes() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetails(
                ServiceInstanceDetails.builder()
                    .shouldUseNodesFromCD(true)
                    .deployedServiceInstances(
                        IntStream.range(0, 100).boxed().map(i -> "c" + i).collect(Collectors.toList()))
                    .serviceInstancesBeforeDeployment(
                        IntStream.range(0, 100).boxed().map(i -> "p" + i).collect(Collectors.toList()))
                    .serviceInstancesAfterDeployment(Stream
                                                         .concat(IntStream.range(0, 100).boxed().map(i -> "p" + i),
                                                             IntStream.range(0, 100).boxed().map(i -> "c" + i))
                                                         .collect(Collectors.toList()))
                    .build())
            .build();

    List<String> testNodes = VerificationJobInstanceServiceInstanceUtils.getSampledTestNodes(verificationJobInstance);
    assertThat(testNodes).hasSize(VerificationJobInstanceServiceInstanceUtils.MAX_TEST_NODE_COUNT);
    assertThat(testNodes).allMatch(
        tn -> verificationJobInstance.getServiceInstanceDetails().getDeployedServiceInstances().contains(tn));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getSampledControlNodes() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetails(
                ServiceInstanceDetails.builder()
                    .shouldUseNodesFromCD(true)
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

    List<String> controlNodes =
        VerificationJobInstanceServiceInstanceUtils.getSampledControlNodes(verificationJobInstance);
    assertThat(controlNodes).hasSize(VerificationJobInstanceServiceInstanceUtils.MAX_CONTROL_NODE_COUNT);
    assertThat(controlNodes)
        .allMatch(
            tn -> VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance).contains(tn));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getTestNodes_forCanaryWithAutoScaling() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetails(
                ServiceInstanceDetails.builder()
                    .shouldUseNodesFromCD(true)
                    .deployedServiceInstances(Arrays.asList("c1", "c2"))
                    .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                    .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "p3", "p4", "c1", "c2"))
                    .build())
            .build();

    List<String> testNodes = VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance);
    assertThat(testNodes).isEqualTo(Arrays.asList("c1", "c2"));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getTestNodes_forAutoCanary() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.autoVerificationJobBuilder().build())
            .serviceInstanceDetails(
                ServiceInstanceDetails.builder()
                    .shouldUseNodesFromCD(true)
                    .deployedServiceInstances(Arrays.asList("c1", "c2"))
                    .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                    .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "p3", "p4", "c1", "c2"))
                    .build())
            .build();

    List<String> testNodes = VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance);
    assertThat(testNodes).isEqualTo(Arrays.asList("c1", "c2"));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getTestNodes_forAutoRolling() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.autoVerificationJobBuilder().build())
            .serviceInstanceDetails(ServiceInstanceDetails.builder()
                                        .shouldUseNodesFromCD(true)
                                        .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                        .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                        .serviceInstancesAfterDeployment(Arrays.asList("c1", "c2", "c3"))
                                        .build())
            .build();

    List<String> testNodes = VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance);
    assertThat(testNodes).isEqualTo(Arrays.asList("c1", "c2"));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getTestNodes_forRollingBlueGreenWithNoNodeChange() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.blueGreenVerificationJobBuilder().build())
            .serviceInstanceDetails(ServiceInstanceDetails.builder()
                                        .shouldUseNodesFromCD(true)
                                        .deployedServiceInstances(Arrays.asList("p1", "p2"))
                                        .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2"))
                                        .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2"))
                                        .build())
            .build();

    List<String> testNodes = VerificationJobInstanceServiceInstanceUtils.getTestNodes(verificationJobInstance);
    assertThat(testNodes).isEqualTo(Arrays.asList("p1", "p2"));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getControlNodes_forRollingBlueGreenWithDeScaling() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.blueGreenVerificationJobBuilder().build())
            .serviceInstanceDetails(ServiceInstanceDetails.builder()
                                        .shouldUseNodesFromCD(true)
                                        .deployedServiceInstances(Arrays.asList("p1", "p2"))
                                        .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                        .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2"))
                                        .build())
            .build();

    List<String> controlNodes = VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance);
    assertThat(controlNodes).isEqualTo(Arrays.asList("p1", "p2", "p3"));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getControlNodes_forCanaryWithAutoDeScaling() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .serviceInstanceDetails(ServiceInstanceDetails.builder()
                                        .shouldUseNodesFromCD(true)
                                        .deployedServiceInstances(Arrays.asList("c1", "c2"))
                                        .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
                                        .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "c1", "c2"))
                                        .build())
            .build();

    List<String> controlNodes = VerificationJobInstanceServiceInstanceUtils.getControlNodes(verificationJobInstance);
    assertThat(controlNodes).isEqualTo(Arrays.asList("p1", "p2"));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void isValidCanaryDeployment_valid() {
    ServiceInstanceDetails serviceInstanceDetails =
        ServiceInstanceDetails.builder()
            .shouldUseNodesFromCD(true)
            .deployedServiceInstances(Arrays.asList("c1", "c2"))
            .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
            .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2", "c1", "c2"))
            .build();

    assertThat(VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(serviceInstanceDetails)).isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void isValidCanaryDeployment_noBeforeNodes() {
    ServiceInstanceDetails serviceInstanceDetails =
        ServiceInstanceDetails.builder()
            .shouldUseNodesFromCD(true)
            .deployedServiceInstances(Arrays.asList("c1", "c2"))
            .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2", "p3"))
            .serviceInstancesAfterDeployment(Arrays.asList("p4", "p5", "c1", "c2"))
            .build();

    assertThat(VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(serviceInstanceDetails)).isFalse();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void isValidCanaryDeployment_AllNodesGotDeployedAgain() {
    ServiceInstanceDetails serviceInstanceDetails = ServiceInstanceDetails.builder()
                                                        .shouldUseNodesFromCD(true)
                                                        .deployedServiceInstances(Arrays.asList("p1", "p2"))
                                                        .serviceInstancesBeforeDeployment(Arrays.asList("p1", "p2"))
                                                        .serviceInstancesAfterDeployment(Arrays.asList("p1", "p2"))
                                                        .build();

    assertThat(VerificationJobInstanceServiceInstanceUtils.isValidCanaryDeployment(serviceInstanceDetails)).isFalse();
  }
}
