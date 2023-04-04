/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.service;

import static io.harness.rule.OwnerRule.MANAVJOT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;

public class ClusterServiceTest extends CDNGEntitiesTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String ENV_ID = "ENV_ID";

  @Inject private ClusterService clusterService;

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGet() {
    final String uuid = UUIDGenerator.generateUuid();
    clusterService.create(getCluster(uuid));

    Optional<Cluster> cluster = clusterService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, uuid);

    assertThat(cluster).isPresent();
    assertThat(cluster.get().getEnvRef()).isEqualTo(ENV_ID);
    assertThat(cluster.get().getClusterRef()).isEqualTo(uuid);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testBulkCreate() {
    assertThat(clusterService.bulkCreate(asList(getCluster("c1"), getCluster("c2"), getCluster("c3")))).isEqualTo(3);
    assertThat(
        clusterService.bulkCreate(asList(getCluster("c1"), getCluster("c2"), getCluster("c3"), getCluster("c4"))))
        // In memory mongo  does not have indexes, hence we do not get duplicate key exceptions. In real mongo, this
        // value should be 1
        .isEqualTo(4);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDelete() {
    final String uuid = UUIDGenerator.generateUuid();
    clusterService.create(getCluster(uuid));

    clusterService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, uuid, ScopeLevel.PROJECT);

    assertThat(clusterService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, uuid)).isNotPresent();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAccountLevelClusterDelete() {
    final String uuid = UUIDGenerator.generateUuid();
    clusterService.create(getAccountLevelCluster(uuid));
    clusterService.delete(ACCOUNT_ID, null, null, ENV_ID, uuid, ScopeLevel.PROJECT);
    assertThat(clusterService.get(ACCOUNT_ID, null, null, ENV_ID, uuid)).isNotPresent();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteFromAllEnv() {
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "a1"), getClusterForEnv("env1", "a2"), getClusterForEnv("env1", "a3")));
    clusterService.bulkCreate(
        asList(getClusterForEnv("env2", "a1"), getClusterForEnv("env2", "a2"), getClusterForEnv("env2", "a3")));

    clusterService.deleteFromAllEnv(ACCOUNT_ID, ORG_ID, PROJECT_ID, "a1");

    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env1"))
                   .stream()
                   .map(Cluster::getClusterRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("a2", "a3");
    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env2"))
                   .stream()
                   .map(Cluster::getClusterRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("a2", "a3");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteFromAllEnvForAccLevelCluster() {
    clusterService.bulkCreate(asList(getClusterForEnv("env1", "account.a1"), getClusterForEnv("env1", "a2"),
        getClusterForEnv("env1", "account.a3")));
    clusterService.bulkCreate(asList(getClusterForEnv("env2", "account.a1"), getClusterForEnv("env2", "a2"),
        getClusterForEnv("env2", "account.a3")));
    clusterService.bulkCreate(asList(getClusterForEnv("env3", "account.a4")));

    clusterService.deleteFromAllEnv(ACCOUNT_ID, "", "", "a1");

    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env1"))
                   .stream()
                   .map(Cluster::getClusterRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("a2", "account.a3");
    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env2"))
                   .stream()
                   .map(Cluster::getClusterRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("a2", "account.a3");

    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env3"))
                   .stream()
                   .map(Cluster::getClusterRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("account.a4");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteAllFromEnv() {
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "a1"), getClusterForEnv("env1", "a2"), getClusterForEnv("env1", "a3")));
    clusterService.bulkCreate(
        asList(getClusterForEnv("env2", "a1"), getClusterForEnv("env2", "a2"), getClusterForEnv("env2", "a3")));

    clusterService.deleteAllFromEnv(ACCOUNT_ID, ORG_ID, PROJECT_ID, "env2");

    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env1")).size()).isEqualTo(3);
    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env2")).size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteAllFromProj() {
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "y1"), getClusterForEnv("env1", "y2"), getClusterForEnv("env1", "y3")));
    clusterService.bulkCreate(
        asList(getClusterForEnv("env2", "z1"), getClusterForEnv("env2", "z2"), getClusterForEnv("env2", "z3")));

    clusterService.deleteAllFromProj(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env1", "env2")).size())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testBulkDelete() {
    List<Cluster> clusterList =
        asList(getClusterForEnv("env1", "y1"), getClusterForEnv("env1", "y2"), getClusterForEnv("env1", "y3"));
    clusterService.bulkCreate(clusterList);

    clusterService.bulkDelete(clusterList, ACCOUNT_ID, ORG_ID, PROJECT_ID, "env1");

    assertThat(clusterService.listAcrossEnv(0, 5, ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("env1")).size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testList() {
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "t1"), getClusterForEnv("env1", "t2"), getClusterForEnv("env1", "t13")));
    clusterService.bulkCreate(
        asList(getClusterForEnv("env2", "t1"), getClusterForEnv("env2", "t2"), getClusterForEnv("env2", "t3")));

    assertThat(
        clusterService.list(0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "env1", "t1", new ArrayList<>(), new ArrayList<>())
            .getContent())
        .hasSize(2);
  }

  @Test
  @Owner(developers = MANAVJOT)
  @Category(UnitTests.class)
  public void testDeleteAllFromEnvAndReturnCount() {
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "t1"), getClusterForEnv("env1", "t2"), getClusterForEnv("env1", "t13")));

    long deleteCount = clusterService.deleteAllFromEnvAndReturnCount(ACCOUNT_ID, ORG_ID, PROJECT_ID, "env1");
    assertThat(deleteCount).isEqualTo(3L);
  }

  @Test
  @Owner(developers = MANAVJOT)
  @Category(UnitTests.class)
  public void testList_withSameNameClusters() {
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "t1"), getClusterForEnv("env2", "t1"), getClusterForEnv("env3", "t1")));
    clusterService.bulkCreate(
        asList(getClusterForEnv("env1", "t1"), getClusterForEnv("env1", "t1"), getClusterForEnv("env1", "t1")));

    Page<Cluster> page =
        clusterService.list(0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "env1", "t1", new ArrayList<>(), new ArrayList<>());
    assertThat(page.getContent().size()).isEqualTo(4L);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldListClustersFromAllScopes() {
    clusterService.bulkCreate(asList(getClusterForEnv("env1", "t1", ORG_ID, PROJECT_ID),
        getClusterForEnv("env2", "t1", ORG_ID, null), getClusterForEnv("env3", "t1", null, null)));

    List<Cluster> envs = clusterService.listAcrossEnv(0, 100, ACCOUNT_ID, ORG_ID, PROJECT_ID, asList("env1"));
    assertThat(envs).hasSize(1);

    envs = clusterService.listAcrossEnv(0, 100, ACCOUNT_ID, ORG_ID, PROJECT_ID, asList("env1", "env2"));
    assertThat(envs).hasSize(1);

    envs = clusterService.listAcrossEnv(0, 100, ACCOUNT_ID, ORG_ID, PROJECT_ID, asList("env1", "org.env2"));
    assertThat(envs).hasSize(2);

    envs = clusterService.listAcrossEnv(0, 100, ACCOUNT_ID, ORG_ID, PROJECT_ID, asList("env1", "org.env2", "env3"));
    assertThat(envs).hasSize(2);

    envs = clusterService.listAcrossEnv(
        0, 100, ACCOUNT_ID, ORG_ID, PROJECT_ID, asList("env1", "org.env2", "account.env3"));
    assertThat(envs).hasSize(3);
  }

  private Cluster getCluster(String uuid) {
    return Cluster.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .envRef(ENV_ID)
        .clusterRef(uuid)
        .build();
  }

  private Cluster getAccountLevelCluster(String uuid) {
    return Cluster.builder().accountId(ACCOUNT_ID).envRef(ENV_ID).clusterRef(uuid).build();
  }

  private Cluster getClusterForEnv(String envRef, String uuid) {
    return Cluster.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .envRef(envRef)
        .clusterRef(uuid)
        .build();
  }

  private Cluster getClusterForEnv(String envRef, String uuid, String orgId, String projectId) {
    return Cluster.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .envRef(envRef)
        .clusterRef(uuid)
        .build();
  }
}
