/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_JAR;
import static io.harness.delegate.beans.VersionOverrideType.UPGRADER_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.WATCHER_JAR;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.configuration.DeployMode;
import io.harness.delegate.beans.VersionOverride;
import io.harness.delegate.beans.VersionOverride.VersionOverrideKeys;
import io.harness.delegate.beans.VersionOverrideType;
import io.harness.network.Http;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateRingService;

import software.wings.app.MainConfiguration;
import software.wings.service.impl.infra.InfraDownloadService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateVersionService {
  public static final String DEFAULT_DELEGATE_IMAGE_TAG = "harness/delegate:latest";
  public static final String DEFAULT_UPGRADER_IMAGE_TAG = "harness/upgrader:latest";

  private static final String IMMUTABLE_DELEGATE_DOCKER_IMAGE = "IMMUTABLE_DELEGATE_DOCKER_IMAGE";
  private static final String UPGRADER_DOCKER_IMAGE = "UPGRADER_DOCKER_IMAGE";
  private final DelegateRingService delegateRingService;
  private final InfraDownloadService infraDownloadService;
  private final MainConfiguration mainConfiguration;
  private final HPersistence persistence;

  public String getDelegateImageTag(final String accountId, boolean immutable) {
    final VersionOverride versionOverride = getVersionOverride(accountId, DELEGATE_IMAGE_TAG);
    if (versionOverride != null && isNotBlank(versionOverride.getVersion())) {
      return versionOverride.getVersion();
    }

    if (immutable) {
      if (DeployMode.isOnPrem(mainConfiguration.getDeployMode().name())) {
        return fetchImmutableDelegateImageOnPrem();
      }
      final String ringImage = delegateRingService.getDelegateImageTag(accountId);
      if (isNotBlank(ringImage)) {
        return ringImage;
      }
    }

    final String managerConfigImage = mainConfiguration.getPortal().getDelegateDockerImage();
    if (isNotBlank(managerConfigImage)) {
      return managerConfigImage;
    }
    return DEFAULT_DELEGATE_IMAGE_TAG;
  }

  private String fetchImmutableDelegateImageOnPrem() {
    final String immutableDelegateImage = System.getenv(IMMUTABLE_DELEGATE_DOCKER_IMAGE);
    if (isNotBlank(immutableDelegateImage)) {
      return immutableDelegateImage;
    }
    throw new IllegalStateException("No immutable delegate image is defined in manager configMap");
  }

  /**
   * Separate function to generate delegate image tag for delegates in ng. Keeping a separate function for
   * ng delegates because we don't want to pass igNgDelegate parameter as part of above function.
   *
   * @param accountId
   * @return
   */
  public String getImmutableDelegateImageTag(final String accountId) {
    final VersionOverride versionOverride = getVersionOverride(accountId, DELEGATE_IMAGE_TAG);
    if (versionOverride != null && isNotBlank(versionOverride.getVersion())) {
      return versionOverride.getVersion();
    }

    if (DeployMode.isOnPrem(mainConfiguration.getDeployMode().name())) {
      return fetchImmutableDelegateImageOnPrem();
    }

    final String ringImage = delegateRingService.getDelegateImageTag(accountId);
    if (isNotBlank(ringImage)) {
      return ringImage;
    }
    throw new IllegalStateException("No immutable delegate image tag found in ring");
  }

  public String getUpgraderImageTag(final String accountId, boolean immutable) {
    final VersionOverride versionOverride = getVersionOverride(accountId, UPGRADER_IMAGE_TAG);
    if (versionOverride != null && isNotBlank(versionOverride.getVersion())) {
      return versionOverride.getVersion();
    }

    if (DeployMode.isOnPrem(mainConfiguration.getDeployMode().name())) {
      final String upgraderImage = System.getenv(UPGRADER_DOCKER_IMAGE);
      if (isNotBlank(upgraderImage)) {
        return upgraderImage;
      }
    }

    final String ringImage = delegateRingService.getUpgraderImageTag(accountId);
    if (immutable && isNotBlank(ringImage)) {
      return ringImage;
    }

    return DEFAULT_UPGRADER_IMAGE_TAG;
  }

  public List<String> getDelegateJarVersions(final String accountId) {
    final VersionOverride versionOverride = getVersionOverride(accountId, DELEGATE_JAR);
    if (versionOverride != null && isNotBlank(versionOverride.getVersion())) {
      return singletonList(versionOverride.getVersion());
    }

    final List<String> ringVersion = delegateRingService.getDelegateVersions(accountId);
    if (!CollectionUtils.isEmpty(ringVersion)) {
      return ringVersion;
    }

    return Collections.emptyList();
  }

  public List<String> getDelegateJarVersions(final String ringName, final String accountId) {
    if (isNotEmpty(accountId)) {
      final VersionOverride versionOverride = getVersionOverride(accountId, DELEGATE_JAR);
      if (versionOverride != null && isNotBlank(versionOverride.getVersion())) {
        return Collections.singletonList(versionOverride.getVersion());
      }
    }

    final List<String> ringVersion = delegateRingService.getDelegateVersionsForRing(ringName, true);
    if (!CollectionUtils.isEmpty(ringVersion)) {
      return ringVersion;
    }
    return Collections.emptyList();
  }

  public String getWatcherJarVersions(final String accountId) {
    final VersionOverride versionOverride = getVersionOverride(accountId, WATCHER_JAR);
    if (versionOverride != null && isNotBlank(versionOverride.getVersion())) {
      return versionOverride.getVersion();
    }
    final String watcherVerionFromRing = delegateRingService.getWatcherVersions(accountId);
    if (isNotEmpty(watcherVerionFromRing)) {
      return watcherVerionFromRing;
    }
    // Get watcher version from gcp.
    final String watcherMetadataUrl = infraDownloadService.getCdnWatcherMetaDataFileUrl();
    try {
      final String watcherMetadata = Http.getResponseStringFromUrl(watcherMetadataUrl, 10, 10);
      return substringBefore(watcherMetadata, " ").trim();
    } catch (Exception ex) {
      log.error("Unable to fetch watcher version from {} ", watcherMetadataUrl, ex);
      throw new IllegalStateException("Unable to fetch watcher version");
    }
  }

  private VersionOverride getVersionOverride(final String accountId, final VersionOverrideType overrideType) {
    return persistence.createQuery(VersionOverride.class)
        .filter(VersionOverrideKeys.accountId, accountId)
        .filter(VersionOverrideKeys.overrideType, overrideType)
        .get();
  }
}
