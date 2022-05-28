/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DelegateArtifactCollectionUtils {
  public static final List<String> SUPPORTED_ARTIFACT_CLEANUP_LIST =
      Lists.newArrayList(DOCKER, AMI, ARTIFACTORY, GCR, ECR, ACR, NEXUS, AZURE_MACHINE_IMAGE, CUSTOM)
          .stream()
          .map(Enum::name)
          .collect(Collectors.toList());
  /**
   * getNewBuildDetails returns new BuildDetails after removing Artifact already present in DB.
   *
   * @param savedBuildDetailsKeys    the artifact keys for artifacts already stored in DB
   * @param buildDetails             the new build details fetched from third-party repo
   * @param artifactStreamType       the artifact stream type
   * @param artifactStreamAttributes the artifact stream attributes - used only for ARTIFACTORY
   * @return the new build details
   */
  public static List<BuildDetails> getNewBuildDetails(Set<String> savedBuildDetailsKeys,
      List<BuildDetails> buildDetails, String artifactStreamType, ArtifactStreamAttributes artifactStreamAttributes) {
    if (isEmpty(buildDetails)) {
      return Collections.emptyList();
    }
    if (isEmpty(savedBuildDetailsKeys)) {
      return buildDetails;
    }

    Function<BuildDetails, String> keyFn = getBuildDetailsKeyFn(artifactStreamType, artifactStreamAttributes);
    return buildDetails.stream()
        .filter(singleBuildDetails -> !savedBuildDetailsKeys.contains(keyFn.apply(singleBuildDetails)))
        .collect(Collectors.toList());
  }

  /**
   * getBuildDetailsKeyFn returns a function that can extract a unique key for a BuildDetails object so that it can be
   * compared with an Artifact object.
   *
   * @param artifactStreamType       the artifact stream type
   * @param artifactStreamAttributes the artifact stream attributes - used only for ARTIFACTORY
   * @return the function that can used to get the key for a BuildDetails
   */
  public static Function<BuildDetails, String> getBuildDetailsKeyFn(
      String artifactStreamType, ArtifactStreamAttributes artifactStreamAttributes) {
    if (AMI.name().equals(artifactStreamType)) {
      return BuildDetails::getRevision;
    } else if (isGenericArtifactStream(artifactStreamType, artifactStreamAttributes)) {
      return BuildDetails::getArtifactPath;
    } else {
      return BuildDetails::getNumber;
    }
  }

  /**
   * isGenericArtifactStream returns true if we need to compare artifact paths to check if two artifacts - one stored in
   * our DB and one from an artifact repo - are different.
   *
   * @param artifactStreamType       the artifact stream type
   * @param artifactStreamAttributes the artifact stream attributes - used only for ARTIFACTORY
   * @return true, if generic artifact stream - uses artifact path as key
   */
  public static boolean isGenericArtifactStream(
      String artifactStreamType, ArtifactStreamAttributes artifactStreamAttributes) {
    if (AMAZON_S3.name().equals(artifactStreamType)) {
      return true;
    }
    if (ARTIFACTORY.name().equals(artifactStreamType)) {
      if (artifactStreamAttributes.getArtifactType() != null
          && artifactStreamAttributes.getArtifactType() == ArtifactType.DOCKER) {
        return false;
      }
      return artifactStreamAttributes.getRepositoryType() == null
          || !artifactStreamAttributes.getRepositoryType().equals(RepositoryType.docker.name());
    }
    return false;
  }

  public static boolean supportsCleanup(String artifactStreamType) {
    return SUPPORTED_ARTIFACT_CLEANUP_LIST.stream().anyMatch(x -> x.equals(artifactStreamType));
  }
}
