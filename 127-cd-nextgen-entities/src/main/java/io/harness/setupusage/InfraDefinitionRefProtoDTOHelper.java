/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.setupusage;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;

import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class InfraDefinitionRefProtoDTOHelper {
  public static InfraDefinitionReferenceProtoDTO createInfraDefinitionReferenceProtoDTO(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String envIdentifier, String identifier, String envName) {
    InfraDefinitionReferenceProtoDTO.Builder identifierRefBuilder =
        InfraDefinitionReferenceProtoDTO.newBuilder()
            .setIdentifier(StringValue.of(identifier))
            .setAccountIdentifier(StringValue.of(accountIdentifier));
    if (isNotBlank(orgIdentifier)) {
      identifierRefBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }
    if (isNotBlank(projectIdentifier)) {
      identifierRefBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }
    if (isNotBlank(envIdentifier)) {
      identifierRefBuilder.setEnvIdentifier(StringValue.of(envIdentifier));
    }
    if (isNotBlank(envName)) {
      identifierRefBuilder.setEnvName(StringValue.of(envName));
    }
    return identifierRefBuilder.build();
  }
}
