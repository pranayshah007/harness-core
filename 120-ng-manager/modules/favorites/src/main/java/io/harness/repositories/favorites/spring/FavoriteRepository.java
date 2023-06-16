/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.favorites.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.repositories.favorites.custom.FavoriteRepositoryCustom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface FavoriteRepository extends PagingAndSortingRepository<Favorite, String>, FavoriteRepositoryCustom {
  List<Favorite> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userIdentifier,
      ResourceType resourceType);

  List<Favorite> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userIdentifier);

  Optional<Favorite>
  findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userIdentifier,
      ResourceType resourceType, String resourceId);

  void
  deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userIdentifier,
      ResourceType resourceType, String resourceIdentifier);
}