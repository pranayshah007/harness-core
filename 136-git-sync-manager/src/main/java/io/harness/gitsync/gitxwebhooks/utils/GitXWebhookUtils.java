/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
@OwnedBy(PIPELINE)
public class GitXWebhookUtils {
  public List<String> compareFolderPaths(List<String> webhookFolderPaths, List<String> modifiedFolderPaths) {
    ArrayList<String> matchingFolderPaths = new ArrayList<>();
    if (modifiedFolderPaths == null || modifiedFolderPaths.isEmpty()) {
      return matchingFolderPaths;
    }
    for (String webhookFolderPath : webhookFolderPaths) {
      Pattern webhookPattern = Pattern.compile(webhookFolderPath);
      for (String modifiedFolderPath : modifiedFolderPaths) {
        Matcher m = webhookPattern.matcher(modifiedFolderPath);
        if (m.find()) {
          matchingFolderPaths.add(modifiedFolderPath);
        }
      }
    }
    return matchingFolderPaths;
  }

  public String massageInputStringIntoRegex(String folderPath) {
    return null;
  }
}
