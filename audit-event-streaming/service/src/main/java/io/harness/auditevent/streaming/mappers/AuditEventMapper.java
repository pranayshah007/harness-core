/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.mappers;

import static io.harness.audit.AuditCommonConstants.USER_ID;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.PrincipalType;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.AuthenticationInfo;
import io.harness.auditevent.streaming.entities.outgoing.Author;
import io.harness.auditevent.streaming.entities.outgoing.HttpRequestInfo;
import io.harness.auditevent.streaming.entities.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.entities.outgoing.Principal;
import io.harness.auditevent.streaming.entities.outgoing.Resource;
import io.harness.auditevent.streaming.entities.outgoing.ResourceScope;
import io.harness.ng.core.common.beans.KeyValuePair;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@OwnedBy(HarnessTeam.PL)
@Component
public class AuditEventMapper {
  public OutgoingAuditMessage toOutgoingAuditMessage(AuditEvent auditEvent) {
    return OutgoingAuditMessage.builder()
        .auditEventId(auditEvent.getId())
        .auditEventAuthor(Author.builder().principal(getPrincipal(auditEvent.getAuthenticationInfo())).build())
        .auditModule(auditEvent.getModule())
        .auditResource(Resource.builder()
                           .identifier(auditEvent.getResource().getIdentifier())
                           .type(auditEvent.getResource().getType())
                           .build())
        .auditResourceScope(ResourceScope.builder()
                                .accountIdentifier(auditEvent.getResourceScope().getAccountIdentifier())
                                .orgIdentifier(auditEvent.getResourceScope().getOrgIdentifier())
                                .projectIdentifier(auditEvent.getResourceScope().getProjectIdentifier())
                                .build())
        .auditAction(auditEvent.getAction())
        .auditHttpRequestInfo(getHttpRequestInfo(auditEvent))
        .auditEventTime(auditEvent.getTimestamp())
        .build();
  }

  private HttpRequestInfo getHttpRequestInfo(AuditEvent auditEvent) {
    if (auditEvent.getHttpRequestInfo() == null && auditEvent.getRequestMetadata() == null) {
      return null;
    }
    HttpRequestInfo.HttpRequestInfoBuilder httpRequestInfoBuilder = HttpRequestInfo.builder();
    if (auditEvent.getHttpRequestInfo() != null) {
      httpRequestInfoBuilder.requestMethod(auditEvent.getHttpRequestInfo().getRequestMethod());
    }
    if (auditEvent.getRequestMetadata() != null) {
      httpRequestInfoBuilder.clientIP(auditEvent.getRequestMetadata().getClientIP());
    }
    return httpRequestInfoBuilder.build();
  }

  private Principal getPrincipal(AuthenticationInfo authenticationInfo) {
    Principal.PrincipalBuilder principalBuilder = Principal.builder().type(authenticationInfo.getPrincipal().getType());
    if (authenticationInfo.getPrincipal().getType().equals(PrincipalType.USER)) {
      principalBuilder.email(authenticationInfo.getPrincipal().getIdentifier());
    }
    if (isNotEmpty(authenticationInfo.getLabels())) {
      Map<String, String> labels = authenticationInfo.getLabels().stream().collect(
          Collectors.toMap(KeyValuePair::getKey, KeyValuePair::getValue));
      principalBuilder.identifier(labels.get(USER_ID));
    } else {
      principalBuilder.identifier(authenticationInfo.getPrincipal().getIdentifier());
    }
    return principalBuilder.build();
  }
}
