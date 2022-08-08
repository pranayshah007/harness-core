/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.dtos.SaasGitDTO;
import io.harness.utils.FilePathUtils;

import java.net.URL;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@Slf4j
@OwnedBy(DX)
public class GitUtils {
  public SaasGitDTO isSaasGit(String repoURL) {
    try {
      URL url = new URL(getURLWithHttp(repoURL));
      String host = getHostNameWithWWW(url.getHost());
      for (RepoProviders repoProvider : RepoProviders.values()) {
        if (StringUtils.containsIgnoreCase(host, repoProvider.name())) {
          return SaasGitDTO.builder()
              .isSaasGit(host.contains("www." + repoProvider.name().toLowerCase() + ".com")
                  || host.contains("www." + repoProvider.name().toLowerCase() + ".org"))
              .build();
        }
      }
    } catch (Exception e) {
      log.error("Failed to generate Git Provider Repository Url {}", repoURL, e);
    }
    return SaasGitDTO.builder().isSaasGit(false).build();
  }

  String getURLWithHttp(String url) {
    return url.startsWith("http") ? url : ("http://" + url);
  }

  String getHostNameWithWWW(String host) {
    return (host.startsWith("www.")) ? host : ("www." + host);
  }

  public static String convertToUrlWithGit(String url) {
    if (isEmpty(url)) {
      return url;
    }

    url = FilePathUtils.removeTrailingChars(url, "/");
    if (!url.endsWith(".git")) {
      return url;
    }
    final int lastIndexOfGit = url.lastIndexOf(".git");
    final CharSequence repoUrlWithGitRemoved = url.subSequence(0, lastIndexOfGit);
    return repoUrlWithGitRemoved.toString();
  }
}
