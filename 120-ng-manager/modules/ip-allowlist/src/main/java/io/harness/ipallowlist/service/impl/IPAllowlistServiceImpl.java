/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.service.impl;

import static io.harness.exception.WingsException.USER;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.NoResultFoundException;
import io.harness.ipallowlist.IPAllowlistResourceUtils;
import io.harness.ipallowlist.dto.IPAllowlistFilterDTO;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.events.IPAllowlistConfigCreateEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigDeleteEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigUpdateEvent;
import io.harness.ipallowlist.service.IPAllowlistService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ipallowlist.spring.IPAllowlistRepository;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class IPAllowlistServiceImpl implements IPAllowlistService {
  private final IPAllowlistRepository ipAllowlistRepository;
  OutboxService outboxService;
  TransactionTemplate transactionTemplate;

  private final IPAllowlistResourceUtils ipAllowlistResourceUtil;

  @Inject
  public IPAllowlistServiceImpl(IPAllowlistRepository ipAllowlistRepository, OutboxService outboxService,
      TransactionTemplate transactionTemplate, IPAllowlistResourceUtils ipAllowlistResourceUtil) {
    this.ipAllowlistRepository = ipAllowlistRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.ipAllowlistResourceUtil = ipAllowlistResourceUtil;
  }

  @Override
  public IPAllowlistEntity create(IPAllowlistEntity ipAllowlistEntity) {
    try {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        IPAllowlistEntity savedIpAllowlistEntity = ipAllowlistRepository.save(ipAllowlistEntity);
        outboxService.save(new IPAllowlistConfigCreateEvent(savedIpAllowlistEntity.getAccountIdentifier(),
            ipAllowlistResourceUtil.toIPAllowlistConfig(savedIpAllowlistEntity)));
        return savedIpAllowlistEntity;
      }));
    } catch (DuplicateKeyException exception) {
      String message =
          String.format("IP Allowlist config with identifier [%s] already exists.", ipAllowlistEntity.getIdentifier());
      log.error(message, exception);
      throw new DuplicateFieldException(message);
    }
  }

  @Override
  public IPAllowlistEntity get(String accountIdentifier, String identifier) {
    Optional<IPAllowlistEntity> optionalIPAllowlistEntity =
        ipAllowlistRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);

    if (optionalIPAllowlistEntity.isEmpty()) {
      String message = String.format("IP Allowlist config with identifier [%s] not found.", identifier);
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message(message)
          .level(Level.ERROR)
          .reportTargets(USER)
          .build();
    }

    return optionalIPAllowlistEntity.get();
  }

  @Override
  public IPAllowlistEntity update(String ipConfigIdentifier, IPAllowlistEntity ipAllowlistEntity) {
    IPAllowlistEntity existingIPAllowlist = get(ipAllowlistEntity.getAccountIdentifier(), ipConfigIdentifier);

    ipAllowlistEntity.setCreated(existingIPAllowlist.getCreated());
    ipAllowlistEntity.setUpdated(existingIPAllowlist.getUpdated());
    ipAllowlistEntity.setCreatedBy(existingIPAllowlist.getCreatedBy());
    ipAllowlistEntity.setId(existingIPAllowlist.getId());
    try {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        IPAllowlistEntity savedIpAllowlistEntity = ipAllowlistRepository.save(ipAllowlistEntity);
        outboxService.save(new IPAllowlistConfigUpdateEvent(savedIpAllowlistEntity.getAccountIdentifier(),

            ipAllowlistResourceUtil.toIPAllowlistConfig(savedIpAllowlistEntity),
            ipAllowlistResourceUtil.toIPAllowlistConfig(existingIPAllowlist)));
        return savedIpAllowlistEntity;
      }));
    } catch (DuplicateKeyException exception) {
      String message =
          String.format("IP Allowlist config with identifier [%s] already exists.", ipAllowlistEntity.getIdentifier());
      log.error(message, exception);
      throw new DuplicateFieldException(message);
    }
  }

  @Override
  public boolean delete(String accountIdentifier, String identifier) {
    IPAllowlistEntity ipAllowlistEntity = get(accountIdentifier, identifier);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ipAllowlistRepository.deleteByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
      outboxService.save(new IPAllowlistConfigDeleteEvent(
          ipAllowlistEntity.getAccountIdentifier(), ipAllowlistResourceUtil.toIPAllowlistConfig(ipAllowlistEntity)));
      return true;
    }));
  }

  @Override
  public boolean validateUniqueness(String accountIdentifier, String identifier) {
    Optional<IPAllowlistEntity> optionalIPAllowlistEntity =
        ipAllowlistRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    return optionalIPAllowlistEntity.isEmpty();
  }

  @Override
  public Page<IPAllowlistEntity> list(
      String accountIdentifier, Pageable pageable, IPAllowlistFilterDTO ipAllowlistFilterDTO) {
    Criteria criteria = getCriteriaForIPAllowlistConfigs(accountIdentifier, ipAllowlistFilterDTO);
    return ipAllowlistRepository.findAll(criteria, pageable);
  }

  private Criteria getCriteriaForIPAllowlistConfigs(
      String accountIdentifier, IPAllowlistFilterDTO ipAllowlistFilterDTO) {
    Criteria criteria = Criteria.where(IPAllowlistEntity.IPAllowlistConfigKeys.accountIdentifier).is(accountIdentifier);
    if (null != ipAllowlistFilterDTO.getAllowedSourceType()) {
      criteria.and(IPAllowlistEntity.IPAllowlistConfigKeys.allowedSourceType)
          .is(ipAllowlistFilterDTO.getAllowedSourceType());
    }
    if (StringUtils.isNotEmpty(ipAllowlistFilterDTO.getSearchTerm())) {
      criteria.orOperator(
          Criteria.where(IPAllowlistEntity.IPAllowlistConfigKeys.name)
              .regex(ipAllowlistFilterDTO.getSearchTerm(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(IPAllowlistEntity.IPAllowlistConfigKeys.identifier)
              .regex(ipAllowlistFilterDTO.getSearchTerm(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }
    return criteria;
  }
}