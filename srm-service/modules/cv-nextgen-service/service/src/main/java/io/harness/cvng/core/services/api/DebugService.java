/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.core.beans.CompositeSLODebugResponse;
import io.harness.cvng.core.beans.SLODebugResponse;
import io.harness.cvng.core.beans.VerifyStepDebugResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.jobs.FakeFeatureFlagSRMProducer;

public interface DebugService {
  SLODebugResponse getSLODebugResponse(ProjectParams projectParams, String identifier);

  boolean forceDeleteSLO(ProjectParams projectParams, String sloIdentifier);

  VerifyStepDebugResponse getVerifyStepDebugResponse(ProjectParams projectParams, String identifier);

  CompositeSLODebugResponse getCompositeSLODebugResponse(ProjectParams projectParams, String identifier);
  DataCollectionTask retryDataCollectionTask(ProjectParams projectParams, String identifier);

  boolean registerInternalChangeEvent(ProjectParams projectParams, ChangeEventDTO changeEventDTO);

  void registerFFChangeEvent(FakeFeatureFlagSRMProducer.FFEventBody ffEventBody);
}
