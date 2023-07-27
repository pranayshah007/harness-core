/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitCapabilityHelperTest extends CategoryTest {
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities() {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .url("URL")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .isGithubAppAuthentication(true)
                                                        .branch("master")
                                                        .connectorName("terraform")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .build();
    List<ExecutionCapability> executionCapabilityList =
        GitCapabilityHelper.fetchRequiredExecutionCapabilities(gitStoreDelegateConfig, Collections.emptyList());

    assertThat(executionCapabilityList).hasSize(1);
    assertThat(executionCapabilityList.get(0)).isInstanceOf(GitConnectionNGCapability.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilitiesWithOptimizedFileFetch() {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .url("URL")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .isGithubAppAuthentication(true)
            .optimizedFilesFetch(true)
            .apiAuthEncryptedDataDetails(List.of(EncryptedDataDetail.builder().build()))
            .branch("master")
            .connectorName("terraform")
            .gitConfigDTO(gitConfigDTO)
            .build();
    List<ExecutionCapability> executionCapabilityList =
        GitCapabilityHelper.fetchRequiredExecutionCapabilities(gitStoreDelegateConfig, Collections.emptyList());

    assertThat(executionCapabilityList).hasSize(1);
    assertThat(executionCapabilityList.get(0)).isInstanceOf(GitConnectionNGCapability.class);
    assertThat(((GitConnectionNGCapability) executionCapabilityList.get(0)).getEncryptedDataDetails()).hasSize(1);
  }
}
