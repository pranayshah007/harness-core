/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.terraform;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;
import io.harness.provision.TfVarSource;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.beans.NameValuePair;
import software.wings.sm.ContextElement;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class TerraformProvisionInheritPlanElement implements ContextElement {
  private String entityId;
  private String provisionerId;
  private List<String> targets;
  private List<String> tfVarFiles;
  private TfVarSource tfVarSource;
  private String sourceRepoSettingId;
  private String sourceRepoReference;
  private List<NameValuePair> variables;
  private List<NameValuePair> backendConfigs;
  private List<NameValuePair> environmentVariables;
  private String workspace;
  private String delegateTag;
  private EncryptedRecordData encryptedTfPlan;
  private String tfPlanJsonFileId;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.TERRAFORM_INHERIT_PLAN;
  }

  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }
}
