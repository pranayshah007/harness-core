/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.PersistentLockException;
import io.harness.exception.WingsException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/***
 * Service responsible to glue all artifact
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ArtifactCollectionServiceImpl implements ArtifactCollectionService {
  @Inject private PersistentLocker persistentLocker;
  @Inject private BuildSourceService buildSourceService;
  @Inject private ArtifactService artifactService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;

  public static final Duration timeout = Duration.ofMinutes(10);
  public static final Duration waitTimeout = Duration.ofMinutes(9);

  @Override
  public Artifact collectArtifact(String artifactStreamId, BuildDetails buildDetails) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      throw new WingsException("Artifact Stream was deleted", USER);
    }
    return artifactService.create(artifactCollectionUtils.getArtifact(artifactStream, buildDetails));
  }

  @Override
  public void collectNewArtifactsAsync(ArtifactStream artifactStream, String permitId) {}

  @Override
  public Artifact collectNewArtifacts(String appId, ArtifactStream artifactStream, String buildNumber) {
    List<BuildDetails> builds =
        buildSourceService.getBuilds(appId, artifactStream.getUuid(), artifactStream.getSettingId());
    if (EmptyPredicate.isNotEmpty(builds)) {
      Optional<BuildDetails> buildDetails =
          builds.stream().filter(build -> buildNumber.equals(build.getNumber())).findFirst();
      if (buildDetails.isPresent()) {
        return collectArtifact(artifactStream.getUuid(), buildDetails.get());
      }
    }
    return null;
  }

  @Override
  public Artifact collectNewArtifacts(
      String appId, ArtifactStream artifactStream, String buildNumber, Map<String, Object> artifactVariables) {
    BuildDetails buildDetails =
        buildSourceService.getBuild(appId, artifactStream.getUuid(), artifactStream.getSettingId(), artifactVariables);
    if (buildDetails != null) {
      return collectArtifact(artifactStream.getUuid(), buildDetails);
    }
    return null;
  }

  @Override
  public List<Artifact> collectNewArtifacts(String appId, String artifactStreamId) {
    try (AcquiredLock ignored =
             persistentLocker.waitToAcquireLock(ArtifactStream.class, artifactStreamId, timeout, waitTimeout)) {
      ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
      if (artifactStream == null) {
        log.info("Artifact stream: [{}] does not exist. Returning", artifactStreamId);
        return new ArrayList<>();
      }

      log.info("Collecting build details for artifact stream: [{}], type: [{}] and source name: [{}]",
          artifactStream.getUuid(), artifactStream.getArtifactStreamType(), artifactStream.getSourceName());
      List<BuildDetails> builds = buildSourceService.getNewBuilds(
          artifactStream.fetchAppId(), artifactStream.getUuid(), artifactStream.getSettingId());
      if (isEmpty(builds)) {
        return new ArrayList<>();
      }

      // New build are filtered at the delegate. So all the builds coming in the BuildSourceExecutionResponse are the
      // ones not present in the DB.
      return builds.stream()
          .map(
              buildDetails -> artifactService.create(artifactCollectionUtils.getArtifact(artifactStream, buildDetails)))
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error(
          "Not able to fetch the artifact collection for appId: {} , artifactStreamId: {}", appId, artifactStreamId, e);
      throw new PersistentLockException(
          String.format("Cannot acquire lock for artifactStreamId : %s ", artifactStreamId),
          ErrorCode.FAILED_TO_ACQUIRE_PERSISTENT_LOCK, USER);
    }
  }
}
