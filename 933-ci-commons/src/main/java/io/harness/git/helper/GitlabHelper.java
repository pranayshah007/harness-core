/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git.helper;

import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GitlabHelper {
  public static String getPRLink(GitlabConnectorDTO gitlabConnectorDTO, int prNumber) {
    String url = gitlabConnectorDTO.getUrl();
    int lastIndex = url.lastIndexOf(".git");
    if (lastIndex != -1) {
      url = url.substring(0, lastIndex);
    }
    return url + "/merge_requests/" + prNumber;
  }
}
