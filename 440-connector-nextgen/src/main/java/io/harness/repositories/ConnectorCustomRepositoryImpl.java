/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.connector.entities.Connector.CONNECTOR_COLLECTION_NAME;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;
import io.harness.ng.core.utils.NGYamlUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@HarnessRepo
@GitSyncableHarnessRepo
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class ConnectorCustomRepositoryImpl implements ConnectorCustomRepository {
  private MongoTemplate mongoTemplate;
  private GitAwarePersistence gitAwarePersistence;

  // todo(abhinav): This method is not yet migrated because of find By fqn
  @Override
  public Page<Connector> findAll(Criteria criteria, Pageable pageable, boolean getDistinctIdentifiers) {
    pageable = getPageableWithSortFieldAsTimeWhenConnectorIsLastUpdated(pageable);
    Query query = new Query(criteria).with(pageable);
    if (!getDistinctIdentifiers) {
      List<Connector> connectors = mongoTemplate.find(query, Connector.class);
      return PageableExecutionUtils.getPage(
          connectors, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Connector.class));
    } else {
      return EntityDistinctElementHelper.getDistinctElementPage(
          mongoTemplate, criteria, pageable, ConnectorKeys.fullyQualifiedIdentifier, Connector.class);
    }
  }

  @Override
  public Page<Connector> findAll(
      Criteria criteria, Pageable pageable, String projectIdentifier, String orgIdentifier, String accountIdentifier) {
    pageable = getPageableWithSortFieldAsTimeWhenConnectorIsLastUpdated(pageable);
    List<Connector> connectors = gitAwarePersistence.find(
        criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier, Connector.class, false);
    return PageableExecutionUtils.getPage(connectors, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, Connector.class));
  }

  @Override
  public Page<Connector> findAll(Criteria criteria, Pageable pageable) {
    pageable = getPageableWithSortFieldAsTimeWhenConnectorIsLastUpdated(pageable);
    Query query = new Query(criteria).with(pageable);
    List<Connector> connectors = mongoTemplate.find(query, Connector.class);
    return PageableExecutionUtils.getPage(
        connectors, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Connector.class));
  }

  @Override
  public Connector update(Criteria criteria, Update update, ChangeType changeType, String projectIdentifier,
      String orgIdentifier, String accountIdentifier) {
    return mongoTemplate.findAndModify(
        query(criteria), update, FindAndModifyOptions.options().returnNew(true), Connector.class);
  }

  @Override
  public Connector update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    criteria = gitAwarePersistence.makeCriteriaGitAware(
        accountIdentifier, orgIdentifier, projectIdentifier, Connector.class, criteria);

    return mongoTemplate.findAndModify(
        query(criteria), update, FindAndModifyOptions.options().returnNew(true), Connector.class);
  }

  @Override
  public UpdateResult updateMultiple(Query query, Update update) {
    return mongoTemplate.updateMulti(query, update, Connector.class);
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, CONNECTOR_COLLECTION_NAME, classToFillResultIn);
  }

  @Override
  public Optional<Connector> findByFullyQualifiedIdentifierAndDeletedNot(String fullyQualifiedIdentifier,
      String projectIdentifier, String orgIdentifier, String accountIdentifier, boolean notDeleted) {
    return gitAwarePersistence.findOne(Criteria.where(ConnectorKeys.fullyQualifiedIdentifier)
                                           .is(fullyQualifiedIdentifier)
                                           .and(ConnectorKeys.deleted)
                                           .is(!notDeleted),
        projectIdentifier, orgIdentifier, accountIdentifier, Connector.class);
  }

  @Override
  public boolean existsByFullyQualifiedIdentifier(
      String fullyQualifiedIdentifier, String projectIdentifier, String orgIdentifier, String accountId) {
    return gitAwarePersistence.exists(
        Criteria.where(ConnectorKeys.fullyQualifiedIdentifier).is(fullyQualifiedIdentifier), projectIdentifier,
        orgIdentifier, accountId, Connector.class);
  }

  @Override
  public Connector save(Connector objectToSave, ConnectorDTO yaml) {
    return gitAwarePersistence.save(objectToSave, NGYamlUtils.getYamlString(yaml), ChangeType.ADD, Connector.class);
  }

  @Override
  public Connector save(Connector objectToSave, ChangeType changeType) {
    return gitAwarePersistence.save(objectToSave, changeType, Connector.class);
  }

  @Override
  public Connector save(Connector objectToSave, ConnectorDTO connectorDTO, ChangeType changeType) {
    return gitAwarePersistence.save(objectToSave, NGYamlUtils.getYamlString(connectorDTO), changeType, Connector.class);
  }

  @Override
  public Connector save(Connector objectToSave, ConnectorDTO connectorDTO, ChangeType changeType, Supplier functor) {
    return gitAwarePersistence.save(
        objectToSave, NGYamlUtils.getYamlString(connectorDTO), changeType, Connector.class, functor);
  }

  @Override
  public void delete(Connector objectToRemove, ConnectorDTO connectorDTO, ChangeType changeType, Supplier functor) {
    gitAwarePersistence.delete(
        objectToRemove, NGYamlUtils.getYamlString(connectorDTO), changeType, Connector.class, functor);
  }

  @Override
  public void delete(Connector objectToRemove, ChangeType changeType) {
    gitAwarePersistence.delete(objectToRemove, changeType, Connector.class);
  }

  @Override
  public Optional<Connector> findOne(Criteria criteria, String repo, String branch) {
    return gitAwarePersistence.findOne(criteria, repo, branch, Connector.class);
  }

  @Override
  public long count(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), Connector.class);
  }

  @VisibleForTesting
  Pageable getPageableWithSortFieldAsTimeWhenConnectorIsLastUpdated(Pageable pageable) {
    Order lastModifiedAtSortOrder = pageable.getSort().getOrderFor(ConnectorKeys.lastModifiedAt);
    if (Objects.nonNull(lastModifiedAtSortOrder)) {
      List<Order> orders = new ArrayList<>();
      for (Order order : pageable.getSort()) {
        if (lastModifiedAtSortOrder.equals(order)) {
          orders.add(new Order(lastModifiedAtSortOrder.getDirection(), ConnectorKeys.timeWhenConnectorIsLastUpdated));
        } else {
          orders.add(order);
        }
      }
      return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
    return pageable;
  }
}
