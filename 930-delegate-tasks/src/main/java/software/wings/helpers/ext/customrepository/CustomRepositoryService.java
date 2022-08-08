/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.customrepository;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;

public interface CustomRepositoryService {
  List<BuildDetails> getBuilds(ArtifactStreamAttributes artifactStreamAttributes);

  boolean validateArtifactSource(ArtifactStreamAttributes artifactStreamAttributes);
}
