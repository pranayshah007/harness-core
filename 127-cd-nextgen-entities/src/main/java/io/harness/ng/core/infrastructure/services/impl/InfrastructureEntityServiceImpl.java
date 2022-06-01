/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.repositories.infrastructure.spring.InfrastructureRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InfrastructureEntityServiceImpl implements InfrastructureEntityService {
  private final InfrastructureRepository infrastructureRepository;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_TRANSACTION_RETRY_POLICY;

  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Infrastructure [%s] under Environment [%s] Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Infrastructure [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT =
      "Infrastructure [%s] in Account [%s] already exists";

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  @Override
  public InfrastructureEntity create(@NotNull @Valid InfrastructureEntity infraEntity) {
    try {
      validatePresenceOfRequiredFields(
          infraEntity.getAccountId(), infraEntity.getIdentifier(), infraEntity.getEnvIdentifier());
      setNameIfNotPresent(infraEntity);
      modifyInfraRequest(infraEntity);
      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> infrastructureRepository.save(infraEntity)));

    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateInfrastructureExistsErrorMessage(infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
              infraEntity.getProjectIdentifier(), infraEntity.getEnvIdentifier(), infraEntity.getIdentifier()),
          USER, ex);
    }
  }

  @Override
  public Optional<InfrastructureEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String infraIdentifier) {
    return infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
  }

  @Override
  public InfrastructureEntity update(@Valid InfrastructureEntity requestInfra) {
    validatePresenceOfRequiredFields(requestInfra.getAccountId(), requestInfra.getIdentifier());
    setNameIfNotPresent(requestInfra);
    modifyInfraRequest(requestInfra);
    Criteria criteria = getInfrastructureEqualityCriteria(requestInfra);
    Optional<InfrastructureEntity> infraEntityOptional =
        get(requestInfra.getAccountId(), requestInfra.getOrgIdentifier(), requestInfra.getProjectIdentifier(),
            requestInfra.getEnvIdentifier(), requestInfra.getIdentifier());
    if (infraEntityOptional.isPresent()) {
      InfrastructureEntity oldInfra = infraEntityOptional.get();
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        InfrastructureEntity updatedResult = infrastructureRepository.update(criteria, requestInfra);
        if (updatedResult == null) {
          throw new InvalidRequestException(String.format(
              "Infrastructure [%s] under Environment [%s], Project [%s], Organization [%s] couldn't be updated or doesn't exist.",
              requestInfra.getIdentifier(), requestInfra.getEnvIdentifier(), requestInfra.getProjectIdentifier(),
              requestInfra.getOrgIdentifier()));
        }

        return updatedResult;
      }));
    } else {
      throw new InvalidRequestException(
          String.format("Infrastructure [%s] under Environment [%s], Project [%s], Organization [%s] doesn't exist.",
              requestInfra.getIdentifier(), requestInfra.getEnvIdentifier(), requestInfra.getProjectIdentifier(),
              requestInfra.getOrgIdentifier()));
    }
  }

  @Override
  public InfrastructureEntity upsert(@Valid InfrastructureEntity requestInfra) {
    validatePresenceOfRequiredFields(requestInfra.getAccountId(), requestInfra.getIdentifier());
    setNameIfNotPresent(requestInfra);
    modifyInfraRequest(requestInfra);
    Criteria criteria = getInfrastructureEqualityCriteria(requestInfra);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      InfrastructureEntity result = infrastructureRepository.upsert(criteria, requestInfra);
      if (result == null) {
        throw new InvalidRequestException(String.format(
            "Infrastructure [%s] under Environment [%s] Project[%s], Organization [%s] couldn't be upserted.",
            requestInfra.getIdentifier(), requestInfra.getEnvIdentifier(), requestInfra.getProjectIdentifier(),
            requestInfra.getOrgIdentifier()));
      }
      return result;
    }));
  }

  @Override
  public Page<InfrastructureEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable) {
    return infrastructureRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String infraIdentifier) {
    InfrastructureEntity infraEntity = InfrastructureEntity.builder()
                                           .accountId(accountId)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .envIdentifier(envIdentifier)
                                           .identifier(infraIdentifier)
                                           .build();
    // todo: check for infra usage in pipelines
    // todo: outbox events
    Criteria criteria = getInfrastructureEqualityCriteria(infraEntity);
    Optional<InfrastructureEntity> infraEntityOptional =
        get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    if (infraEntityOptional.isPresent()) {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        DeleteResult deleteResult = infrastructureRepository.delete(criteria);
        if (!deleteResult.wasAcknowledged() || deleteResult.getDeletedCount() != 1) {
          throw new InvalidRequestException(String.format(
              "Infrastructure [%s] under Environment [%s], Project[%s], Organization [%s] couldn't be deleted.",
              infraIdentifier, envIdentifier, projectIdentifier, orgIdentifier));
        }
        return true;
      }));
    } else {
      throw new InvalidRequestException(
          String.format("Infrastructure [%s] under Environment [%s], Project[%s], Organization [%s] doesn't exist.",
              infraIdentifier, envIdentifier, projectIdentifier, orgIdentifier));
    }
  }

  private void setNameIfNotPresent(InfrastructureEntity requestInfra) {
    if (isEmpty(requestInfra.getName())) {
      requestInfra.setName(requestInfra.getIdentifier());
    }
  }

  private Criteria getInfrastructureEqualityCriteria(@Valid InfrastructureEntity requestInfra) {
    return Criteria.where(InfrastructureEntityKeys.accountId)
        .is(requestInfra.getAccountId())
        .and(InfrastructureEntityKeys.orgIdentifier)
        .is(requestInfra.getOrgIdentifier())
        .and(InfrastructureEntityKeys.projectIdentifier)
        .is(requestInfra.getProjectIdentifier())
        .and(InfrastructureEntityKeys.envIdentifier)
        .is(requestInfra.getEnvIdentifier())
        .and(InfrastructureEntityKeys.identifier)
        .is(requestInfra.getIdentifier());
  }

  @Override
  public Page<InfrastructureEntity> bulkCreate(String accountId, @NotNull List<InfrastructureEntity> infraEntities) {
    try {
      validateInfraList(infraEntities);
      populateDefaultNameIfNotPresent(infraEntities);
      modifyInfraRequestBatch(infraEntities);
      List<InfrastructureEntity> outputInfrastructureEntitiesList =
          (List<InfrastructureEntity>) infrastructureRepository.saveAll(infraEntities);
      return new PageImpl<>(outputInfrastructureEntitiesList);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateInfrastructureExistsErrorMessage(accountId, ex.getMessage()), USER, ex);
    } catch (Exception ex) {
      String infraNames = infraEntities.stream().map(InfrastructureEntity::getName).collect(Collectors.joining(","));
      log.info("Encountered exception while saving the infrastructure entity records of [{}], with exception",
          infraNames, ex);
      throw new UnexpectedException("Encountered exception while saving the infrastructure entity records.");
    }
  }

  @Override
  public InfrastructureEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String infraIdentifier) {
    return infrastructureRepository.find(
        accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
  }

  @Override
  public List<InfrastructureEntity> getAllInfrastructureFromIdentifierList(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String envIdentifier, List<String> infraIdentifierList) {
    return infrastructureRepository.findAllFromInfraIdentifierList(
        accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifierList);
  }

  @Override
  public List<InfrastructureEntity> getAllInfrastructureFromEnvIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    return infrastructureRepository.findAllFromEnvIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier);
  }

  String getDuplicateInfrastructureExistsErrorMessage(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String infraIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT, infraIdentifier, accountIdentifier);
    } else if (EmptyPredicate.isEmpty(projectIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ORG, infraIdentifier, orgIdentifier, accountIdentifier);
    }
    return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT, infraIdentifier, envIdentifier, projectIdentifier,
        orgIdentifier, accountIdentifier);
  }

  @VisibleForTesting
  String getDuplicateInfrastructureExistsErrorMessage(String accountId, String exceptionString) {
    String errorMessageToBeReturned;
    try {
      JSONObject jsonObjectOfDuplicateKey = DuplicateKeyExceptionParser.getDuplicateKey(exceptionString);
      if (jsonObjectOfDuplicateKey != null) {
        String orgIdentifier = jsonObjectOfDuplicateKey.getString("orgIdentifier");
        String projectIdentifier = jsonObjectOfDuplicateKey.getString("projectIdentifier");
        String envIdentifier = jsonObjectOfDuplicateKey.getString("envIdentifier");
        String identifier = jsonObjectOfDuplicateKey.getString("identifier");
        errorMessageToBeReturned = getDuplicateInfrastructureExistsErrorMessage(
            accountId, orgIdentifier, projectIdentifier, envIdentifier, identifier);
      } else {
        errorMessageToBeReturned = "A Duplicate Infrastructure already exists";
      }
    } catch (Exception ex) {
      errorMessageToBeReturned = "A Duplicate Infrastructure already exists";
    }
    return errorMessageToBeReturned;
  }

  private void validateInfraList(List<InfrastructureEntity> infraEntities) {
    if (isEmpty(infraEntities)) {
      return;
    }
    infraEntities.forEach(
        infraEntity -> validatePresenceOfRequiredFields(infraEntity.getAccountId(), infraEntity.getIdentifier()));
  }

  private void populateDefaultNameIfNotPresent(List<InfrastructureEntity> infraEntities) {
    if (isEmpty(infraEntities)) {
      return;
    }
    infraEntities.forEach(this::setNameIfNotPresent);
  }

  private void modifyInfraRequest(InfrastructureEntity requestInfra) {
    requestInfra.setName(requestInfra.getName().trim());
  }

  private void modifyInfraRequestBatch(List<InfrastructureEntity> infrastructureEntityList) {
    if (isEmpty(infrastructureEntityList)) {
      return;
    }
    infrastructureEntityList.forEach(this::modifyInfraRequest);
  }
}
