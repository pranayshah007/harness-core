/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.account;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public enum AccountEventType {
  APP_CREATED,
  SERVICE_CREATED,
  ENV_CREATED,
  WORKFLOW_CREATED,
  WORKFLOW_DEPLOYED,
  PIPELINE_DEPLOYED,
  DELEGATE_INSTALLED,
  CLOUD_PROVIDER_CREATED,
  ARTIFACT_REPO_CREATED,
  PIPELINE_CREATED,
  ARTIFACT_STREAM_ADDED,
  INFRA_MAPPING_ADDED,
  INFRA_DEFINITION_ADDED,
  CUSTOM
}
