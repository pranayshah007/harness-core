/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ScmGitRefManagerTest extends CIExecutionTestBase {
  @Inject ScmGitRefManager scmGitRefManager;
  @Mock SecretUtils secretUtils;
  @Mock ScmServiceClient scmServiceClient;
  @Mock SCMGrpc.SCMBlockingStub scmBlockingStub;

  private final String accountId = "test";
  private final String connectorId = "test";
  @Before
  public void setUp() {
    on(scmGitRefManager).set("secretUtils", secretUtils);
    on(scmGitRefManager).set("scmServiceClient", scmServiceClient);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void fetchCodebaseMetadataWithBranch() {
    ConnectorDetails connectorDetails = getConnector();
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    String branch = "main";
    GetLatestCommitResponse commitResponse = GetLatestCommitResponse.newBuilder().setStatus(200).setError("").build();

    when(scmServiceClient.getLatestCommit(any(), any(), any(), any())).thenReturn(commitResponse);
    ScmGitRefTaskResponseData response =
        scmGitRefManager.fetchCodebaseMetadata(scmConnector, connectorId, branch, "", "");
    assertEquals(branch, response.getBranch());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void fetchCodebaseMetadataWithTag() {
    ConnectorDetails connectorDetails = getConnector();
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    String tag = "v0.0.1";
    GetLatestCommitResponse commitResponse = GetLatestCommitResponse.newBuilder().setStatus(200).setError("").build();

    when(scmServiceClient.getLatestCommit(any(), any(), any(), any())).thenReturn(commitResponse);
    scmGitRefManager.fetchCodebaseMetadata(scmConnector, connectorId, "", "", tag);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getScmConnector() {
    ConnectorDetails connectorDetails = getConnector();

    DecryptableEntity decryptableEntity = mock(GithubApiAccessSpecDTO.class);
    when(secretUtils.decryptViaManager(any(), any(), any(), any())).thenReturn(decryptableEntity);

    ScmConnector scmConnector = scmGitRefManager.getScmConnector(connectorDetails, accountId, "test");
    assertEquals(decryptableEntity, ((GithubConnectorDTO) scmConnector).getApiAccess().getSpec());
  }

  private ConnectorDetails getConnector() {
    ConnectorConfigDTO connectorConfigDTO = GithubConnectorDTO.builder()
                                                .connectionType(GitConnectionType.REPO)
                                                .url("https://github.com/harness/harness-core")
                                                .apiAccess(GithubApiAccessDTO.builder().build())
                                                .build();
    return ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).connectorConfig(connectorConfigDTO).build();
  }
}
