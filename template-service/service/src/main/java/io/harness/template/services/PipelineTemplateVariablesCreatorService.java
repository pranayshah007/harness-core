/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc;
import io.harness.pms.contracts.service.VariablesServiceRequestV2;
import io.harness.pms.mappers.VariablesResponseDtoMapper;
import io.harness.pms.variables.VariableMergeServiceResponse;

import com.google.inject.Inject;

public class PipelineTemplateVariablesCreatorService implements TemplateVariableCreatorService {
  @Inject VariablesServiceGrpc.VariablesServiceBlockingStub variablesServiceBlockingStub;

  @Override
  public boolean supportsVariables() {
    return true;
  }

  @Override
  public VariableMergeServiceResponse getVariables(
      String accountId, String orgId, String projectId, String entityYaml, TemplateEntityType templateEntityType) {
    VariablesServiceRequestV2.Builder requestBuilder = VariablesServiceRequestV2.newBuilder();
    requestBuilder.setAccountId(accountId);
    if (EmptyPredicate.isNotEmpty(orgId)) {
      requestBuilder.setOrgId(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      requestBuilder.setProjectId(projectId);
    }
    requestBuilder.setYaml(entityYaml);
    VariablesServiceRequestV2 request = requestBuilder.build();
    VariableMergeResponseProto variables = variablesServiceBlockingStub.getVariablesV2(request);
    return VariablesResponseDtoMapper.toDto(variables);
  }
}
