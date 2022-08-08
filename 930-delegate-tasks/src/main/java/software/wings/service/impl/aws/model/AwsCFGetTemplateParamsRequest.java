/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.impl.aws.model.AwsCFRequest.AwsCFRequestType.GET_TEMPLATE_PARAMETERS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class AwsCFGetTemplateParamsRequest extends AwsCFRequest {
  @NotNull private String type;
  private String data;
  private List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  private GitConfig gitConfig;
  private GitFileConfig gitFileConfig;

  @Builder
  public AwsCFGetTemplateParamsRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String data,
      String region, String type, String sourceRepoSettingId, String sourceRepoBranch, String templatePath,
      List<EncryptedDataDetail> sourceRepoEncryptionDetails, GitFileConfig gitFileConfig, GitConfig gitConfig) {
    super(awsConfig, encryptionDetails, GET_TEMPLATE_PARAMETERS, region);
    this.data = data;
    this.type = type;
    this.gitFileConfig = gitFileConfig;
    this.gitConfig = gitConfig;
    this.sourceRepoEncryptionDetails = sourceRepoEncryptionDetails;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(super.fetchRequiredExecutionCapabilities(maskingEvaluator));
    if (gitConfig != null && isNotEmpty(gitConfig.getDelegateSelectors())) {
      capabilities.add(SelectorCapability.builder().selectors(new HashSet<>(gitConfig.getDelegateSelectors())).build());
    }
    return capabilities;
  }
}
