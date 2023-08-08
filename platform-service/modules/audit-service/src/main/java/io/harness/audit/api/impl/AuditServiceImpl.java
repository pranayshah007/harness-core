/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.Action.ABORT;
import static io.harness.audit.Action.ADD_COLLABORATOR;
import static io.harness.audit.Action.ADD_MEMBERSHIP;
import static io.harness.audit.Action.CREATE;
import static io.harness.audit.Action.CREATE_TOKEN;
import static io.harness.audit.Action.DELETE;
import static io.harness.audit.Action.END;
import static io.harness.audit.Action.ERROR_BUDGET_RESET;
import static io.harness.audit.Action.FORCE_DELETE;
import static io.harness.audit.Action.INVITE;
import static io.harness.audit.Action.LOGIN;
import static io.harness.audit.Action.LOGIN2FA;
import static io.harness.audit.Action.PAUSE;
import static io.harness.audit.Action.REMOVE_COLLABORATOR;
import static io.harness.audit.Action.REMOVE_MEMBERSHIP;
import static io.harness.audit.Action.RESEND_INVITE;
import static io.harness.audit.Action.RESTORE;
import static io.harness.audit.Action.RESUME;
import static io.harness.audit.Action.REVOKE_INVITE;
import static io.harness.audit.Action.REVOKE_TOKEN;
import static io.harness.audit.Action.SIGNED_EULA;
import static io.harness.audit.Action.STAGE_END;
import static io.harness.audit.Action.STAGE_START;
import static io.harness.audit.Action.START;
import static io.harness.audit.Action.TIMEOUT;
import static io.harness.audit.Action.UNSUCCESSFUL_LOGIN;
import static io.harness.audit.Action.UPDATE;
import static io.harness.audit.Action.UPSERT;
import static io.harness.audit.mapper.AuditEventMapper.fromDTO;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.StaticAuditFilter;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditSettingsService;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.Environment;
import io.harness.audit.beans.Principal;
import io.harness.audit.beans.PrincipalType;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.audit.entities.Resource;
import io.harness.audit.entities.ResourceScope;
import io.harness.audit.entities.YamlDiffRecord;
import io.harness.audit.mapper.ResourceMapper;
import io.harness.audit.mapper.ResourceScopeMapper;
import io.harness.audit.remote.StaticAuditFilterV2;
import io.harness.audit.repositories.AuditRepository;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.KeyValuePair;
import io.harness.ng.core.common.beans.KeyValuePair.KeyValuePairKeys;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
public class AuditServiceImpl implements AuditService {
  private final TransactionTemplate transactionTemplate;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private final AuditRepository auditRepository;
  private final AuditYamlService auditYamlService;
  private final AuditSettingsService auditSettingsService;
  private final AuditFilterPropertiesValidator auditFilterPropertiesValidator;

  public static List<Action> entityChangeEvents = List.of(CREATE, UPDATE, RESTORE, DELETE, FORCE_DELETE, UPSERT, INVITE,
      RESEND_INVITE, REVOKE_INVITE, ADD_COLLABORATOR, REMOVE_COLLABORATOR, CREATE_TOKEN, REVOKE_TOKEN, ADD_MEMBERSHIP,
      REMOVE_MEMBERSHIP, ERROR_BUDGET_RESET, SIGNED_EULA);
  public static List<Action> loginEvents = List.of(LOGIN, LOGIN2FA, UNSUCCESSFUL_LOGIN);
  public static List<Action> runTimeEvents = List.of(START, STAGE_START, STAGE_END, END, PAUSE, RESUME, ABORT, TIMEOUT);

  @Inject
  public AuditServiceImpl(AuditRepository auditRepository, AuditYamlService auditYamlService,
      AuditFilterPropertiesValidator auditFilterPropertiesValidator, TransactionTemplate transactionTemplate,
      AuditSettingsService auditSettingsService) {
    this.auditRepository = auditRepository;
    this.auditYamlService = auditYamlService;
    this.auditFilterPropertiesValidator = auditFilterPropertiesValidator;
    this.transactionTemplate = transactionTemplate;
    this.auditSettingsService = auditSettingsService;
  }

  @Override
  public Boolean create(AuditEventDTO auditEventDTO) {
    AuditEvent auditEvent = fromDTO(auditEventDTO);
    try {
      long startTime = System.currentTimeMillis();
      Boolean result = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        AuditEvent savedAuditEvent = auditRepository.save(auditEvent);
        saveYamlDiff(auditEventDTO, savedAuditEvent.getId());
        return true;
      }));
      log.info(String.format("Took %d milliseconds for create audit db operation for insertId %s.",
          System.currentTimeMillis() - startTime, auditEventDTO.getInsertId()));
      return result;
    } catch (DuplicateKeyException ex) {
      log.info("Audit for this entry already exists with id {} and account identifier {}", auditEvent.getInsertId(),
          auditEvent.getResourceScope().getAccountIdentifier());
      return true;
    } catch (Exception e) {
      log.error("Could not audit this event with id {} and account identifier {}", auditEvent.getInsertId(),
          auditEvent.getResourceScope().getAccountIdentifier(), e);
      return false;
    }
  }

  private void saveYamlDiff(AuditEventDTO auditEventDTO, String auditId) {
    if (auditEventDTO.getYamlDiffRecord() != null) {
      YamlDiffRecord yamlDiffRecord = YamlDiffRecord.builder()
                                          .auditId(auditId)
                                          .accountIdentifier(auditEventDTO.getResourceScope().getAccountIdentifier())
                                          .oldYaml(auditEventDTO.getYamlDiffRecord().getOldYaml())
                                          .newYaml(auditEventDTO.getYamlDiffRecord().getNewYaml())
                                          .timestamp(Instant.ofEpochMilli(auditEventDTO.getTimestamp()))
                                          .build();
      auditYamlService.save(yamlDiffRecord);
    }
  }

  @Override
  public Optional<AuditEvent> get(String accountIdentifier, String auditId) {
    Criteria criteria =
        Criteria.where(AuditEventKeys.id).is(auditId).and(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(accountIdentifier);
    return Optional.ofNullable(auditRepository.get(criteria));
  }

  @Override
  public Page<AuditEvent> list(
      String accountIdentifier, PageRequest pageRequest, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    long startTime = System.currentTimeMillis();
    auditFilterPropertiesValidator.validate(accountIdentifier, auditFilterPropertiesDTO);
    Criteria criteria = getFilterCriteria(accountIdentifier, auditFilterPropertiesDTO);
    Page<AuditEvent> result = auditRepository.findAll(criteria, getPageRequest(pageRequest));
    log.info(
        String.format("Took %d milliseconds for list audit db operation.", System.currentTimeMillis() - startTime));
    return result;
  }

  private Criteria getFilterCriteria(String accountIdentifier, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    List<Criteria> criteriaList = new ArrayList<>();
    criteriaList.add(getBaseScopeCriteria(accountIdentifier));
    if (auditFilterPropertiesDTO == null) {
      return criteriaList.get(0);
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getScopes())) {
      criteriaList.add(getScopeCriteria(auditFilterPropertiesDTO.getScopes()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getResources())) {
      criteriaList.add(getResourceCriteria(auditFilterPropertiesDTO.getResources()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getModules())) {
      criteriaList.add(Criteria.where(AuditEventKeys.module).in(auditFilterPropertiesDTO.getModules()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getActions())) {
      criteriaList.add(Criteria.where(AuditEventKeys.action).in(auditFilterPropertiesDTO.getActions()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getEnvironments())) {
      criteriaList.add(getEnvironmentCriteria(auditFilterPropertiesDTO.getEnvironments()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getPrincipals())) {
      criteriaList.add(getPrincipalCriteria(auditFilterPropertiesDTO.getPrincipals()));
    }
    criteriaList.add(
        Criteria.where(AuditEventKeys.timestamp)
            .gte(Instant.ofEpochMilli(
                auditFilterPropertiesDTO.getStartTime() == null ? 0 : auditFilterPropertiesDTO.getStartTime())));
    criteriaList.add(Criteria.where(AuditEventKeys.timestamp)
                         .lte(Instant.ofEpochMilli(auditFilterPropertiesDTO.getEndTime() == null
                                 ? currentTimeMillis()
                                 : auditFilterPropertiesDTO.getEndTime())));
    Criteria staticFilterCriteria;
    if (isNotEmpty(auditFilterPropertiesDTO.getStaticFilters())) {
      staticFilterCriteria = getStaticFilterCriteria(auditFilterPropertiesDTO.getStaticFilters());
    } else {
      staticFilterCriteria = getStaticFilterCriteria(auditFilterPropertiesDTO.getStaticFilter());
    }
    return new Criteria().andOperator(
        new Criteria().andOperator(criteriaList.toArray(new Criteria[0])), staticFilterCriteria);
  }

  private Criteria getStaticFilterCriteria(StaticAuditFilter staticFilter) {
    if (staticFilter == StaticAuditFilter.EXCLUDE_LOGIN_EVENTS) {
      return new Criteria().norOperator(Criteria.where(AuditEventKeys.action).is(LOGIN),
          Criteria.where(AuditEventKeys.action).is(LOGIN2FA),
          Criteria.where(AuditEventKeys.action).is(UNSUCCESSFUL_LOGIN));
    } else if (staticFilter == StaticAuditFilter.EXCLUDE_SYSTEM_EVENTS) {
      return new Criteria().norOperator(Criteria.where(AuditEventKeys.PRINCIPAL_TYPE_KEY).is(PrincipalType.SYSTEM));
    }
    return new Criteria();
  }

  private Criteria getStaticFilterCriteria(List<StaticAuditFilterV2> staticFilter) {
    List<Criteria> criteriaList = new ArrayList<>();

    Criteria loginCriteria = Criteria.where(AuditEventKeys.action).in(loginEvents);
    Criteria systemCriteria = Criteria.where(AuditEventKeys.PRINCIPAL_TYPE_KEY).is(PrincipalType.SYSTEM);
    Criteria entityChangeEventCriteria = Criteria.where(AuditEventKeys.action).in(entityChangeEvents);
    Criteria runTimeEventCriteria = Criteria.where(AuditEventKeys.action).in(runTimeEvents);

    if (staticFilter.contains(StaticAuditFilterV2.LOGIN_EVENTS)) {
      criteriaList.add(loginCriteria);
    }
    if (staticFilter.contains(StaticAuditFilterV2.SYSTEM_EVENTS)) {
      criteriaList.add(systemCriteria);
    }
    if (staticFilter.contains(StaticAuditFilterV2.ENTITY_CHANGE_EVENTS)) {
      criteriaList.add(entityChangeEventCriteria);
    }
    if (staticFilter.contains(StaticAuditFilterV2.RUNTIME_EVENTS)) {
      criteriaList.add(runTimeEventCriteria);
    }
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria getBaseScopeCriteria(String accountIdentifier) {
    return Criteria.where(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(accountIdentifier);
  }

  private Criteria getScopeCriteria(List<ResourceScopeDTO> resourceScopes) {
    List<Criteria> criteriaList = new ArrayList<>();
    resourceScopes.forEach(resourceScope -> {
      Criteria criteria =
          Criteria.where(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(resourceScope.getAccountIdentifier());
      if (isNotEmpty(resourceScope.getOrgIdentifier())) {
        criteria.and(AuditEventKeys.ORG_IDENTIFIER_KEY).is(resourceScope.getOrgIdentifier());
        if (isNotEmpty(resourceScope.getProjectIdentifier())) {
          criteria.and(AuditEventKeys.PROJECT_IDENTIFIER_KEY).is(resourceScope.getProjectIdentifier());
          ResourceScope dbo = ResourceScopeMapper.fromDTO(resourceScope);
          List<KeyValuePair> labels = dbo.getLabels();
          if (isNotEmpty(labels)) {
            List<Criteria> labelsCriteria = new ArrayList<>();
            labels.forEach(label
                -> labelsCriteria.add(Criteria.where(AuditEventKeys.RESOURCE_SCOPE_LABEL_KEY)
                                          .elemMatch(Criteria.where(KeyValuePairKeys.key)
                                                         .is(label.getKey())
                                                         .and(KeyValuePairKeys.value)
                                                         .is(label.getValue()))));
            criteria.andOperator(labelsCriteria.toArray(new Criteria[0]));
          }
        }
      }
      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria getResourceCriteria(List<ResourceDTO> resources) {
    List<Criteria> criteriaList = new ArrayList<>();
    resources.forEach(resource -> {
      Criteria criteria = Criteria.where(AuditEventKeys.RESOURCE_TYPE_KEY).is(resource.getType());
      if (isNotEmpty(resource.getIdentifier())) {
        criteria.and(AuditEventKeys.RESOURCE_IDENTIFIER_KEY).is(resource.getIdentifier());
      }
      Resource dbo = ResourceMapper.fromDTO(resource);
      List<KeyValuePair> labels = dbo.getLabels();
      if (isNotEmpty(labels)) {
        List<Criteria> labelsCriteria = new ArrayList<>();
        labels.forEach(label
            -> labelsCriteria.add(Criteria.where(AuditEventKeys.RESOURCE_LABEL_KEY)
                                      .elemMatch(Criteria.where(KeyValuePairKeys.key)
                                                     .is(label.getKey())
                                                     .and(KeyValuePairKeys.value)
                                                     .is(label.getValue()))));
        criteria.andOperator(labelsCriteria.toArray(new Criteria[0]));
      }

      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria getPrincipalCriteria(List<Principal> principals) {
    List<Criteria> criteriaList = new ArrayList<>();
    principals.forEach(principal -> {
      Criteria criteria = Criteria.where(AuditEventKeys.PRINCIPAL_TYPE_KEY)
                              .is(principal.getType())
                              .and(AuditEventKeys.PRINCIPAL_IDENTIFIER_KEY)
                              .is(principal.getIdentifier());
      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  @Override
  public void purgeAuditsOlderThanTimestamp(String accountIdentifier, Instant timestamp) {
    auditRepository.delete(Criteria.where(AuditEventKeys.timestamp)
                               .lte(timestamp)
                               .and(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                               .is(accountIdentifier));
  }

  @Override
  public Set<String> getUniqueAuditedAccounts() {
    return new HashSet<>(auditRepository.fetchDistinctAccountIdentifiers());
  }

  private Criteria getEnvironmentCriteria(List<Environment> environments) {
    List<Criteria> criteriaList = new ArrayList<>();
    environments.forEach(environment -> {
      Criteria criteria = new Criteria();
      if (environment.getType() != null) {
        criteria.and(AuditEventKeys.ENVIRONMENT_TYPE_KEY).is(environment.getType());
      }
      if (isNotEmpty(environment.getIdentifier())) {
        criteria.and(AuditEventKeys.ENVIRONMENT_IDENTIFIER_KEY).is(environment.getIdentifier());
      }
      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  @Override
  public void deleteAuditInfo(String accountId) {
    log.info("Starting the process to delete Audit Events, Yaml Diff and Audit settings for account: " + accountId);
    auditYamlService.deleteByAccount(accountId);
    auditRepository.delete(Criteria.where(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(accountId));
    auditSettingsService.deleteByAccountIdentifier(accountId);
    log.info("Cleaned Audit Events, Yaml Diff and Audit settings for account: " + accountId);
  }
}
