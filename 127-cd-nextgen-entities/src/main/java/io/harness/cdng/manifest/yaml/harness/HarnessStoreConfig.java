/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.harness;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.filters.WithFileRef;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

@OwnedBy(CDP)
public interface HarnessStoreConfig extends StoreConfig, WithFileRef {
  @JsonIgnore List<ParameterField<String>> getFileReferences();
  @JsonIgnore ParameterField<List<HarnessStoreFile>> getFiles();

  @JsonIgnore
  default ParameterField<String> getConnectorReference() {
    throw new UnsupportedOperationException("Connector reference is not supported for Harness store");
  }
}
