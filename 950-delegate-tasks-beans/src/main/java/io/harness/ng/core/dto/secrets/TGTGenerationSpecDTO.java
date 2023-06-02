/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.beans.DecryptableEntity;
import io.harness.ng.core.models.TGTGenerationSpec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "tgtGenerationMethod",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = TGTKeyTabFilePathSpecDTO.class, name = "KeyTabFilePath")
  , @JsonSubTypes.Type(value = TGTPasswordSpecDTO.class, name = "Password")
})
public abstract class TGTGenerationSpecDTO {
  @JsonIgnore public abstract List<DecryptableEntity> getDecryptableEntities();
  public abstract TGTGenerationSpec toEntity();
}
