/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public interface PollingConstants {
  int MANIFEST_COLLECTION_NG_INTERVAL_MINUTES = 2;
  int MANIFEST_COLLECTION_NG_TIMEOUT_MINUTES = 3;
  int ARTIFACT_COLLECTION_NG_INTERVAL_MINUTES = 1;
  int ARTIFACT_COLLECTION_NG_TIMEOUT_MINUTES = 2;
  int WEBHOOK_POLLING_VALIDITY_INTERVAL_MULTIPLIER = 2;
}
