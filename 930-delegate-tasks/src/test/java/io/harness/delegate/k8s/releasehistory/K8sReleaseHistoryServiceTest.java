/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_HARNESS_SECRET_LABELS;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_HARNESS_SECRET_TYPE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1Secret;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class K8sReleaseHistoryServiceTest extends CategoryTest {
  @Spy @InjectMocks K8sReleaseHistoryService releaseHistoryService;
  @Mock KubernetesContainerService kubernetesContainerService;
  @Mock K8sReleaseService releaseService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCreateRelease() {
    doReturn("releaseName").when(releaseService).generateName(anyString(), anyInt());
    V1Secret release = releaseHistoryService.createRelease("name", 1, "status");
    assertThat(release.getMetadata().getName()).isEqualTo("releaseName");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDeleteReleases() {
    String releaseName = "release";
    Set<String> releaseNumbers = Collections.emptySet();
    doReturn("listArg").when(releaseService).createListBasedArg(anyString(), anyString());
    doReturn("setArg").when(releaseService).createSetBasedArg(anyString(), anySet());
    doReturn("fieldArg").when(releaseService).createCommaSeparatedKeyValueList(anyMap());

    releaseHistoryService.deleteReleases(KubernetesConfig.builder().build(), releaseName, releaseNumbers);
    verify(kubernetesContainerService).deleteSecrets(any(), eq("listArg,setArg"), eq("fieldArg"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseCleanup() {
    Set<String> releasesToDelete = Set.of("1", "2");
    doReturn(releasesToDelete).when(releaseService).getReleaseNumbersToClean(anyList(), anyInt());
    doNothing().when(releaseHistoryService).deleteReleases(any(), anyString(), anySet());

    releaseHistoryService.cleanReleaseHistory(
        KubernetesConfig.builder().build(), "release", 2, Collections.emptyList());
    verify(releaseHistoryService).deleteReleases(any(), anyString(), eq(releasesToDelete));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetReleaseHistory() {
    String releaseName = "releaseName";
    Map<String, String> labels = new HashMap<>(RELEASE_HARNESS_SECRET_LABELS);
    labels.put(RELEASE_KEY, releaseName);

    doReturn("labelArg").when(releaseService).createCommaSeparatedKeyValueList(labels);
    doReturn("fieldArg").when(releaseService).createCommaSeparatedKeyValueList(RELEASE_HARNESS_SECRET_TYPE);

    releaseHistoryService.getReleaseHistory(KubernetesConfig.builder().build(), releaseName);
    verify(kubernetesContainerService).getSecretsWithLabelsAndFields(any(), eq("labelArg"), eq("fieldArg"));
  }
}
