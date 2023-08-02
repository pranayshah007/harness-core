/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.git;

import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ScmConnectorMapperDelegate {
  public GitConfigDTO toGitConfigDTO(ScmConnector scmConnector, List<EncryptedDataDetail> encryptedDataDetails) {
    return ScmConnectorMapper.toGitConfigDTO(scmConnector);
  }
}