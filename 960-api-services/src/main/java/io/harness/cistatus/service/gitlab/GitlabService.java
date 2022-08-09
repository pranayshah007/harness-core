/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.gitlab;

import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public interface GitlabService {
  boolean sendStatus(GitlabConfig bitbucketConfig, String userName, String token,
      List<EncryptedDataDetail> encryptionDetails, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap);

  JSONObject mergePR(String apiUrl, String slug, String token, String prNumber);
}
