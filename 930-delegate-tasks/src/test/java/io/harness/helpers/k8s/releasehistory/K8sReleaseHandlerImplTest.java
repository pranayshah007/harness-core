/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sReleaseHandlerImplTest extends CategoryTest {
  @Mock KubernetesContainerService kubernetesContainerService;

  @InjectMocks K8sReleaseHandlerImpl releaseHandler;

  private static final String RELEASE_NAME = "releaseName";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetReleaseHistory() {
    releaseHandler.getReleaseHistory(KubernetesConfig.builder().build(), RELEASE_NAME);
    verify(kubernetesContainerService)
        .getSecretsWithLabelsAndFields(
            any(), eq("owner=harness,release=" + RELEASE_NAME), eq("type=harness.io/release/v2"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCreateRelease() {
    K8sRelease release = (K8sRelease) releaseHandler.createRelease("name", 1);
    assertThat(release.getReleaseSecret().getMetadata().getName()).isEqualTo("release.name.1");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSaveRelease() throws Exception {
    doReturn(null).when(kubernetesContainerService).createOrReplaceSecret(any(), any());
    V1Secret release = new V1Secret();
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    releaseHandler.saveRelease(K8sReleasePersistDTO.builder()
                                   .kubernetesConfig(kubernetesConfig)
                                   .release(K8sRelease.builder().releaseSecret(release).build())
                                   .build());
    verify(kubernetesContainerService).createOrReplaceSecret(kubernetesConfig, release);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReleaseCleanup() throws Exception {
    List<K8sRelease> releases = new ArrayList<>();
    releases.add(createRelease("1", Succeeded.name()));
    releases.add(createRelease("2", Failed.name()));
    releases.add(createRelease("3", Succeeded.name()));
    releases.add(createRelease("4", Failed.name()));
    releases.add(createRelease("5", Succeeded.name()));
    releases.add(createRelease("6", Failed.name()));
    releases.add(createRelease("7", Succeeded.name()));
    releases.add(createRelease("8", Succeeded.name()));

    LogCallback logCallback = new NGDelegateLogCallback(null, null, false, null);

    K8sReleaseHistory releaseHistory = K8sReleaseHistory.builder().releaseHistory(releases).build();
    K8sReleaseCleanupDTO releaseCleanupDTO = K8sReleaseCleanupDTO.builder()
                                                 .releaseName(RELEASE_NAME)
                                                 .releaseHistory(releaseHistory)
                                                 .logCallback(logCallback)
                                                 .build();

    releaseHandler.cleanReleaseHistory(releaseCleanupDTO);
    verify(kubernetesContainerService)
        .deleteSecrets(any(), eq("release=releaseName,release-number in (2,4,6)"), eq("type=harness.io/release/v2"));
  }

  private K8sRelease createRelease(String releaseNumber, String status) {
    return K8sRelease.builder().releaseSecret(createSecret(releaseNumber, status)).build();
  }

  private V1Secret createSecret(String releaseNumber, String status) {
    return new V1SecretBuilder()
        .withMetadata(new V1ObjectMetaBuilder()
                          .withLabels(Map.of(RELEASE_NUMBER_LABEL_KEY, releaseNumber, RELEASE_STATUS_LABEL_KEY, status))
                          .build())
        .build();
  }
}
