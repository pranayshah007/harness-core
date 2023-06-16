/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.service.impl;

import static io.harness.rule.OwnerRule.BOOPESH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.impl.FavoritesServiceImpl;
import io.harness.favorites.utils.FavoritesResourceUtils;
import io.harness.favorites.utils.FavoritesValidator;
import io.harness.repositories.favorites.spring.FavoriteRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoritesResourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
@OwnedBy(HarnessTeam.PL)
public class FavoritesServiceImplTest extends CategoryTest {
  @Mock private FavoritesValidator favoritesValidator;

  @Mock private FavoritesResourceUtils favoritesResourceUtils;

  @Mock private FavoriteRepository favoriteRepository;

  private FavoritesServiceImpl favoriteService;
  private static String userId = randomAlphabetic(10);
  private static String accountId = randomAlphabetic(10);
  private static String resourceTypeDTO = "CONNECTOR";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    favoriteService = new FavoritesServiceImpl(favoriteRepository, favoritesResourceUtils, favoritesValidator);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void createFavoriteValidInputSaveFavorite() {
    FavoriteDTO favoriteDTO = new FavoriteDTO()
                                  .resourceType(FavoritesResourceType.CONNECTOR)
                                  .module(io.harness.spec.server.ng.v1.model.ModuleType.CD)
                                  .userId(userId);
    Favorite favorite =
        Favorite.builder().resourceType(ResourceType.CONNECTOR).module(ModuleType.CD).userIdentifier(userId).build();
    when(favoritesResourceUtils.toFavoriteEntity(favoriteDTO, accountId)).thenReturn(favorite);
    when(favoriteRepository.save(favorite)).thenReturn(favorite);
    Favorite createdFavorite = favoriteService.createFavorite(favoriteDTO, accountId);
    assertThat(createdFavorite.getResourceIdentifier()).isEqualTo(favoriteDTO.getResourceId());
    assertThat(createdFavorite.getResourceType().toString()).isEqualTo(favoriteDTO.getResourceType().toString());
    assertThat(createdFavorite.getUserIdentifier()).isEqualTo(favoriteDTO.getUserId());
    assertThat(createdFavorite.getModule().toString()).isEqualTo(favoriteDTO.getModule().toString());
    verify(favoritesValidator).validateFavoriteEntry(favoriteDTO, accountId);
    verify(favoritesResourceUtils).toFavoriteEntity(favoriteDTO, accountId);
    verify(favoriteRepository).save(favorite);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void createFavoriteDuplicateKeyExceptionReturnFavorite() {
    FavoriteDTO favoriteDTO = new FavoriteDTO();
    Favorite favorite = Favorite.builder().build();
    when(favoritesResourceUtils.toFavoriteEntity(favoriteDTO, accountId)).thenReturn(favorite);
    doThrow(DuplicateKeyException.class).when(favoriteRepository).save(favorite);
    favoriteService.createFavorite(favoriteDTO, accountId);
    verify(favoritesValidator).validateFavoriteEntry(favoriteDTO, accountId);
    verify(favoritesResourceUtils).toFavoriteEntity(favoriteDTO, accountId);
    verify(favoriteRepository).save(favorite);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getFavoritesWithResourceTypeFoundFavoritesReturnFavoritesList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    ResourceType resourceType = ResourceType.CONNECTOR;
    List<Favorite> expectedFavorites = new ArrayList<>();
    expectedFavorites.add(Favorite.builder().build());
    expectedFavorites.add(Favorite.builder().build());
    when(favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
             accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType))
        .thenReturn(expectedFavorites);
    List<Favorite> actualFavorites =
        favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeDTO);
    assertThat(expectedFavorites).isEqualTo(actualFavorites);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getFavoritesWithInvalidResourceTypeFoundFavoritesReturnFavoritesList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId, null);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getFavoritesWithResourceTypeNoFavoritesReturnEmptyList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    ResourceType resourceType = ResourceType.CONNECTOR;
    when(favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
             accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType))
        .thenReturn(new ArrayList<>());
    List<Favorite> favorites =
        favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeDTO);
    assertThat(favorites.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getUserFavoritesSuccess() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    List<Favorite> expectedFavorites = new ArrayList<>();
    expectedFavorites.add(Favorite.builder().build());
    expectedFavorites.add(Favorite.builder().build());
    when(favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, userId))
        .thenReturn(expectedFavorites);
    List<Favorite> actualFavorites =
        favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId);
    assertThat(expectedFavorites).isEqualTo(actualFavorites);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isFavoritesSuccess() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    ResourceType resourceType = ResourceType.CONNECTOR;
    String favoriteConnectorId = randomAlphabetic(10);
    Favorite favorite = Favorite.builder()
                            .userIdentifier(userId)
                            .resourceType(resourceType)
                            .resourceIdentifier(favoriteConnectorId)
                            .build();
    when(favoriteRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceId(
                 accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType, favoriteConnectorId))
        .thenReturn(Optional.of(favorite));
    boolean isFavorite = favoriteService.isFavorite(
        accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType.toString(), favoriteConnectorId);
    assertThat(isFavorite).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isFavoritesFailure() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    ResourceType resourceType = ResourceType.CONNECTOR;
    String favoriteConnectorId = randomAlphabetic(10);
    when(favoriteRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceId(
                 accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType, favoriteConnectorId))
        .thenReturn(Optional.empty());
    boolean isFavorite = favoriteService.isFavorite(
        accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType.toString(), favoriteConnectorId);
    assertThat(isFavorite).isFalse();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getFavoritesNoFavoritesReturnEmptyList() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    when(favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, userId))
        .thenReturn(new ArrayList<>());
    List<Favorite> actualFavorites =
        favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId);
    assertThat(actualFavorites.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void deleteFavoriteInvokeRepositoryDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    String resourceId = randomAlphabetic(10);
    favoriteService.deleteFavorite(
        accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeDTO, resourceId);
    verify(favoriteRepository)
        .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, ResourceType.CONNECTOR, resourceId);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void deleteFavoriteInvokeRepositoryDeleteInvalidRequest() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    String resourceId = randomAlphabetic(10);
    favoriteService.deleteFavorite(accountIdentifier, orgIdentifier, projectIdentifier, userId, null, resourceId);
  }
}
