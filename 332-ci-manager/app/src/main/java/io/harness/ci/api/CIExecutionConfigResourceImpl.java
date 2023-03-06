/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.Operation;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.cimanager.executionconfig.api.CIExecutionConfigResource;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@OwnedBy(CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class CIExecutionConfigResourceImpl implements CIExecutionConfigResource {
  @Inject CIExecutionConfigService configService;

  public ResponseDTO<Boolean> updateExecutionConfig(Type infra, String accountIdentifier, List<Operation> operations) {
    return ResponseDTO.newResponse(configService.updateCIContainerTags(accountIdentifier, operations, infra));
  }

  public ResponseDTO<Boolean> resetExecutionConfig(Type infra, String accountIdentifier, List<Operation> operations) {
    return ResponseDTO.newResponse(configService.resetCIContainerTags(accountIdentifier, operations, infra));
  }

  public ResponseDTO<Boolean> deleteExecutionConfig(String accountIdentifier) {
    return ResponseDTO.newResponse(configService.deleteCIExecutionConfig(accountIdentifier));
  }

  public ResponseDTO<List<DeprecatedImageInfo>> getExecutionConfig(String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getDeprecatedTags(accountIdentifier));
  }

  public ResponseDTO<CIExecutionImages> getDeprecatedConfig(String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getDeprecatedImages(accountIdentifier));
  }

  public ResponseDTO<CIExecutionImages> getCustomerConfig(Type infra, boolean overridesOnly, String accountIdentifier) {
    CIExecutionImages ciExecutionImages = configService.getCustomerConfig(accountIdentifier, infra, overridesOnly);
    return ResponseDTO.newResponse(ciExecutionImages);
  }

  public ResponseDTO<CIExecutionImages> getDefaultConfig(Type infra) {
    return ResponseDTO.newResponse(configService.getDefaultConfig(infra));
  }
}
