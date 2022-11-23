/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.IDENTIFIER;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.customdeployment.helper.CustomDeploymentEntitySetupHelper;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureInputsMergedResponseDto;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadata;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.services.impl.InputSetMergeUtility;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.infrastructure.spring.InfrastructureRepository;
import io.harness.setupusage.InfrastructureEntitySetupUsageHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InfrastructureEntityServiceImpl implements InfrastructureEntityService {
  private final InfrastructureRepository infrastructureRepository;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final OutboxService outboxService;
  @Inject CustomDeploymentEntitySetupHelper customDeploymentEntitySetupHelper;
  @Inject private InfrastructureEntitySetupUsageHelper infrastructureEntitySetupUsageHelper;

  @Inject private HPersistence hPersistence;

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
      setObsoleteAsFalse(infraEntity);
      validatePresenceOfRequiredFields(
          infraEntity.getAccountId(), infraEntity.getIdentifier(), infraEntity.getEnvIdentifier());
      setNameIfNotPresent(infraEntity);
      modifyInfraRequest(infraEntity);
      InfrastructureEntity createdInfra =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            InfrastructureEntity infrastructureEntity = infrastructureRepository.save(infraEntity);
            outboxService.save(EnvironmentUpdatedEvent.builder()
                                   .accountIdentifier(infraEntity.getAccountId())
                                   .orgIdentifier(infraEntity.getOrgIdentifier())
                                   .status(EnvironmentUpdatedEvent.Status.CREATED)
                                   .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
                                   .projectIdentifier(infraEntity.getProjectIdentifier())
                                   .newInfrastructureEntity(infraEntity)
                                   .build());
            return infrastructureEntity;
          }));
      infrastructureEntitySetupUsageHelper.updateSetupUsages(createdInfra);
      if (infraEntity.getType() == InfrastructureType.CUSTOM_DEPLOYMENT) {
        customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(infraEntity);
      }
      return createdInfra;

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
    setObsoleteAsFalse(requestInfra);
    setNameIfNotPresent(requestInfra);
    modifyInfraRequest(requestInfra);
    Criteria criteria = getInfrastructureEqualityCriteria(requestInfra);
    Optional<InfrastructureEntity> infraEntityOptional =
        get(requestInfra.getAccountId(), requestInfra.getOrgIdentifier(), requestInfra.getProjectIdentifier(),
            requestInfra.getEnvIdentifier(), requestInfra.getIdentifier());
    if (infraEntityOptional.isPresent()) {
      InfrastructureEntity updatedInfra =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            InfrastructureEntity updatedResult = infrastructureRepository.update(criteria, requestInfra);
            if (updatedResult == null) {
              throw new InvalidRequestException(String.format(
                  "Infrastructure [%s] under Environment [%s], Project [%s], Organization [%s] couldn't be updated or doesn't exist.",
                  requestInfra.getIdentifier(), requestInfra.getEnvIdentifier(), requestInfra.getProjectIdentifier(),
                  requestInfra.getOrgIdentifier()));
            }
            outboxService.save(EnvironmentUpdatedEvent.builder()
                                   .accountIdentifier(requestInfra.getAccountId())
                                   .orgIdentifier(requestInfra.getOrgIdentifier())
                                   .status(EnvironmentUpdatedEvent.Status.UPDATED)
                                   .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
                                   .projectIdentifier(requestInfra.getProjectIdentifier())
                                   .newInfrastructureEntity(requestInfra)
                                   .oldInfrastructureEntity(infraEntityOptional.get())
                                   .build());
            return updatedResult;
          }));
      infrastructureEntitySetupUsageHelper.updateSetupUsages(updatedInfra);
      if (requestInfra.getType() == InfrastructureType.CUSTOM_DEPLOYMENT) {
        customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(requestInfra);
      }
      return updatedInfra;
    } else {
      throw new InvalidRequestException(
          String.format("Infrastructure [%s] under Environment [%s], Project [%s], Organization [%s] doesn't exist.",
              requestInfra.getIdentifier(), requestInfra.getEnvIdentifier(), requestInfra.getProjectIdentifier(),
              requestInfra.getOrgIdentifier()));
    }
  }

  @Override
  public InfrastructureEntity upsert(@Valid InfrastructureEntity requestInfra, UpsertOptions upsertOptions) {
    validatePresenceOfRequiredFields(requestInfra.getAccountId(), requestInfra.getIdentifier());
    setNameIfNotPresent(requestInfra);
    modifyInfraRequest(requestInfra);
    Criteria criteria = getInfrastructureEqualityCriteria(requestInfra);
    InfrastructureEntity upsertedInfra =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          InfrastructureEntity result = infrastructureRepository.upsert(criteria, requestInfra);
          if (result == null) {
            throw new InvalidRequestException(String.format(
                "Infrastructure [%s] under Environment [%s] Project[%s], Organization [%s] couldn't be upserted.",
                requestInfra.getIdentifier(), requestInfra.getEnvIdentifier(), requestInfra.getProjectIdentifier(),
                requestInfra.getOrgIdentifier()));
          }
          outboxService.save(EnvironmentUpdatedEvent.builder()
                                 .accountIdentifier(requestInfra.getAccountId())
                                 .orgIdentifier(requestInfra.getOrgIdentifier())
                                 .status(EnvironmentUpdatedEvent.Status.UPSERTED)
                                 .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
                                 .projectIdentifier(requestInfra.getProjectIdentifier())
                                 .newInfrastructureEntity(requestInfra)
                                 .build());
          return result;
        }));
    infrastructureEntitySetupUsageHelper.updateSetupUsages(upsertedInfra);
    if (requestInfra.getType() == InfrastructureType.CUSTOM_DEPLOYMENT) {
      customDeploymentEntitySetupHelper.addReferencesInEntitySetupUsage(requestInfra);
    }
    return upsertedInfra;
  }

  @Override
  public Page<InfrastructureEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable) {
    return infrastructureRepository.findAll(criteria, pageable);
  }

  @Override
  public HIterator<InfrastructureEntity> listIterator(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, Collection<String> identifiers) {
    return new HIterator<>(hPersistence.createQuery(InfrastructureEntity.class)
                               .filter(InfrastructureEntityKeys.accountId, accountId)
                               .filter(InfrastructureEntityKeys.orgIdentifier, orgIdentifier)
                               .filter(InfrastructureEntityKeys.projectIdentifier, projectIdentifier)
                               .filter(InfrastructureEntityKeys.envIdentifier, envIdentifier)
                               .field(InfrastructureEntityKeys.identifier)
                               .in(identifiers)
                               .fetch());
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
      if (infraEntityOptional.get().getType() == InfrastructureType.CUSTOM_DEPLOYMENT) {
        customDeploymentEntitySetupHelper.deleteReferencesInEntitySetupUsage(infraEntityOptional.get());
      }
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        DeleteResult deleteResult = infrastructureRepository.delete(criteria);
        if (!deleteResult.wasAcknowledged() || deleteResult.getDeletedCount() != 1) {
          throw new InvalidRequestException(String.format(
              "Infrastructure [%s] under Environment [%s], Project[%s], Organization [%s] couldn't be deleted.",
              infraIdentifier, envIdentifier, projectIdentifier, orgIdentifier));
        }

        infraEntityOptional.ifPresent(
            infrastructureEntity -> infrastructureEntitySetupUsageHelper.deleteSetupUsages(infrastructureEntity));

        outboxService.save(EnvironmentUpdatedEvent.builder()
                               .accountIdentifier(accountId)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .oldInfrastructureEntity(infraEntityOptional.get())
                               .status(EnvironmentUpdatedEvent.Status.DELETED)
                               .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
                               .build());
        return true;
      }));
    } else {
      throw new InvalidRequestException(
          String.format("Infrastructure [%s] under Environment [%s], Project[%s], Organization [%s] doesn't exist.",
              infraIdentifier, envIdentifier, projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public boolean forceDeleteAllInEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    checkArgument(isNotEmpty(accountId), "account id must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org id must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project id must be present");
    checkArgument(isNotEmpty(envIdentifier), "env id must be present");

    Criteria criteria =
        getInfrastructureEqualityCriteriaForEnv(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    List<InfrastructureEntity> infrastructureEntityListForEnvIdentifier =
        getAllInfrastructureFromEnvIdentifier(accountId, orgIdentifier, projectIdentifier, envIdentifier);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = infrastructureRepository.delete(criteria);

      if (deleteResult.wasAcknowledged()) {
        for (InfrastructureEntity infra : infrastructureEntityListForEnvIdentifier) {
          infrastructureEntitySetupUsageHelper.deleteSetupUsages(infra);
        }
      } else {
        log.error(
            String.format("Infrastructures under Environment [%s], Project[%s], Organization [%s] couldn't be deleted.",
                envIdentifier, projectIdentifier, orgIdentifier));
      }

      return deleteResult.wasAcknowledged();
    }));
  }

  @Override
  public boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "account id must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org id must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project id must be present");

    Criteria criteria = getInfrastructureEqualityCriteriaForProject(accountId, orgIdentifier, projectIdentifier);
    List<InfrastructureEntity> infrastructureEntityListForProjectIdentifier =
        getAllInfrastructureFromProjectIdentifier(accountId, orgIdentifier, projectIdentifier);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = infrastructureRepository.delete(criteria);

      if (deleteResult.wasAcknowledged()) {
        for (InfrastructureEntity infra : infrastructureEntityListForProjectIdentifier) {
          infrastructureEntitySetupUsageHelper.deleteSetupUsages(infra);
        }
      } else {
        log.error(String.format("Infrastructures under Project[%s], Organization [%s] couldn't be deleted.",
            projectIdentifier, orgIdentifier));
      }

      return deleteResult.wasAcknowledged();
    }));
  }

  private void setObsoleteAsFalse(InfrastructureEntity requestInfra) {
    requestInfra.setObsolete(false);
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
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        List<InfrastructureEntity> outputInfrastructureEntitiesList =
            (List<InfrastructureEntity>) infrastructureRepository.saveAll(infraEntities);
        for (InfrastructureEntity infraEntity : infraEntities) {
          outboxService.save(EnvironmentUpdatedEvent.builder()
                                 .accountIdentifier(infraEntity.getAccountId())
                                 .orgIdentifier(infraEntity.getOrgIdentifier())
                                 .status(EnvironmentUpdatedEvent.Status.CREATED)
                                 .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
                                 .projectIdentifier(infraEntity.getProjectIdentifier())
                                 .newInfrastructureEntity(infraEntity)
                                 .build());
        }

        return new PageImpl<>(outputInfrastructureEntitiesList);
      }));
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
  @Override
  public List<InfrastructureEntity> getAllInfrastructureFromProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return infrastructureRepository.findAllFromProjectIdentifier(accountIdentifier, orgIdentifier, projectIdentifier);
  }
  @Override
  public String createInfrastructureInputsFromYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, List<String> infraIdentifiers, boolean deployToAll,
      NoInputMergeInputAction noInputMergeInputAction) {
    Map<String, Object> yamlInputs = createInfrastructureInputsYamlInternal(accountId, orgIdentifier, projectIdentifier,
        environmentIdentifier, deployToAll, infraIdentifiers, noInputMergeInputAction);

    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlPipelineUtils.writeYamlString(yamlInputs);
  }

  @Override
  public UpdateResult batchUpdateInfrastructure(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, List<String> infraIdentifier, Update update) {
    return infrastructureRepository.batchUpdateInfrastructure(
        accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier, update);
  }

  private Map<String, Object> createInfrastructureInputsYamlInternal(String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, boolean deployToAll, List<String> infraIdentifiers,
      NoInputMergeInputAction noInputMergeInputAction) {
    Map<String, Object> yamlInputs = new HashMap<>();
    List<ObjectNode> infraDefinitionInputList = new ArrayList<>();
    // create one mapper for all infra defs
    ObjectMapper mapper = new ObjectMapper();

    List<InfrastructureEntity> infrastructureEntities;
    if (deployToAll) {
      infrastructureEntities =
          getAllInfrastructureFromEnvIdentifier(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    } else {
      infrastructureEntities = getAllInfrastructureFromIdentifierList(
          accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifiers);
    }

    for (InfrastructureEntity infraEntity : infrastructureEntities) {
      Optional<ObjectNode> infraDefinitionNodeWithInputsOptional =
          createInfraDefinitionNodeWithInputs(infraEntity, mapper);
      if (infraDefinitionNodeWithInputsOptional.isPresent()) {
        infraDefinitionInputList.add(infraDefinitionNodeWithInputsOptional.get());
      } else if (noInputMergeInputAction.equals(NoInputMergeInputAction.ADD_IDENTIFIER_NODE)) {
        ObjectNode infraNode = mapper.createObjectNode();
        infraNode.put(IDENTIFIER, infraEntity.getIdentifier());
        infraDefinitionInputList.add(infraNode);
      }
    }

    if (isNotEmpty(infraDefinitionInputList)) {
      yamlInputs.put(YamlTypes.INFRASTRUCTURE_DEFS, infraDefinitionInputList);
    }
    return yamlInputs;
  }

  /***
   *
   * @param infraEntity
   * @param mapper
   * @return Optional.of(infraNode) if runtime inputs are present, else Optional.empty() otherwise
   */
  private Optional<ObjectNode> createInfraDefinitionNodeWithInputs(
      InfrastructureEntity infraEntity, ObjectMapper mapper) {
    String yaml = infraEntity.getYaml();
    if (isEmpty(yaml)) {
      throw new InvalidRequestException(
          "Infrastructure Yaml cannot be empty for infra : " + infraEntity.getIdentifier());
    }
    ObjectNode infraNode = mapper.createObjectNode();
    try {
      String infraDefinitionInputs = RuntimeInputFormHelper.createRuntimeInputForm(yaml, true);
      if (isEmpty(infraDefinitionInputs)) {
        return Optional.empty();
      }

      infraNode.put(IDENTIFIER, infraEntity.getIdentifier());
      YamlField infrastructureDefinitionYamlField =
          YamlUtils.readTree(infraDefinitionInputs).getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
      ObjectNode infraDefinitionNode = (ObjectNode) infrastructureDefinitionYamlField.getNode().getCurrJsonNode();
      infraNode.set(YamlTypes.INPUTS, infraDefinitionNode);
    } catch (IOException e) {
      throw new InvalidRequestException(
          format("Error occurred while creating inputs for infra definition : %s", infraEntity.getIdentifier()), e);
    }
    return Optional.of(infraNode);
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

  private Criteria getInfrastructureEqualityCriteriaForEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    return Criteria.where(InfrastructureEntityKeys.accountId)
        .is(accountId)
        .and(InfrastructureEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(InfrastructureEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(InfrastructureEntityKeys.envIdentifier)
        .is(envIdentifier);
  }

  private Criteria getInfrastructureEqualityCriteriaForProject(
      String accountId, String orgIdentifier, String projectIdentifier) {
    return Criteria.where(InfrastructureEntityKeys.accountId)
        .is(accountId)
        .and(InfrastructureEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(InfrastructureEntityKeys.projectIdentifier)
        .is(projectIdentifier);
  }

  public List<InfrastructureYamlMetadata> createInfrastructureYamlMetadata(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentIdentifier, List<String> infraIds) {
    List<InfrastructureEntity> infrastructureEntities = getAllInfrastructureFromIdentifierList(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, infraIds);
    List<InfrastructureYamlMetadata> infrastructureYamlMetadataList = new ArrayList<>();
    infrastructureEntities.forEach(infrastructureEntity
        -> infrastructureYamlMetadataList.add(createInfrastructureYamlMetadataInternal(infrastructureEntity)));
    return infrastructureYamlMetadataList;
  }

  private InfrastructureYamlMetadata createInfrastructureYamlMetadataInternal(
      InfrastructureEntity infrastructureEntity) {
    if (isBlank(infrastructureEntity.getYaml())) {
      log.info(
          "Infrastructure with identifier {} is not configured with an Infrastructure definition. Infrastructure Yaml is empty",
          infrastructureEntity.getIdentifier());
      return InfrastructureYamlMetadata.builder()
          .infrastructureIdentifier(infrastructureEntity.getIdentifier())
          .infrastructureYaml("")
          .inputSetTemplateYaml("")
          .build();
    }

    final String infrastructureInputSetYaml = createInfrastructureInputsFromYaml(infrastructureEntity.getAccountId(),
        infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(),
        infrastructureEntity.getEnvIdentifier(), infrastructureEntity.getIdentifier());
    return InfrastructureYamlMetadata.builder()
        .infrastructureIdentifier(infrastructureEntity.getIdentifier())
        .infrastructureYaml(infrastructureEntity.getYaml())
        .inputSetTemplateYaml(infrastructureInputSetYaml)
        .build();
  }

  @Override
  public String createInfrastructureInputsFromYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, String infraIdentifier) {
    Map<String, Object> yamlInputs = createInfrastructureInputsYamlInternal(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, infraIdentifier);

    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlPipelineUtils.writeYamlString(yamlInputs);
  }

  @Override
  public InfrastructureInputsMergedResponseDto mergeInfraStructureInputs(String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String infraIdentifier, String oldInfrastructureInputsYaml) {
    Optional<InfrastructureEntity> infrastructureEntityOptional =
        get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    if (infrastructureEntityOptional.isEmpty()) {
      throw new NotFoundException(
          format("Infrastructure with identifier [%s] in environment [%s] in project [%s], org [%s] not found",
              infraIdentifier, envIdentifier, projectIdentifier, orgIdentifier));
    }

    InfrastructureEntity infrastructureEntity = infrastructureEntityOptional.get();
    String infraYaml = infrastructureEntity.getYaml();
    if (isEmpty(infraYaml)) {
      return InfrastructureInputsMergedResponseDto.builder()
          .mergedInfrastructureInputsYaml("")
          .infrastructureYaml("")
          .build();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      Optional<ObjectNode> infraDefinitionNodeWithInputs =
          createInfraDefinitionNodeWithInputs(infrastructureEntity, mapper);

      Map<String, Object> yamlInputs = new HashMap<>();
      infraDefinitionNodeWithInputs.ifPresent(
          jsonNodes -> yamlInputs.put(YamlTypes.INPUTS, jsonNodes.get(YamlTypes.INPUTS)));

      String newInfraInputsYaml =
          isNotEmpty(yamlInputs) ? YamlPipelineUtils.writeYamlString(yamlInputs) : StringUtils.EMPTY;

      return InfrastructureInputsMergedResponseDto.builder()
          .mergedInfrastructureInputsYaml(
              InputSetMergeUtility.mergeArrayNodeInputs(oldInfrastructureInputsYaml, newInfraInputsYaml))
          .infrastructureYaml(infraYaml)
          .build();
    } catch (Exception ex) {
      throw new InvalidRequestException("Error occurred while merging old and new infrastructure inputs", ex);
    }
  }

  InfrastructureEntity getInfrastructureFromEnvAndInfraIdentifier(
      String accountId, String orgId, String projectId, String envId, String infraId) {
    Optional<InfrastructureEntity> infrastructureEntity =
        infrastructureRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
            accountId, orgId, projectId, envId, infraId);
    if (infrastructureEntity.isPresent()) {
      return infrastructureEntity.get();
    } else {
      return null;
    }
  }

  private Map<String, Object> createInfrastructureInputsYamlInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String infraIdentifier) {
    Map<String, Object> yamlInputs = new HashMap<>();
    InfrastructureEntity infrastructureEntity = getInfrastructureFromEnvAndInfraIdentifier(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
    ObjectNode infraDefinition = createInfraDefinitionNodeWithInputs(infrastructureEntity);
    if (infraDefinition != null) {
      yamlInputs.put("infrastructureInputs", infraDefinition);
    }
    return yamlInputs;
  }

  private ObjectNode createInfraDefinitionNodeWithInputs(InfrastructureEntity infraEntity) {
    String yaml = infraEntity.getYaml();
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Infrastructure Yaml cannot be empty");
    }
    try {
      String infraDefinitionInputs = RuntimeInputFormHelper.createRuntimeInputForm(yaml, true);
      if (isEmpty(infraDefinitionInputs)) {
        return null;
      }

      YamlField infrastructureDefinitionYamlField =
          YamlUtils.readTree(infraDefinitionInputs).getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
      return (ObjectNode) infrastructureDefinitionYamlField.getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new InvalidRequestException("Error occurred while creating Infrastructure inputs ", e);
    }
  }
}
