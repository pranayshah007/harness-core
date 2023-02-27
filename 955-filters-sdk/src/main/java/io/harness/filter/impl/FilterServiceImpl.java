/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filter.dto.FilterVisibility.EVERYONE;
import static io.harness.filter.dto.FilterVisibility.ONLY_CREATOR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.dto.FilterVisibility;
import io.harness.filter.entity.Filter;
import io.harness.filter.entity.Filter.FilterKeys;
import io.harness.filter.mapper.FilterMapper;
import io.harness.filter.service.FilterService;
import io.harness.repositories.FilterRepository;
import io.harness.scope.ScopeHelper;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class FilterServiceImpl implements FilterService {
  private FilterRepository filterRepository;
  private FilterMapper filterMapper;

  @Override
  public FilterDTO create(String accountId, FilterDTO filterDTO) {
    Filter filterEntity = filterMapper.toEntity(filterDTO, accountId);
    try {
      return filterMapper.writeDTO(filterRepository.save(filterEntity));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(format("A filter already exists with name %s in %s",
          filterEntity.getIdentifier(), getFilterScopeMessage(accountId, filterDTO)));
    }
  }

  @Override
  public FilterDTO update(String accountId, FilterDTO filterDTO) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountId, filterDTO.getOrgIdentifier(), filterDTO.getProjectIdentifier(), filterDTO.getIdentifier());
    Optional<Filter> existingFilter =
        filterRepository.findByFullyQualifiedIdentifierAndFilterType(fullyQualifiedIdentifier, getType(filterDTO));
    if (!existingFilter.isPresent() || !userHaveAccessToFilter(existingFilter.get(), accountId)) {
      throw new InvalidRequestException(format("No filter exists with the Identifier %s in %s",
          filterDTO.getIdentifier(), getFilterScopeMessage(accountId, filterDTO)));
    }
    validateTheUpdateRequestIsValid(existingFilter.get(), filterDTO, accountId);
    Filter filterEntity = filterMapper.toEntity(filterDTO, accountId);
    filterEntity.setId(existingFilter.get().getId());
    filterEntity.setVersion(existingFilter.get().getVersion());
    Filter updatedFilter = filterRepository.save(filterEntity);
    return filterMapper.writeDTO(updatedFilter);
  }

  private void validateTheUpdateRequestIsValid(Filter existingFilter, FilterDTO newFilter, String accountIdentifier) {
    validateTheNameIsNotChanged(accountIdentifier, existingFilter, newFilter);
    validateTheTypeIsNotChanged(existingFilter, newFilter);
  }

  private void validateTheTypeIsNotChanged(Filter existingFilter, FilterDTO newFilter) {
    if (existingFilter.getFilterType() != newFilter.getFilterProperties().getFilterType()) {
      throw new InvalidRequestException("The filter type cannot be updated");
    }
  }

  private void validateTheNameIsNotChanged(String accountIdentifier, Filter existingFilter, FilterDTO newFilter) {
    Optional<Filter> filterWithGivenName =
        filterRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndName(
            accountIdentifier, newFilter.getOrgIdentifier(), newFilter.getProjectIdentifier(), newFilter.getName());
    if (filterWithGivenName.isPresent()
        && !filterWithGivenName.get().getIdentifier().equals(existingFilter.getIdentifier())) {
      throw new InvalidRequestException("A filter already exists with the name " + newFilter.getName());
    }
  }

  private FilterType getType(FilterDTO filterDTO) {
    if (filterDTO == null || filterDTO.getFilterProperties() == null) {
      throw new InvalidRequestException("The filter properties cannot be null");
    }
    return filterDTO.getFilterProperties().getFilterType();
  }

  private boolean userHaveAccessToFilter(Filter filter, String accountId) {
    String userId = getUserId();
    if (filter.getFilterVisibility() == FilterVisibility.ONLY_CREATOR) {
      if (userId != null) {
        if (!userId.equals(filter.getCreatedBy() == null ? "" : filter.getCreatedBy().getUuid())) {
          return false;
        }
      }
    }
    return true;
  }

  private String getUserId() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      UserPrincipal userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
      return userPrincipal.getName();
    }
    return null;
  }

  private Object getFilterScopeMessage(String accountId, FilterDTO filterDTO) {
    return ScopeHelper.getScopeMessageForLogs(
        accountId, filterDTO.getOrgIdentifier(), filterDTO.getProjectIdentifier());
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier, FilterType type) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountId, orgIdentifier, projectIdentifier, identifier);
    Optional<Filter> filter =
        filterRepository.findByFullyQualifiedIdentifierAndFilterType(fullyQualifiedIdentifier, type);
    if (!filter.isPresent() || !userHaveAccessToFilter(filter.get(), accountId)) {
      throwNoFilterExistsException(orgIdentifier, projectIdentifier, identifier);
    }
    long deletedCount = filterRepository.deleteByFullyQualifiedIdentifierAndFilterType(fullyQualifiedIdentifier, type);
    if (deletedCount == 1) {
      return true;
    }
    return false;
  }

  @Override
  public void deleteByScope(Scope scope) {
    Criteria criteria =
        createScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    filterRepository.deleteAll(criteria);
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(FilterKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(FilterKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(FilterKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }

  private void throwNoFilterExistsException(String orgIdentifier, String projectIdentifier, String identifier) {
    throw new NotFoundException(format(
        "No Filter exists with the identifier %s in org %s, project %s", identifier, orgIdentifier, projectIdentifier));
  }

  @Override
  public FilterDTO get(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier, FilterType type) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountId, orgIdentifier, projectIdentifier, identifier);
    Optional<Filter> filter =
        filterRepository.findByFullyQualifiedIdentifierAndFilterType(fullyQualifiedIdentifier, type);
    if (!filter.isPresent() || !userHaveAccessToFilter(filter.get(), accountId)) {
      throwNoFilterExistsException(orgIdentifier, projectIdentifier, identifier);
    }
    return filterMapper.writeDTO(filter.get());
  }

  @Override
  public Page<FilterDTO> list(int page, int size, String accountId, String orgIdentifier, String projectIdentifier,
      List<String> filterIds, FilterType type) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(filterIds)) {
      criteria.and(FilterKeys.identifier).in(filterIds);
    }
    criteria.and(FilterKeys.accountIdentifier).in(accountId);
    criteria.and(FilterKeys.orgIdentifier).in(orgIdentifier);
    criteria.and(FilterKeys.projectIdentifier).in(projectIdentifier);
    criteria.and(FilterKeys.filterType).in(type);
    Criteria orOperator = new Criteria().orOperator(Criteria.where(FilterKeys.filterVisibility).is(EVERYONE),
        Criteria.where(FilterKeys.filterVisibility).is(ONLY_CREATOR).and(FilterKeys.userId).is(getUserId()));
    criteria.andOperator(orOperator);
    Order sortOrder = Sort.Order.asc(FilterKeys.name);
    Pageable pageRequest = PageRequest.of(page, size, Sort.by(sortOrder));
    return filterRepository.findAll(criteria, pageRequest).map(filterMapper::writeDTO);
  }
}
