/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.factory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.AzureRepoConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.BitbucketConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GithubConnectorProcessor;
import io.harness.idp.gitintegration.processor.impl.GitlabConnectorProcessor;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
public class ConnectorProcessorFactory {
  @Inject GithubConnectorProcessor githubConnectorProcessor;
  @Inject GitlabConnectorProcessor gitlabConnectorProcessor;
  @Inject BitbucketConnectorProcessor bitbucketConnectorProcessor;
  @Inject AzureRepoConnectorProcessor azureRepoConnectorProcessor;

  public ConnectorProcessor getConnectorProcessor(ConnectorType connectorType) {
    if (connectorType == null) {
      return null;
    }

    switch (connectorType) {
      case GITHUB:
        return githubConnectorProcessor;
      case GITLAB:
        return gitlabConnectorProcessor;
      case BITBUCKET:
        return bitbucketConnectorProcessor;
      case AZURE_REPO:
        return azureRepoConnectorProcessor;
      default:
        throw new UnsupportedOperationException("Invalid Connector type for git integrations");
    }
  }
}
