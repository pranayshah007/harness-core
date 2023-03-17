/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.governance.service;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequest;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineGovernanceServiceImplTest extends CategoryTest {
  String accountIdentifier = "account";
  String orgIdentifier = "org";
  String projectIdentifier = "project";

  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PmsGitSyncHelper gitSyncHelper;
  @Mock private ExpansionRequestsExtractor expansionRequestsExtractor;
  @Mock private JsonExpander jsonExpander;

  @InjectMocks PipelineGovernanceServiceImpl pipelineGovernanceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONFromYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String dummyYaml = "\"don't really need a proper yaml cuz only testing the flow\"";
    ByteString randomByteString = ByteString.copyFromUtf8("sss");
    ExpansionRequestMetadata expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                                            .setAccountId(accountIdentifier)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .setGitSyncBranchContext(randomByteString)
                                                            .setYaml(ByteString.copyFromUtf8(dummyYaml))
                                                            .build();
    ExpansionRequest dummyRequest = ExpansionRequest.builder().fqn("fqn").build();
    Set<ExpansionRequest> dummyRequestSet = Collections.singleton(dummyRequest);
    doReturn(randomByteString).when(gitSyncHelper).getGitSyncBranchContextBytesThreadLocal();
    doReturn(dummyRequestSet).when(expansionRequestsExtractor).fetchExpansionRequests(dummyYaml);
    ExpansionResponseProto dummyResponse =
        ExpansionResponseProto.newBuilder().setSuccess(false).setErrorMessage("just because").build();
    ExpansionResponseBatch dummyResponseBatch =
        ExpansionResponseBatch.newBuilder().addExpansionResponseProto(dummyResponse).build();
    Set<ExpansionResponseBatch> dummyResponseSet = Collections.singleton(dummyResponseBatch);
    doReturn(dummyResponseSet).when(jsonExpander).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
    pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, dummyYaml, false);
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(dummyYaml);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String noExp = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, dummyYaml, false);
    assertThat(noExp).isEqualTo(dummyYaml);
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(dummyYaml);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONForV1Yaml() {
    String dummyYaml = "\"version: 1\"";
    String noExp = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, dummyYaml, false);
    assertThat(noExp).isEqualTo(dummyYaml);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONFromYamlWithPipelineEntity() {
    String pipelineYaml = "pipeline:\n"
        + "    identifier: cipipeline2GDdkmQLfb\n"
        + "    name: run pipeline with output variable success\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              identifier: outputvar\n"
        + "              name: output variable\n"
        + "              type: CI\n"
        + "              spec:\n"
        + "                  execution:\n"
        + "                      steps:\n"
        + "                          - step:\n"
        + "                                identifier: two\n"
        + "                                name: two\n"
        + "                                type: Run\n"
        + "                                spec:\n"
        + "                                    command: <+input>\n"
        + "                                    shell: Powershell\n"
        + "                  infrastructure:\n"
        + "                      type: VM\n"
        + "                      spec:\n"
        + "                          type: Pool\n"
        + "                          spec:\n"
        + "                              identifier: windows\n"
        + "                  cloneCodebase: false\n"
        + "    projectIdentifier: Plain_Old_Project\n"
        + "    orgIdentifier: default\n";
    ByteString randomByteString = ByteString.copyFromUtf8("sss");
    ExpansionRequestMetadata expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                                            .setAccountId(accountIdentifier)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .setGitSyncBranchContext(randomByteString)
                                                            .setYaml(ByteString.copyFromUtf8(pipelineYaml))
                                                            .build();
    ExpansionRequest dummyRequest = ExpansionRequest.builder().fqn("fqn").build();
    Set<ExpansionRequest> dummyRequestSet = Collections.singleton(dummyRequest);
    doReturn(randomByteString).when(gitSyncHelper).getGitSyncBranchContextBytesThreadLocal();
    doReturn(dummyRequestSet).when(expansionRequestsExtractor).fetchExpansionRequests(pipelineYaml);
    ExpansionResponseProto dummyResponse =
        ExpansionResponseProto.newBuilder().setSuccess(false).setErrorMessage("just because").build();
    ExpansionResponseBatch dummyResponseBatch =
        ExpansionResponseBatch.newBuilder().addExpansionResponseProto(dummyResponse).build();
    Set<ExpansionResponseBatch> dummyResponseSet = Sets.newHashSet(dummyResponseBatch);
    doReturn(dummyResponseSet).when(jsonExpander).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .filePath("filePath")
                                        .repo("repo")
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String noExp =
        pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(pipelineEntity, pipelineYaml, true, "branch");
    assertThat(noExp).isEqualTo(
        "{\"pipeline\":{\"identifier\":\"cipipeline2GDdkmQLfb\",\"name\":\"run pipeline with output variable success\",\"stages\":[{\"stage\":{\"identifier\":\"outputvar\",\"name\":\"output variable\",\"type\":\"CI\",\"spec\":{\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"two\",\"name\":\"two\",\"type\":\"Run\",\"spec\":{\"command\":\"<+input>\",\"shell\":\"Powershell\"}}}]},\"infrastructure\":{\"type\":\"VM\",\"spec\":{\"type\":\"Pool\",\"spec\":{\"identifier\":\"windows\"}}},\"cloneCodebase\":false}}}],\"projectIdentifier\":\"Plain_Old_Project\",\"orgIdentifier\":\"default\",\"gitConfig\":{\"branch\":\"branch\",\"repoName\":\"repo\",\"filePath\":\"filePath\"}}}");
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(pipelineYaml);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
  }
}
