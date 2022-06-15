/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.spring;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.entities.NGFile;
import io.harness.repositories.custom.FileStoreRepositoryCustom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(CDP)
@HarnessRepo
public interface FileStoreRepository extends PagingAndSortingRepository<NGFile, String>, FileStoreRepositoryCustom {
  List<NGFile> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentIdentifier);
  List<NGFile> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifierNotAndPathStartsWith(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String path,
      Sort sort);
  Optional<NGFile> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  Optional<NGFile> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentIdentifier, String name);
}
