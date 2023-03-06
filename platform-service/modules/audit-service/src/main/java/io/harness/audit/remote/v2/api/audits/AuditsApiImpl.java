/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v2.api.audits;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static javax.ws.rs.core.Response.ResponseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.mapper.ApiUtils;
import io.harness.audit.mapper.AuditEventMapper;
import io.harness.spec.server.audit.v1.AuditsApi;
import io.harness.spec.server.audit.v1.model.AuditFilterPropertiesV1DTO;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class AuditsApiImpl implements AuditsApi {
  private final AuditService auditService;
  private final AuditPermissionValidator auditPermissionValidator;
  private final ApiUtils apiUtils;

  @Override
  public Response postAudits(@Valid AuditFilterPropertiesV1DTO body, String harnessAccount, Integer page,
      @Max(100L) Integer limit, String sort, String order) {
    long startTime = System.currentTimeMillis();
    if (body != null && isNotEmpty(body.getScopes())) {
      for (io.harness.spec.server.audit.v1.model.ResourceScopeDTO resourceScopeDTO : body.getScopes()) {
        auditPermissionValidator.validate(harnessAccount, apiUtils.getResourceScopeDTO(resourceScopeDTO));
      }
    } else {
      auditPermissionValidator.validate(
          harnessAccount, ResourceScopeDTO.builder().accountIdentifier(harnessAccount).build());
    }

    Page<AuditEventDTO> audits = auditService
                                     .list(harnessAccount, apiUtils.getPageRequest(page, limit, sort, order),
                                         apiUtils.getAuditFilterPropertiesV1DTO(body))
                                     .map(AuditEventMapper::toDTO);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        io.harness.utils.ApiUtils.addLinksHeader(responseBuilder, audits.getTotalElements(), page, limit);
    log.info(String.format("Took %d milliseconds for list audit api.", System.currentTimeMillis() - startTime));
    return responseBuilderWithLinks.entity(audits.getContent()).build();
  }
}
