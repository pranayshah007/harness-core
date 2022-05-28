/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import software.wings.beans.artifact.ArtifactMetadataKeys;

import java.util.Map;

public class CommandUnitHelper {
  public void addArtifactFileNameToEnv(Map<String, String> envVariables, CommandExecutionContext context) {
    if (context.isMultiArtifact()) {
      if (isNotEmpty(context.getArtifactFileName())) {
        envVariables.put("ARTIFACT_FILE_NAME", context.getArtifactFileName());
      }
    } else {
      if (context.getMetadata() != null) {
        String value = context.getMetadata().get(ArtifactMetadataKeys.artifactFileName);
        if (isNotEmpty(value)) {
          envVariables.put("ARTIFACT_FILE_NAME", value);
        }
      } else if (isNotEmpty(context.getArtifactFiles())) {
        String name = context.getArtifactFiles().get(0).getName();
        if (isNotEmpty(name)) {
          envVariables.put("ARTIFACT_FILE_NAME", name);
        }
      }
    }
  }
}
