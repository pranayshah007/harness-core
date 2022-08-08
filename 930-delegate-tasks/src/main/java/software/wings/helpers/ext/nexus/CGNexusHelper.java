/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.common.AlphanumComparator;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class CGNexusHelper {
  @NotNull
  @SuppressWarnings("squid:S00107")
  public List<BuildDetails> constructBuildDetails(String repoId, String groupId, String artifactName,
      List<String> versions, Map<String, String> versionToArtifactUrls,
      Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls, String extension, String classifier) {
    log.info("Versions come from nexus server {}", versions);
    versions = versions.stream().sorted(new AlphanumComparator()).collect(toList());
    log.info("After sorting alphanumerically versions {}", versions);

    return versions.stream()
        .map(version -> {
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.repositoryName, repoId);
          metadata.put(ArtifactMetadataKeys.nexusGroupId, groupId);
          metadata.put(ArtifactMetadataKeys.nexusArtifactId, artifactName);
          metadata.put(ArtifactMetadataKeys.version, version);
          if (isNotEmpty(extension)) {
            metadata.put(ArtifactMetadataKeys.extension, extension);
          }
          if (isNotEmpty(classifier)) {
            metadata.put(ArtifactMetadataKeys.classifier, classifier);
          }
          return aBuildDetails()
              .withNumber(version)
              .withRevision(version)
              .withBuildUrl(versionToArtifactUrls.get(version))
              .withMetadata(metadata)
              .withUiDisplayName("Version# " + version)
              .withArtifactDownloadMetadata(versionToArtifactDownloadUrls.get(version))
              .build();
        })
        .collect(toList());
  }
}
