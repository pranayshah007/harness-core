/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.client.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.AuditCommonConstants.CORRELATION_ID;
import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.context.MdcGlobalContextData.MDC_ID;
import static io.harness.ng.core.CorrelationContext.getCorrelationIdKey;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.request.RequestContextData.REQUEST_CONTEXT;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditEventDTO.AuditEventDTOBuilder;
import io.harness.audit.beans.AuthenticationInfoDTO;
import io.harness.audit.beans.PrincipalType;
import io.harness.audit.beans.YamlDiffRecordDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.audit.client.remote.AuditClient;
import io.harness.context.GlobalContext;
import io.harness.context.MdcGlobalContextData;
import io.harness.request.HttpRequestInfo;
import io.harness.request.RequestContextData;
import io.harness.request.RequestMetadata;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class AuditClientServiceImpl implements AuditClientService {
  private final AuditClient auditClient;

  @Inject
  public AuditClientServiceImpl(AuditClient auditClient) {
    this.auditClient = auditClient;
  }

  public boolean publishAudit(AuditEntry auditEntry, GlobalContext globalContext) {
    AuditEventDTO auditEventDTO = getAuditEventDTO(auditEntry, globalContext);
    return getResponse(auditClient.createAudit(auditEventDTO));
  }

  @Override
  public boolean publishAudit(
      AuditEntry auditEntry, AuthenticationInfoDTO authenticationInfo, GlobalContext globalContext) {
    AuditEventDTO auditEventDTO = getAuditEventDTO(auditEntry, globalContext);
    auditEventDTO.setAuthenticationInfo(authenticationInfo);
    return getResponse(auditClient.createAudit(auditEventDTO));
  }

  private AuditEventDTO getAuditEventDTO(AuditEntry auditEntry, GlobalContext globalContext) {
    HttpRequestInfo httpRequestInfo = null;
    RequestMetadata requestMetadata = null;
    Principal principal = null;
    String correlationId = null;
    if (globalContext != null && globalContext.get(REQUEST_CONTEXT) instanceof RequestContextData
        && ((RequestContextData) globalContext.get(REQUEST_CONTEXT)).getRequestContext() != null) {
      httpRequestInfo =
          ((RequestContextData) globalContext.get(REQUEST_CONTEXT)).getRequestContext().getHttpRequestInfo();
      requestMetadata =
          ((RequestContextData) globalContext.get(REQUEST_CONTEXT)).getRequestContext().getRequestMetadata();
    }
    if (globalContext != null && globalContext.get(PRINCIPAL_CONTEXT) instanceof PrincipalContextData) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    if (globalContext != null && globalContext.get(MDC_ID) instanceof MdcGlobalContextData
        && ((MdcGlobalContextData) globalContext.get(MDC_ID)).getMap() != null) {
      correlationId = ((MdcGlobalContextData) globalContext.get(MDC_ID)).getMap().get(getCorrelationIdKey());
    }
    YamlDiffRecordDTO yamlDiffRecordDTO = null;
    if (auditEntry.getNewYaml() != null || auditEntry.getOldYaml() != null) {
      yamlDiffRecordDTO =
          YamlDiffRecordDTO.builder().newYaml(auditEntry.getNewYaml()).oldYaml(auditEntry.getOldYaml()).build();
    }

    AuditEventDTOBuilder auditEventDTOBuilder = AuditEventDTO.builder()
                                                    .resource(auditEntry.getResource())
                                                    .action(auditEntry.getAction())
                                                    .resourceScope(auditEntry.getResourceScope())
                                                    .insertId(auditEntry.getInsertId())
                                                    .module(auditEntry.getModule())
                                                    .auditEventData(auditEntry.getAuditEventData())
                                                    .environment(auditEntry.getEnvironment())
                                                    .yamlDiffRecord(yamlDiffRecordDTO)
                                                    .timestamp(auditEntry.getTimestamp());

    if (principal != null) {
      auditEventDTOBuilder.authenticationInfo(fromSecurityPrincipal(principal));
    } else {
      log.error(String.format(
          "[AUDIT_ERROR]: Principal not found for audit entry with insertId %s, this should not happen. Please check!!",
          auditEntry.getInsertId()));
      auditEventDTOBuilder.authenticationInfo(
          fromSecurityPrincipal(new ServicePrincipal(String.valueOf(PrincipalType.SYSTEM))));
    }
    if (requestMetadata != null) {
      auditEventDTOBuilder.requestMetadata(requestMetadata);
    }
    if (httpRequestInfo != null) {
      auditEventDTOBuilder.httpRequestInfo(httpRequestInfo);
    }
    if (correlationId != null) {
      auditEventDTOBuilder.internalInfo(ImmutableMap.of(CORRELATION_ID, correlationId));
    }
    return auditEventDTOBuilder.build();
  }
}
