/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceImplBase;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.pms.contracts.service.VariablesServiceRequestV2;
import io.harness.pms.mappers.VariablesResponseDtoMapper;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Singleton
public class VariablesServiceImpl extends VariablesServiceImplBase {
  private final VariableCreatorMergeService variableCreatorMergeService;

  @Inject
  public VariablesServiceImpl(VariableCreatorMergeService variableCreatorMergeService) {
    this.variableCreatorMergeService = variableCreatorMergeService;
  }

  @Override
  public void getVariables(
      VariablesServiceRequest request, StreamObserver<VariableMergeResponseProto> responseObserver) {
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    VariableMergeServiceResponse variablesResponse =
        variableCreatorMergeService.createVariablesResponses(request.getYaml(), false);
    VariableMergeResponseProto variableMergeResponseProto = VariablesResponseDtoMapper.toProto(variablesResponse);
    responseObserver.onNext(variableMergeResponseProto);
    responseObserver.onCompleted();
  }

  @Override
  public void getVariablesV2(
      VariablesServiceRequestV2 request, StreamObserver<VariableMergeResponseProto> responseObserver) {
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
    VariableMergeServiceResponse variablesResponse = variableCreatorMergeService.createVariablesResponsesV2(
        request.getAccountId(), request.getOrgId(), request.getProjectId(), request.getYaml());
    VariableMergeResponseProto variableMergeResponseProto = VariablesResponseDtoMapper.toProto(variablesResponse);
    responseObserver.onNext(variableMergeResponseProto);
    responseObserver.onCompleted();
  }
}
