/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.spec.server.idp.v1.model.GenerateYamlRequest;
import io.harness.spec.server.idp.v1.model.GenerateYamlResponse;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesBase;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.ManualImportEntityRequest;

import java.util.concurrent.ExecutionException;

@OwnedBy(HarnessTeam.IDP)
public interface OnboardingService {
  HarnessEntitiesCountResponse getHarnessEntitiesCount(String accountIdentifier);

  PageResponse<HarnessBackstageEntities> getHarnessEntities(String accountIdentifier, int page, int limit, String sort,
      String order, String searchTerm, String projectToFilter);

  GenerateYamlResponse generateYaml(String harnessAccount, GenerateYamlRequest generateYamlRequest);

  ImportEntitiesResponse importHarnessEntities(
      String accountIdentifier, ImportEntitiesBase importHarnessEntitiesRequest) throws ExecutionException;

  ImportEntitiesResponse manualImportEntity(String harnessAccount, ManualImportEntityRequest manualImportEntityRequest);
}
