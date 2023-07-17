/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.azure;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.utils.AzureUtils;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;

import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(CV)
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractAzureDataRequest extends DataCollectionRequest<AzureConnectorDTO> {
  @Override
  public Map<String, String> collectionHeaders() {
    return AzureUtils.collectionHeaders();
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return new HashMap<>();
  }
}
