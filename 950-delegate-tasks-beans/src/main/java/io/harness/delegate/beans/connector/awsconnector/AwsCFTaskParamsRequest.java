/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCFTemplatesType;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class AwsCFTaskParamsRequest extends AwsTaskParams {
  private String data;
  private GitStoreDelegateConfig gitStoreDelegateConfig;
  private AwsCFTemplatesType fileStoreType;
  private String accountId;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    if (fileStoreType == AwsCFTemplatesType.GIT) {
      capabilityList.add(GitConnectionNGCapability.builder()
                             .gitConfig(gitStoreDelegateConfig.getGitConfigDTO())
                             .encryptedDataDetails(gitStoreDelegateConfig.getEncryptedDataDetails())
                             .sshKeySpecDTO(gitStoreDelegateConfig.getSshKeySpecDTO())
                             .build());

      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      if (isNotEmpty(gitConfigDTO.getDelegateSelectors())) {
        capabilityList.add(SelectorCapability.builder().selectors(gitConfigDTO.getDelegateSelectors()).build());
      }
    }
    capabilityList.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnector, maskingEvaluator));
    return capabilityList;
  }
}
