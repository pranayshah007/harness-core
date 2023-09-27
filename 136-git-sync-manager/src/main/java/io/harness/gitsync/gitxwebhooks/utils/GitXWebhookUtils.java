/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class GitXWebhookUtils {
  public List<String> compareFolderPaths(List<String> webhookFolderPaths, List<String> modifiedFilePaths) {
    ArrayList<String> matchingFolderPaths = new ArrayList<>();
    if (isEmpty(modifiedFilePaths)) {
      return matchingFolderPaths;
    }
    webhookFolderPaths.forEach(webhookFolderPath -> {
      int webhookFolderPathLength = webhookFolderPath.length();
      modifiedFilePaths.forEach(modifiedFilePath -> {
        int modifiedFilePathLength = modifiedFilePath.length();
        if (webhookFolderPathLength > modifiedFilePathLength) {
          return;
        }
        String modifiedFilePathSubstring = modifiedFilePath.substring(0, webhookFolderPathLength);
        if (webhookFolderPath.equals(modifiedFilePathSubstring)) {
          matchingFolderPaths.add(modifiedFilePath);
        }
      });
    });
    return matchingFolderPaths;
  }
}