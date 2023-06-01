/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.service.impl;

import static io.harness.rule.OwnerRule.BOOPESH;

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

import java.util.ArrayList;
import java.util.List;
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
  private static String userId = "userId";
  private static String accountId = "accountId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    favoriteService = new FavoritesServiceImpl(favoriteRepository, favoritesResourceUtils, favoritesValidator);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void createFavoriteValidInputSaveFavorite() {
    FavoriteDTO favoriteDTO =
        new FavoriteDTO().resourceType(ResourceType.CONNECTOR.toString()).module(ModuleType.CD.name()).userId(userId);
    Favorite favorite =
        Favorite.builder().resourceType(ResourceType.CONNECTOR).module(ModuleType.CD).userIdentifier(userId).build();
    when(favoritesResourceUtils.toFavoriteEntity(favoriteDTO, accountId)).thenReturn(favorite);
    when(favoriteRepository.save(favorite)).thenReturn(favorite);
    Favorite createdFavorite = favoriteService.createFavorite(favoriteDTO, accountId);
    assertThat(createdFavorite.getResourceIdentifier()).isEqualTo(favoriteDTO.getResourceId());
    assertThat(createdFavorite.getResourceType().toString()).isEqualTo(favoriteDTO.getResourceType());
    assertThat(createdFavorite.getUserIdentifier()).isEqualTo(favoriteDTO.getUserId());
    assertThat(createdFavorite.getModule().toString()).isEqualTo(favoriteDTO.getModule());
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
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "project123";
    String userId = "user123";
    ResourceType resourceType = ResourceType.CONNECTOR;
    List<Favorite> expectedFavorites = new ArrayList<>();
    expectedFavorites.add(Favorite.builder().build());
    expectedFavorites.add(Favorite.builder().build());
    when(favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
             accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType))
        .thenReturn(expectedFavorites);
    List<Favorite> actualFavorites =
        favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType);
    assertThat(expectedFavorites).isEqualTo(actualFavorites);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getFavoritesWithResourceTypeNoFavoritesReturnEmptyList() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "project123";
    String userId = "user123";
    ResourceType resourceType = ResourceType.CONNECTOR;
    when(favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
             accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType))
        .thenReturn(new ArrayList<>());
    List<Favorite> favorites =
        favoriteService.getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType);
    assertThat(favorites.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getUserFavoritesSuccess() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "project123";
    String userId = "user123";
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
  public void getFavoritesNoFavoritesReturnEmptyList() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "project123";
    String userId = "user123";
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
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "project123";
    String userId = "user123";
    String resourceId = "resource123";
    String resourceType = "CONNECTOR";
    favoriteService.deleteFavorite(
        accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType, resourceId);
    verify(favoriteRepository)
        .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, ResourceType.CONNECTOR, resourceId);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void deleteFavoriteInvokeRepositoryDeleteInvalidRequest() {
    String accountIdentifier = "account123";
    String orgIdentifier = "org123";
    String projectIdentifier = "project123";
    String userId = "user123";
    String resourceId = "resource123";
    String resourceType = "invalid";
    favoriteService.deleteFavorite(
        accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType, resourceId);
  }
}
