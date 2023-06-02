/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class GitFetchMetadataLocalThread {
  private static ThreadLocal<Map<String, String>> commitIdContext = new ThreadLocal<>();

  public static void init() {
    commitIdContext.set(new HashMap<>());
  }

  public static void putCommitId(String key, String commit) {
    if (isEmpty(key) || isEmpty(commit)) {
      return;
    }
    if (commitIdContext.get() == null) {
      return;
    }
    commitIdContext.get().put(key, commit);
  }

  public static Map<String, String> getCommitIdMap() {
    if (commitIdContext.get() == null) {
      throw new IllegalStateException("Incorrect usage of GitFetchMetadataLocalThread, use GitFetchMetadataContext");
    }
    return commitIdContext.get();
  }

  public static void cleanup() {
    commitIdContext.remove();
  }
}
