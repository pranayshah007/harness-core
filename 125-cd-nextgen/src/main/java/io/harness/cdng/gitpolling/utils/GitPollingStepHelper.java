/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitpolling.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.gitpolling.bean.GitPollingConfig;
import io.harness.cdng.gitpolling.bean.yaml.GitHubPollingConfig;
import io.harness.cdng.gitpolling.mappers.GitPollingConfigToDelegateReqMapper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.gitpolling.GitPollingSourceDelegateRequest;
import io.harness.exception.InvalidConnectorTypeException;
import io.harness.exception.WingsException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class GitPollingStepHelper {
  @Inject private io.harness.utils.ConnectorUtils connectorUtils;

  public GitPollingSourceDelegateRequest toSourceDelegateRequest(GitPollingConfig gitPollingConfig, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    switch (gitPollingConfig.getSourceType()) {
      case GITHUB:
        GitHubPollingConfig gitHubPollingConfig = (GitHubPollingConfig) gitPollingConfig;

        IdentifierRef identifierRef =
            IdentifierRefHelper.getIdentifierRef(gitHubPollingConfig.getConnectorRef().getValue(),
                ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

        ConnectorDetails connectorDetails =
            connectorUtils.getConnectorDetails(identifierRef, identifierRef.buildScopedIdentifier());

        if (connectorDetails == null) {
          throw new InvalidConnectorTypeException("provided Connector "
                  + gitHubPollingConfig.getConnectorRef().getValue() + " is not compatible with "
                  + gitPollingConfig.getSourceType() + " Webhook",
              WingsException.USER);
        }
        return GitPollingConfigToDelegateReqMapper.getGitHubDelegateRequest(
            connectorDetails, gitHubPollingConfig.getWebhookId(), gitHubPollingConfig.getRepository().getValue());
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown GitPolling/Webhook Config type: [%s]", gitPollingConfig.getSourceType()));
    }
  }
}
