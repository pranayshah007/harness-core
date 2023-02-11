/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.bitbucket;

import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public interface BitbucketService {
  boolean sendStatus(BitbucketConfig bitbucketConfig, String userName, String token,
      List<EncryptedDataDetail> encryptionDetails, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap);

  JSONObject mergePR(BitbucketConfig bitbucketConfig, String token, String userName, String org, String name,
      String prNumber, boolean deleteSourceBranch, String ref, boolean isSaaS);

  Boolean deleteRef(BitbucketConfig bitbucketConfig, String token, String org, String name, String ref);
}
