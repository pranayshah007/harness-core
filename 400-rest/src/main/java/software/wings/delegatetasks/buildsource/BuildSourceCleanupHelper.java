/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.ExceptionLogger;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ArtifactService;
import software.wings.utils.DelegateArtifactCollectionUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BuildSourceCleanupHelper {
  private transient ArtifactService artifactService;
  private FeatureFlagService featureFlagService;
  private ArtifactCollectionUtils artifactCollectionUtils;

  @Inject
  public BuildSourceCleanupHelper(ArtifactService artifactService, FeatureFlagService featureFlagService,
      ArtifactCollectionUtils artifactCollectionUtils) {
    this.artifactCollectionUtils = artifactCollectionUtils;
    this.artifactService = artifactService;
    this.featureFlagService = featureFlagService;
  }

  public void cleanupArtifacts(String accountId, ArtifactStream artifactStream, List<BuildDetails> builds) {
    try {
      if (isEmpty(builds)) {
        log.warn(
            "ASYNC_ARTIFACT_CLEANUP: Skipping because of empty builds list for accountId:[{}] artifactStreamId:[{}]",
            accountId, artifactStream.getUuid());
        return;
      }

      List<Artifact> artifacts = processBuilds(artifactStream, builds);
      if (isNotEmpty(artifacts)) {
        log.info("[{}] artifacts deleted for artifactStreamId {}",
            artifacts.stream().map(Artifact::getBuildNo).collect(Collectors.toList()), artifactStream.getUuid());
      }
    } catch (WingsException ex) {
      ex.addContext(Account.class, accountId);
      ex.addContext(ArtifactStream.class, artifactStream.getUuid());
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
    }
  }

  private List<Artifact> processBuilds(ArtifactStream artifactStream, List<BuildDetails> builds) {
    List<Artifact> deletedArtifacts = new ArrayList<>();
    if (artifactStream == null) {
      log.info("Artifact Stream {} does not exist. Returning", artifactStream.getUuid());
      return deletedArtifacts;
    }
    String artifactStreamType = artifactStream.getArtifactStreamType();
    if (DOCKER.name().equals(artifactStreamType)) {
      cleanupDockerArtifacts(artifactStream, deletedArtifacts, builds);
    } else if (AMI.name().equals(artifactStreamType)) {
      cleanupAMIArtifacts(artifactStream, deletedArtifacts, builds);
    } else if (Stream.of(ARTIFACTORY, GCR, ECR, ACR, NEXUS, AZURE_MACHINE_IMAGE)
                   .anyMatch(at -> at.name().equals(artifactStreamType))) {
      // This might not work for Nexus as we are also calling update nexus status
      List<Artifact> deletedArtifactsNew = cleanupStaleArtifacts(artifactStream, builds);
      deletedArtifacts.addAll(deletedArtifactsNew);
    }

    return deletedArtifacts;
  }

  private void cleanupDockerArtifacts(
      ArtifactStream artifactStream, List<Artifact> deletedArtifacts, List<BuildDetails> builds) {
    Set<String> buildNumbers =
        isEmpty(builds) ? new HashSet<>() : builds.stream().map(BuildDetails::getNumber).collect(Collectors.toSet());
    List<Artifact> deletedArtifactsNew = new ArrayList<>();
    try (HIterator<Artifact> artifacts = new HIterator<>(artifactService.prepareCleanupQuery(artifactStream).fetch())) {
      for (Artifact artifact : artifacts) {
        if (!buildNumbers.contains(artifact.getBuildNo())) {
          deletedArtifactsNew.add(artifact);
        }
      }
    }

    if (isEmpty(deletedArtifactsNew)) {
      return;
    }

    artifactService.deleteArtifacts(deletedArtifactsNew);
    deletedArtifacts.addAll(deletedArtifactsNew);
  }

  private void cleanupAMIArtifacts(
      ArtifactStream artifactStream, List<Artifact> deletedArtifacts, List<BuildDetails> builds) {
    Set<String> revisionNumbers =
        isEmpty(builds) ? new HashSet<>() : builds.stream().map(BuildDetails::getRevision).collect(Collectors.toSet());
    List<Artifact> artifactsToBeDeleted = new ArrayList<>();
    try (HIterator<Artifact> artifacts = new HIterator<>(artifactService.prepareCleanupQuery(artifactStream).fetch())) {
      for (Artifact artifact : artifacts) {
        if (artifact != null && (artifact.getRevision() != null) && !revisionNumbers.contains(artifact.getRevision())) {
          artifactsToBeDeleted.add(artifact);
        }
      }
    }

    if (isEmpty(artifactsToBeDeleted)) {
      return;
    }

    artifactService.deleteArtifacts(artifactsToBeDeleted);
    deletedArtifacts.addAll(artifactsToBeDeleted);
  }

  private List<Artifact> cleanupStaleArtifacts(ArtifactStream artifactStream, List<BuildDetails> buildDetails) {
    log.info("Artifact Stream {} cleanup started with type {} name {}", artifactStream.getUuid(),
        artifactStream.getArtifactStreamType(), artifactStream.getSourceName());
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactCollectionUtils.getArtifactStreamAttributes(artifactStream,
            featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, artifactStream.getAccountId()));
    Map<String, BuildDetails> buildDetailsMap;
    if (isEmpty(buildDetails)) {
      buildDetailsMap = Collections.emptyMap();
    } else {
      Function<BuildDetails, String> buildDetailsKeyFn = DelegateArtifactCollectionUtils.getBuildDetailsKeyFn(
          artifactStream.getArtifactStreamType(), artifactStreamAttributes);
      buildDetailsMap = buildDetails.stream().collect(Collectors.toMap(buildDetailsKeyFn, Function.identity()));
    }

    Function<Artifact, String> artifactKeyFn =
        ArtifactCollectionUtils.getArtifactKeyFn(artifactStream.getArtifactStreamType(), artifactStreamAttributes);
    List<Artifact> toBeDeletedArtifacts = new ArrayList<>();
    try (HIterator<Artifact> artifactHIterator =
             new HIterator<>(artifactService.prepareCleanupQuery(artifactStream).fetch())) {
      for (Artifact artifact : artifactHIterator) {
        if (!buildDetailsMap.containsKey(artifactKeyFn.apply(artifact))) {
          toBeDeletedArtifacts.add(artifact);
        }
      }
    }

    artifactService.deleteArtifacts(toBeDeletedArtifacts);

    log.info("Artifact Stream {} cleanup complete with type {}, count {}", artifactStream.getUuid(),
        artifactStream.getArtifactStreamType(), toBeDeletedArtifacts.size());
    return toBeDeletedArtifacts;
  }
}
