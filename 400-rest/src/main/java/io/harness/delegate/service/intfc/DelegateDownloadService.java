/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.DelegateDownloadResponse;
import io.harness.delegate.beans.DelegateDownloadRequest;

@OwnedBy(DEL)
public interface DelegateDownloadService {
  DelegateDownloadResponse downloadKubernetesDelegate(String accountId, String orgIdentifier, String projectIdentifier,
      DelegateDownloadRequest delegateDownloadRequest, String managerHost, String verificationServiceUrl);

  DelegateDownloadResponse downloadDockerDelegate(String accountId, String orgIdentifier, String projectIdentifier,
      DelegateDownloadRequest delegateDownloadRequest, String managerHost, String verificationServiceUrl);
}
