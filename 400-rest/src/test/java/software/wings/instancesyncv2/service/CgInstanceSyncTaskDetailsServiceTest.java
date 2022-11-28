/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.dl.WingsMongoPersistence;
import software.wings.instancesyncv2.model.BasicDeploymentIdentifier;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class CgInstanceSyncTaskDetailsServiceTest extends CategoryTest {
  @Mock private WingsMongoPersistence wingsMongoPersistence;
  @InjectMocks private CgInstanceSyncTaskDetailsService taskDetailsService;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateLastRun() {
    final long lastSuccessfulRunBefore = System.currentTimeMillis() - 100L;
    final long deleteAfterBefore = 100L;
    final long deleteAfterAfter = 200L;
    Map<String, CgReleaseIdentifiers> updateMap =
        ImmutableMap.of("release-1", createK8sReleaseIdentifier("release-1", deleteAfterAfter), "release-2",
            createK8sReleaseIdentifier("release-2", deleteAfterAfter));

    Set<CgReleaseIdentifiers> releaseIdentifiersToDelete =
        new HashSet<>(asList(createK8sReleaseIdentifier("release-3", deleteAfterAfter),
            createK8sReleaseIdentifier("release-4", deleteAfterAfter)));

    Set<CgReleaseIdentifiers> allReleaseIdentifiers = new HashSet<>();
    allReleaseIdentifiers.add(createK8sReleaseIdentifier("release-1", deleteAfterBefore));
    allReleaseIdentifiers.add(createK8sReleaseIdentifier("release-2", deleteAfterBefore));
    allReleaseIdentifiers.add(createK8sReleaseIdentifier("release-3", deleteAfterBefore));
    allReleaseIdentifiers.add(createK8sReleaseIdentifier("release-4", deleteAfterBefore));
    allReleaseIdentifiers.add(createK8sReleaseIdentifier("release-5", deleteAfterBefore));
    allReleaseIdentifiers.add(createK8sReleaseIdentifier("release-6", deleteAfterBefore));

    String taskDetailsId = "taskDetailsId";
    InstanceSyncTaskDetails instanceSyncTaskDetails = InstanceSyncTaskDetails.builder()
                                                          .releaseIdentifiers(new HashSet<>(allReleaseIdentifiers))
                                                          .lastSuccessfulRun(lastSuccessfulRunBefore)
                                                          .build();

    doReturn(instanceSyncTaskDetails).when(wingsMongoPersistence).get(InstanceSyncTaskDetails.class, taskDetailsId);

    taskDetailsService.updateLastRun(taskDetailsId, new HashSet<>(updateMap.values()), releaseIdentifiersToDelete);

    ArgumentCaptor<InstanceSyncTaskDetails> updateInstanceSyncTaskDetailsCaptor =
        ArgumentCaptor.forClass(InstanceSyncTaskDetails.class);

    verify(wingsMongoPersistence).save(updateInstanceSyncTaskDetailsCaptor.capture());
    InstanceSyncTaskDetails updatedInstanceSyncTaskDetails = updateInstanceSyncTaskDetailsCaptor.getValue();
    assertThat(updatedInstanceSyncTaskDetails.getReleaseIdentifiers())
        .doesNotContainAnyElementsOf(releaseIdentifiersToDelete);
    assertThat(updatedInstanceSyncTaskDetails.getLastSuccessfulRun()).isGreaterThan(lastSuccessfulRunBefore);
    for (CgReleaseIdentifiers releaseIdentifier : updatedInstanceSyncTaskDetails.getReleaseIdentifiers()) {
      CgK8sReleaseIdentifier k8sReleaseIdentifier = (CgK8sReleaseIdentifier) releaseIdentifier;
      if (updateMap.containsKey(k8sReleaseIdentifier.getReleaseName())) {
        assertThat(k8sReleaseIdentifier.getDeleteAfter())
            .isEqualTo(updateMap.get(k8sReleaseIdentifier.getReleaseName()).getDeleteAfter());
      }
    }
  }

  private CgReleaseIdentifiers createK8sReleaseIdentifier(String releaseName, long deleteAfter) {
    return CgK8sReleaseIdentifier.builder()
        .releaseName(releaseName)
        .namespace("namespace-" + releaseName)
        .deleteAfter(deleteAfter)
        .deploymentIdentifiers(Collections.singleton(
            BasicDeploymentIdentifier.builder().lastDeploymentSummaryUuid(UUID.randomUUID().toString()).build()))
        .build();
  }
}