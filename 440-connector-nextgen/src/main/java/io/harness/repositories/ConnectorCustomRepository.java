/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.entities.Connector;
import io.harness.git.model.ChangeType;

import com.mongodb.client.result.UpdateResult;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

//@NoRepositoryBean
@OwnedBy(DX)
public interface ConnectorCustomRepository {
  // todo(abhinav): This method is not yet migrated because of find By fqn
  Page<Connector> findAll(Criteria criteria, Pageable pageable, boolean getDistinctIdentifiers);

  Page<Connector> findAll(
      Criteria criteria, Pageable pageable, String projectIdentifier, String orgIdentifier, String accountIdentifier);

  Page<Connector> findAll(Criteria criteria, Pageable pageable);

  Connector update(Criteria criteria, Update update, ChangeType changeType, String projectIdentifier,
      String orgIdentifier, String accountIdentifier);

  UpdateResult updateMultiple(Query query, Update update);

  <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn);

  Optional<Connector> findByFullyQualifiedIdentifierAndDeletedNot(String fullyQualifiedIdentifier,
      String projectIdentifier, String orgIdentifier, String accountIdentifier, boolean notDeleted);

  boolean existsByFullyQualifiedIdentifier(
      String fullyQualifiedIdentifier, String projectIdentifier, String orgIdentifier, String accountId);

  Connector save(Connector objectToSave, ConnectorDTO yaml);

  Connector save(Connector objectToSave, ChangeType changeType);

  Connector save(Connector objectToSave, ConnectorDTO connectorDTO, ChangeType changeType);

  Connector save(Connector objectToSave, ConnectorDTO connectorDTO, ChangeType changeType, Supplier functor);

  void delete(Connector objectToRemove, ConnectorDTO connectorDTO, ChangeType changeType, Supplier functor);

  void delete(Connector objectToRemove, ChangeType changeType);

  Optional<Connector> findOne(Criteria criteria, String repo, String branch);

  long count(Criteria criteria);

  Connector update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update);
}
