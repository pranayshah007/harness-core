/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping.BusinessMappingKeys;
import io.harness.ccm.views.businessMapping.entities.CostCategorySortType;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Collation;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class BusinessMappingDao {
  private static final String INSENSITIVE_SEARCH = "i";
  private static final String LOCALE_EN = "en";
  private static final String OPTIONS = "$options";
  private static final String REGEX = "$regex";

  @Inject private HPersistence hPersistence;

  public BusinessMapping save(BusinessMapping businessMapping) {
    hPersistence.save(businessMapping);
    return businessMapping;
  }

  public BusinessMapping update(BusinessMapping businessMapping) {
    Query<BusinessMapping> query = hPersistence.createQuery(BusinessMapping.class)
                                       .field(BusinessMappingKeys.accountId)
                                       .equal(businessMapping.getAccountId())
                                       .field(BusinessMappingKeys.uuid)
                                       .equal(businessMapping.getUuid());

    hPersistence.update(query, getUpdateOperations(businessMapping));
    return businessMapping;
  }

  private UpdateOperations<BusinessMapping> getUpdateOperations(BusinessMapping businessMapping) {
    UpdateOperations<BusinessMapping> updateOperations = hPersistence.createUpdateOperations(BusinessMapping.class);

    setUnsetUpdateOperations(updateOperations, BusinessMappingKeys.name, businessMapping.getName());
    setUnsetUpdateOperations(updateOperations, BusinessMappingKeys.costTargets, businessMapping.getCostTargets());
    setUnsetUpdateOperations(updateOperations, BusinessMappingKeys.sharedCosts, businessMapping.getSharedCosts());
    setUnsetUpdateOperations(
        updateOperations, BusinessMappingKeys.unallocatedCost, businessMapping.getUnallocatedCost());
    setUnsetUpdateOperations(updateOperations, BusinessMappingKeys.dataSources, businessMapping.getDataSources());

    return updateOperations;
  }

  private void setUnsetUpdateOperations(UpdateOperations<BusinessMapping> updateOperations, String key, Object value) {
    if (Objects.nonNull(value)) {
      updateOperations.set(key, value);
    } else {
      updateOperations.unset(key);
    }
  }

  public boolean delete(String uuid, String accountId) {
    Query<BusinessMapping> query = hPersistence.createQuery(BusinessMapping.class)
                                       .field(BusinessMappingKeys.accountId)
                                       .equal(accountId)
                                       .field(BusinessMappingKeys.uuid)
                                       .equal(uuid);

    return hPersistence.delete(query);
  }

  public BusinessMapping get(String uuid, String accountId) {
    return hPersistence.createQuery(BusinessMapping.class, excludeValidate)
        .filter(BusinessMappingKeys.uuid, uuid)
        .filter(BusinessMappingKeys.accountId, accountId)
        .get();
  }

  public boolean isNamePresent(String name, String accountId) {
    return hPersistence.createQuery(BusinessMapping.class, excludeValidate)
               .filter(BusinessMappingKeys.name, name)
               .filter(BusinessMappingKeys.accountId, accountId)
               .count()
        > 0;
  }

  public BusinessMapping get(String uuid) {
    return hPersistence.createQuery(BusinessMapping.class, excludeValidate)
        .filter(BusinessMappingKeys.uuid, uuid)
        .get();
  }

  public List<BusinessMapping> findByAccountId(String accountId) {
    return hPersistence.createQuery(BusinessMapping.class).filter(BusinessMappingKeys.accountId, accountId).asList();
  }

  private Query<BusinessMapping> getQueryByAccountIdAndRegexName(String accountId, String searchKey) {
    Query<BusinessMapping> query =
        hPersistence.createQuery(BusinessMapping.class).filter(BusinessMappingKeys.accountId, accountId);

    if (!isEmpty(searchKey)) {
      BasicDBObject basicDBObject = new BasicDBObject(REGEX, searchKey);
      basicDBObject.put(OPTIONS, INSENSITIVE_SEARCH);
      query.filter(BusinessMappingKeys.name, basicDBObject);
    }

    return query;
  }

  public List<BusinessMapping> findByAccountIdAndRegexNameWithLimitAndOffsetAndOrder(String accountId, String searchKey,
      CostCategorySortType sortType, CCMSortOrder sortOrder, int limit, int offset) {
    final FindOptions options = new FindOptions();
    options.collation(Collation.builder().locale(LOCALE_EN).build());
    options.limit(limit);
    options.skip(offset);
    final Sort sort = sortOrder == CCMSortOrder.ASCENDING ? Sort.ascending(sortType.getColumnName())
                                                          : Sort.descending(sortType.getColumnName());
    return getQueryByAccountIdAndRegexName(accountId, searchKey).order(sort).asList(options);
  }

  public long getCountByAccountIdAndRegexName(String accountId, String searchKey) {
    return getQueryByAccountIdAndRegexName(accountId, searchKey).count();
  }
}
