/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ImportHarnessEntitiesRequest;
import io.harness.spec.server.idp.v1.model.ManualImportEntityRequest;
import io.harness.spec.server.idp.v1.model.OnboardingAccessCheckResponse;

@OwnedBy(HarnessTeam.IDP)
public interface OnboardingService {
  OnboardingAccessCheckResponse accessCheck(String accountIdentifier, String userId);

  HarnessEntitiesResponse getHarnessEntities(
      String accountIdentifier, int page, int limit, String sort, String order, String searchTerm);

  ImportEntitiesResponse importHarnessEntities(
      String accountIdentifier, ImportHarnessEntitiesRequest importHarnessEntitiesRequest);

  ImportEntitiesResponse manualImportEntity(String harnessAccount, ManualImportEntityRequest manualImportEntityRequest);
}
