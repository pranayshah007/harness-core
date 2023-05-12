/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.k8sbase.K8sLegacyReleaseHandlerImpl;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sLegacyReleaseHandlerImplTest extends CategoryTest {
  @Mock K8sTaskHelperBase taskHelperBase;
  @InjectMocks K8sLegacyReleaseHandlerImpl releaseHandler;

  private static final String EMPTY_RELEASE = "emptyRelease";
  private static final String SOME_RELEASE = "someRelease";
  private static final String SOME_RELEASE_HISTORY_YAML = "someYaml";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetReleaseHistory() throws Exception {
    doReturn("").when(taskHelperBase).getReleaseHistoryData(any(), eq(EMPTY_RELEASE));

    ReleaseHistory releaseHistoryData = ReleaseHistory.builder().version("v1").build();
    doReturn(releaseHistoryData.getAsYaml()).when(taskHelperBase).getReleaseHistoryData(any(), eq(SOME_RELEASE));

    K8SLegacyReleaseHistory emptyReleaseHistory =
        (K8SLegacyReleaseHistory) releaseHandler.getReleaseHistory(KubernetesConfig.builder().build(), EMPTY_RELEASE);
    assertThat(emptyReleaseHistory.getReleaseHistory().getReleases()).isEmpty();

    K8SLegacyReleaseHistory releaseHistory =
        (K8SLegacyReleaseHistory) releaseHandler.getReleaseHistory(KubernetesConfig.builder().build(), SOME_RELEASE);
    assertThat(releaseHistory.getReleaseHistory()).isNotNull();
    assertThat(releaseHistory.getReleaseHistory().getVersion()).isEqualTo("v1");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSaveRelease() throws Exception {
    doNothing().when(taskHelperBase).saveReleaseHistory(any(), anyString(), anyString(), anyBoolean());
    K8SLegacyReleaseHistory legacyReleaseHistory = mock(K8SLegacyReleaseHistory.class);
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    doReturn(releaseHistory).when(legacyReleaseHistory).getReleaseHistory();
    doReturn(SOME_RELEASE_HISTORY_YAML).when(releaseHistory).getAsYaml();

    releaseHandler.saveRelease(K8sReleasePersistDTO.builder()
                                   .releaseName(SOME_RELEASE)
                                   .releaseHistory(legacyReleaseHistory)
                                   .storeInSecrets(false)
                                   .build());
    verify(taskHelperBase).saveReleaseHistory(any(), eq(SOME_RELEASE), eq(SOME_RELEASE_HISTORY_YAML), eq(false));
  }
}
