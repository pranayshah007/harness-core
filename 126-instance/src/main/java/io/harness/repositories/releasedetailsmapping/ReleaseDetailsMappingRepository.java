/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.releasedetailsmapping;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.entities.ReleaseDetailsMapping;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
@HarnessRepo
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public interface ReleaseDetailsMappingRepository extends CrudRepository<ReleaseDetailsMapping, String> {
  Optional<ReleaseDetailsMapping> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndReleaseKeyAndInfraKey(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String releaseKey, String infraKey);

  long deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
