/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.DX)
public class ScmGitCapabilityHelper {
  public List<ExecutionCapability> getHttpConnectionCapability(ScmConnector scmConnector) {
    return getHttpConnectionCapability(scmConnector, true);
  }

  public List<ExecutionCapability> getHttpConnectionCapability(
      ScmConnector scmConnector, boolean includeDelegateSelectors) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(scmConnector);
    if (gitConfigDTO.getGitAuthType().equals(GitAuthType.HTTP)) {
      List<ExecutionCapability> executionCapabilities = new ArrayList<>();
      executionCapabilities.add(HttpConnectionExecutionCapability.builder().url(scmConnector.getUrl()).build());
      if (includeDelegateSelectors && isNotEmpty(gitConfigDTO.getDelegateSelectors())) {
        executionCapabilities.add(SelectorCapability.builder().selectors(gitConfigDTO.getDelegateSelectors()).build());
      }
      return executionCapabilities;
    }

    throw new InvalidRequestException("HTTP authentication is required");
  }
}
