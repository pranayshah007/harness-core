/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceImplBase;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.pms.mappers.VariablesResponseDtoMapper;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.variables.VariableMergeServiceResponse;

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
    VariableMergeServiceResponse variablesResponse =
        variableCreatorMergeService.createVariablesResponses(request.getYaml(), false);
    VariableMergeResponseProto variableMergeResponseProto = VariablesResponseDtoMapper.toProto(variablesResponse);
    responseObserver.onNext(variableMergeResponseProto);
    responseObserver.onCompleted();
  }
}
