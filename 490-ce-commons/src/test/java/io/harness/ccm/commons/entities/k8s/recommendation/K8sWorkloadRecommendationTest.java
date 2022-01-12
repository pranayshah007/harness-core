/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.k8s.recommendation;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sWorkloadRecommendationTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testPrePersist() throws Exception {
    K8sWorkloadRecommendation recommendation =
        K8sWorkloadRecommendation.builder()
            .containerRecommendations(ImmutableMap.<String, ContainerRecommendation>builder()
                                          .put("ctr1",
                                              ContainerRecommendation.builder()
                                                  .current(ResourceRequirement.builder()
                                                               .request("cpu", "1m")
                                                               .request("example.com/foo", "1")
                                                               .limit("cpu", "1m")
                                                               .limit("example.com/foo", "1")
                                                               .build())
                                                  .guaranteed(ResourceRequirement.builder()
                                                                  .request("cpu", "1m")
                                                                  .request("example.com/foo", "1")
                                                                  .limit("cpu", "1m")
                                                                  .limit("example.com/foo", "1")
                                                                  .build())
                                                  .burstable(ResourceRequirement.builder()
                                                                 .request("cpu", "1m")
                                                                 .request("example.com/foo", "1")
                                                                 .limit("cpu", "1m")
                                                                 .limit("example.com/foo", "1")
                                                                 .build())
                                                  .recommended(ResourceRequirement.builder()
                                                                   .request("cpu", "1m")
                                                                   .request("example.com/foo", "1")
                                                                   .limit("example.com/foo", "1")
                                                                   .build())
                                                  .build())
                                          .put("ctr2",
                                              ContainerRecommendation.builder()
                                                  .current(ResourceRequirement.builder()
                                                               .request("cpu", "1m")
                                                               .request("example.com/foo", "2")
                                                               .limit("cpu", "1m")
                                                               .limit("example.com/foo", "2")
                                                               .build())
                                                  .guaranteed(ResourceRequirement.builder()
                                                                  .request("cpu", "1m")
                                                                  .request("example.com/foo", "2")
                                                                  .limit("cpu", "1m")
                                                                  .limit("example.com/foo", "2")
                                                                  .build())
                                                  .burstable(ResourceRequirement.builder()
                                                                 .request("cpu", "1m")
                                                                 .request("example.com/foo", "2")
                                                                 .limit("cpu", "1m")
                                                                 .limit("example.com/foo", "2")
                                                                 .build())
                                                  .recommended(ResourceRequirement.builder()
                                                                   .request("cpu", "1m")
                                                                   .request("example.com/foo", "2")
                                                                   .limit("example.com/foo", "2")
                                                                   .build())
                                                  .build())
                                          .build())
            .build();
    recommendation.prePersist();
    assertThat(recommendation.getContainerRecommendations())
        .isEqualTo(ImmutableMap.<String, ContainerRecommendation>builder()
                       .put("ctr1",
                           ContainerRecommendation.builder()
                               .current(ResourceRequirement.builder()
                                            .request("cpu", "1m")
                                            .request("example~com/foo", "1")
                                            .limit("cpu", "1m")
                                            .limit("example~com/foo", "1")
                                            .build())
                               .guaranteed(ResourceRequirement.builder()
                                               .request("cpu", "1m")
                                               .request("example~com/foo", "1")
                                               .limit("cpu", "1m")
                                               .limit("example~com/foo", "1")
                                               .build())
                               .burstable(ResourceRequirement.builder()
                                              .request("cpu", "1m")
                                              .request("example~com/foo", "1")
                                              .limit("cpu", "1m")
                                              .limit("example~com/foo", "1")
                                              .build())
                               .recommended(ResourceRequirement.builder()
                                                .request("cpu", "1m")
                                                .request("example~com/foo", "1")
                                                .limit("example~com/foo", "1")
                                                .build())
                               .build())
                       .put("ctr2",
                           ContainerRecommendation.builder()
                               .current(ResourceRequirement.builder()
                                            .request("cpu", "1m")
                                            .request("example~com/foo", "2")
                                            .limit("cpu", "1m")
                                            .limit("example~com/foo", "2")
                                            .build())
                               .guaranteed(ResourceRequirement.builder()
                                               .request("cpu", "1m")
                                               .request("example~com/foo", "2")
                                               .limit("cpu", "1m")
                                               .limit("example~com/foo", "2")
                                               .build())
                               .burstable(ResourceRequirement.builder()
                                              .request("cpu", "1m")
                                              .request("example~com/foo", "2")
                                              .limit("cpu", "1m")
                                              .limit("example~com/foo", "2")
                                              .build())
                               .recommended(ResourceRequirement.builder()
                                                .request("cpu", "1m")
                                                .request("example~com/foo", "2")
                                                .limit("example~com/foo", "2")
                                                .build())
                               .build())
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testPostLoad() throws Exception {
    K8sWorkloadRecommendation recommendation =
        K8sWorkloadRecommendation.builder()
            .containerRecommendations(ImmutableMap.<String, ContainerRecommendation>builder()
                                          .put("ctr1",
                                              ContainerRecommendation.builder()
                                                  .current(ResourceRequirement.builder()
                                                               .request("cpu", "1m")
                                                               .request("example~com/foo", "1")
                                                               .limit("cpu", "1m")
                                                               .limit("example~com/foo", "1")
                                                               .build())
                                                  .guaranteed(ResourceRequirement.builder()
                                                                  .request("cpu", "1m")
                                                                  .request("example~com/foo", "1")
                                                                  .limit("cpu", "1m")
                                                                  .limit("example~com/foo", "1")
                                                                  .build())
                                                  .burstable(ResourceRequirement.builder()
                                                                 .request("cpu", "1m")
                                                                 .request("example~com/foo", "1")
                                                                 .limit("cpu", "1m")
                                                                 .limit("example~com/foo", "1")
                                                                 .build())
                                                  .recommended(ResourceRequirement.builder()
                                                                   .request("cpu", "1m")
                                                                   .request("example~com/foo", "1")
                                                                   .limit("example~com/foo", "1")
                                                                   .build())
                                                  .build())
                                          .put("ctr2",
                                              ContainerRecommendation.builder()
                                                  .current(ResourceRequirement.builder()
                                                               .request("cpu", "1m")
                                                               .request("example~com/foo", "2")
                                                               .limit("cpu", "1m")
                                                               .limit("example~com/foo", "2")
                                                               .build())
                                                  .guaranteed(ResourceRequirement.builder()
                                                                  .request("cpu", "1m")
                                                                  .request("example~com/foo", "2")
                                                                  .limit("cpu", "1m")
                                                                  .limit("example~com/foo", "2")
                                                                  .build())
                                                  .burstable(ResourceRequirement.builder()
                                                                 .request("cpu", "1m")
                                                                 .request("example~com/foo", "2")
                                                                 .limit("cpu", "1m")
                                                                 .limit("example~com/foo", "2")
                                                                 .build())
                                                  .recommended(ResourceRequirement.builder()
                                                                   .request("cpu", "1m")
                                                                   .request("example~com/foo", "2")
                                                                   .limit("example~com/foo", "2")
                                                                   .build())
                                                  .build())
                                          .build())
            .build();
    recommendation.postLoad();
    assertThat(recommendation.getContainerRecommendations())
        .isEqualTo(ImmutableMap.<String, ContainerRecommendation>builder()
                       .put("ctr1",
                           ContainerRecommendation.builder()
                               .current(ResourceRequirement.builder()
                                            .request("cpu", "1m")
                                            .request("example.com/foo", "1")
                                            .limit("cpu", "1m")
                                            .limit("example.com/foo", "1")
                                            .build())
                               .guaranteed(ResourceRequirement.builder()
                                               .request("cpu", "1m")
                                               .request("example.com/foo", "1")
                                               .limit("cpu", "1m")
                                               .limit("example.com/foo", "1")
                                               .build())
                               .burstable(ResourceRequirement.builder()
                                              .request("cpu", "1m")
                                              .request("example.com/foo", "1")
                                              .limit("cpu", "1m")
                                              .limit("example.com/foo", "1")
                                              .build())
                               .recommended(ResourceRequirement.builder()
                                                .request("cpu", "1m")
                                                .request("example.com/foo", "1")
                                                .limit("example.com/foo", "1")
                                                .build())
                               .build())
                       .put("ctr2",
                           ContainerRecommendation.builder()
                               .current(ResourceRequirement.builder()
                                            .request("cpu", "1m")
                                            .request("example.com/foo", "2")
                                            .limit("cpu", "1m")
                                            .limit("example.com/foo", "2")
                                            .build())
                               .guaranteed(ResourceRequirement.builder()
                                               .request("cpu", "1m")
                                               .request("example.com/foo", "2")
                                               .limit("cpu", "1m")
                                               .limit("example.com/foo", "2")
                                               .build())
                               .burstable(ResourceRequirement.builder()
                                              .request("cpu", "1m")
                                              .request("example.com/foo", "2")
                                              .limit("cpu", "1m")
                                              .limit("example.com/foo", "2")
                                              .build())
                               .recommended(ResourceRequirement.builder()
                                                .request("cpu", "1m")
                                                .request("example.com/foo", "2")
                                                .limit("example.com/foo", "2")
                                                .build())
                               .build())
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNoNpes() throws Exception {
    K8sWorkloadRecommendation k8sWorkloadRecommendation =
        K8sWorkloadRecommendation.builder()
            .containerRecommendations(ImmutableMap.of("ctr1", ContainerRecommendation.builder().build()))
            .build();
    assertThatCode(() -> {
      k8sWorkloadRecommendation.postLoad();
      k8sWorkloadRecommendation.prePersist();
    }).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldBeInvalidIfZeroDiffInAllContainers() throws Exception {
    K8sWorkloadRecommendation k8sWorkloadRecommendation = K8sWorkloadRecommendation.builder()
                                                              .containerRecommendation("ctr1",
                                                                  ContainerRecommendation.builder()
                                                                      .current(ResourceRequirement.builder()
                                                                                   .request("cpu", "1")
                                                                                   .request("memory", "1G")
                                                                                   .limit("cpu", "1")
                                                                                   .limit("memory", "1G")
                                                                                   .build())
                                                                      .burstable(ResourceRequirement.builder()
                                                                                     .request("cpu", "1")
                                                                                     .request("memory", "1G")
                                                                                     .limit("cpu", "1")
                                                                                     .limit("memory", "1G")
                                                                                     .build())
                                                                      .guaranteed(ResourceRequirement.builder()
                                                                                      .request("cpu", "1")
                                                                                      .request("memory", "1G")
                                                                                      .limit("cpu", "1")
                                                                                      .limit("memory", "1G")
                                                                                      .build())
                                                                      .build())
                                                              .containerRecommendation("ctr2",
                                                                  ContainerRecommendation.builder()
                                                                      .current(ResourceRequirement.builder()
                                                                                   .request("cpu", "500m")
                                                                                   .request("memory", "2G")
                                                                                   .limit("cpu", "500m")
                                                                                   .limit("memory", "2G")
                                                                                   .build())
                                                                      .burstable(ResourceRequirement.builder()
                                                                                     .request("cpu", "500m")
                                                                                     .request("memory", "2G")
                                                                                     .limit("cpu", "500m")
                                                                                     .limit("memory", "2G")
                                                                                     .build())
                                                                      .guaranteed(ResourceRequirement.builder()
                                                                                      .request("cpu", "500m")
                                                                                      .request("memory", "2G")
                                                                                      .limit("cpu", "500m")
                                                                                      .limit("memory", "2G")
                                                                                      .build())
                                                                      .build())
                                                              .build();
    k8sWorkloadRecommendation.prePersist();
    assertThat(k8sWorkloadRecommendation.isValidRecommendation()).isFalse();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldBeValidIfZeroDiffInSomeContainers() throws Exception {
    K8sWorkloadRecommendation k8sWorkloadRecommendation = K8sWorkloadRecommendation.builder()
                                                              .containerRecommendation("ctr1",
                                                                  ContainerRecommendation.builder()
                                                                      .current(ResourceRequirement.builder()
                                                                                   .request("cpu", "1")
                                                                                   .request("memory", "1G")
                                                                                   .limit("cpu", "1")
                                                                                   .limit("memory", "1G")
                                                                                   .build())
                                                                      .burstable(ResourceRequirement.builder()
                                                                                     .request("cpu", "1")
                                                                                     .request("memory", "1G")
                                                                                     .limit("cpu", "1")
                                                                                     .limit("memory", "1G")
                                                                                     .build())
                                                                      .guaranteed(ResourceRequirement.builder()
                                                                                      .request("cpu", "1")
                                                                                      .request("memory", "1G")
                                                                                      .limit("cpu", "1")
                                                                                      .limit("memory", "1G")
                                                                                      .build())
                                                                      .recommended(ResourceRequirement.builder()
                                                                                       .request("cpu", "1")
                                                                                       .request("memory", "1G")
                                                                                       .limit("memory", "1G")
                                                                                       .build())
                                                                      .build())
                                                              .containerRecommendation("ctr2",
                                                                  ContainerRecommendation.builder()
                                                                      .current(ResourceRequirement.builder()
                                                                                   .request("cpu", "500m")
                                                                                   .request("memory", "2G")
                                                                                   .limit("cpu", "500m")
                                                                                   .limit("memory", "2G")
                                                                                   .build())
                                                                      .burstable(ResourceRequirement.builder()
                                                                                     .request("cpu", "100m")
                                                                                     .request("memory", "750M")
                                                                                     .limit("cpu", "500m")
                                                                                     .limit("memory", "2G")
                                                                                     .build())
                                                                      .guaranteed(ResourceRequirement.builder()
                                                                                      .request("cpu", "250m")
                                                                                      .request("memory", "1G")
                                                                                      .limit("cpu", "250m")
                                                                                      .limit("memory", "1G")
                                                                                      .build())
                                                                      .recommended(ResourceRequirement.builder()
                                                                                       .request("cpu", "250m")
                                                                                       .request("memory", "1G")
                                                                                       .limit("memory", "1G")
                                                                                       .build())
                                                                      .build())
                                                              .build();
    k8sWorkloadRecommendation.prePersist();
    assertThat(k8sWorkloadRecommendation.isValidRecommendation()).isTrue();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldBeValidIfZeroDiffOnlyInGuaranteedButNotInBurstable() throws Exception {
    K8sWorkloadRecommendation k8sWorkloadRecommendation = K8sWorkloadRecommendation.builder()
                                                              .containerRecommendation("ctr1",
                                                                  ContainerRecommendation.builder()
                                                                      .current(ResourceRequirement.builder()
                                                                                   .request("cpu", "1")
                                                                                   .request("memory", "1G")
                                                                                   .limit("cpu", "1")
                                                                                   .limit("memory", "1G")
                                                                                   .build())
                                                                      .burstable(ResourceRequirement.builder()
                                                                                     .request("cpu", "1")
                                                                                     .request("memory", "1G")
                                                                                     .limit("cpu", "1")
                                                                                     .limit("memory", "1G")
                                                                                     .build())
                                                                      .guaranteed(ResourceRequirement.builder()
                                                                                      .request("cpu", "1")
                                                                                      .request("memory", "1G")
                                                                                      .limit("cpu", "1")
                                                                                      .limit("memory", "1G")
                                                                                      .build())
                                                                      .recommended(ResourceRequirement.builder()
                                                                                       .request("cpu", "1")
                                                                                       .request("memory", "1G")
                                                                                       .limit("memory", "1G")
                                                                                       .build())
                                                                      .build())
                                                              .containerRecommendation("ctr2",
                                                                  ContainerRecommendation.builder()
                                                                      .current(ResourceRequirement.builder()
                                                                                   .request("cpu", "500m")
                                                                                   .request("memory", "2G")
                                                                                   .limit("cpu", "500m")
                                                                                   .limit("memory", "2G")
                                                                                   .build())
                                                                      .burstable(ResourceRequirement.builder()
                                                                                     .request("cpu", "100m")
                                                                                     .request("memory", "1.5G")
                                                                                     .limit("cpu", "750m")
                                                                                     .limit("memory", "2.5G")
                                                                                     .build())
                                                                      .guaranteed(ResourceRequirement.builder()
                                                                                      .request("cpu", "500m")
                                                                                      .request("memory", "2G")
                                                                                      .limit("cpu", "500m")
                                                                                      .limit("memory", "2G")
                                                                                      .build())
                                                                      .recommended(ResourceRequirement.builder()
                                                                                       .request("cpu", "500m")
                                                                                       .request("memory", "2G")
                                                                                       .limit("memory", "2G")
                                                                                       .build())
                                                                      .build())
                                                              .build();
    k8sWorkloadRecommendation.prePersist();
    assertThat(k8sWorkloadRecommendation.isValidRecommendation()).isTrue();
  }
}
