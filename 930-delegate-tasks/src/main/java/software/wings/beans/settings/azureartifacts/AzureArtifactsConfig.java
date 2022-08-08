/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.settings.azureartifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;

import software.wings.annotation.EncryptableSetting;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
public interface AzureArtifactsConfig extends EncryptableSetting, ExecutionCapabilityDemander {
  String getAzureDevopsUrl();
}
