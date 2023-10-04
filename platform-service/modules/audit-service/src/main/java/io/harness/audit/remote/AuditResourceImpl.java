/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.beans.custom.AccountMetricsDTO;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.audit.mapper.AuditEventMapper;
import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import javax.ws.rs.BeanParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@Slf4j
public class AuditResourceImpl implements AuditResource {
  private final AuditService auditService;

  @Inject
  public AuditResourceImpl(AuditService auditService, AuditPermissionValidator auditPermissionValidator) {
    this.auditService = auditService;
    this.auditPermissionValidator = auditPermissionValidator;
  }

  private final AuditPermissionValidator auditPermissionValidator;

  @InternalApi
  public ResponseDTO<Boolean> create(AuditEventDTO auditEventDTO) {
    long startTime = System.currentTimeMillis();
    ResponseDTO<Boolean> response = ResponseDTO.newResponse(auditService.create(auditEventDTO));
    log.info(String.format("Took %d milliseconds for create audit api for insertId %s.",
        System.currentTimeMillis() - startTime, auditEventDTO.getInsertId()));
    return response;
  }

  public ResponseDTO<PageResponse<AuditEventDTO>> list(
      String accountIdentifier, @BeanParam PageRequest pageRequest, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    long startTime = System.currentTimeMillis();
    if (auditFilterPropertiesDTO != null && isNotEmpty(auditFilterPropertiesDTO.getScopes())) {
      for (ResourceScopeDTO resourceScopeDTO : auditFilterPropertiesDTO.getScopes()) {
        auditPermissionValidator.validate(accountIdentifier, resourceScopeDTO);
      }
    } else {
      auditPermissionValidator.validate(
          accountIdentifier, ResourceScopeDTO.builder().accountIdentifier(accountIdentifier).build());
    }
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order = SortOrder.Builder.aSortOrder().withField(AuditEventKeys.timestamp, DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    Page<AuditEventDTO> audits =
        auditService.list(accountIdentifier, pageRequest, auditFilterPropertiesDTO).map(AuditEventMapper::toDTO);
    ResponseDTO<PageResponse<AuditEventDTO>> response = ResponseDTO.newResponse(getNGPageResponse(audits));
    log.info(String.format("Took %d milliseconds for list audit api.", System.currentTimeMillis() - startTime));
    return response;
  }

  @InternalApi
  public ResponseDTO<Void> publishMetrics(AccountMetricsDTO accountMetricsDTO) {
    auditService.computeMetricsForActiveProject(accountMetricsDTO.getAccountIds(), accountMetricsDTO.getProjectCounts(),
        accountMetricsDTO.getStartTime(), accountMetricsDTO.getEndTime());

    auditService.computeMetricsForUniqueLogins(
        accountMetricsDTO.getAccountIds(), accountMetricsDTO.getStartTime(), accountMetricsDTO.getEndTime());

    return ResponseDTO.newResponse();
  }
}
