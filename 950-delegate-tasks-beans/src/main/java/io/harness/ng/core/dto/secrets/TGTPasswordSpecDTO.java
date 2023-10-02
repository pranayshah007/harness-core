/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.models.TGTGenerationSpec;
import io.harness.ng.core.models.TGTPasswordSpec;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("Password")
@RecasterAlias("io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO")
public class TGTPasswordSpecDTO extends TGTGenerationSpecDTO implements DecryptableEntity {
  @ApiModelProperty(dataType = "string") @SecretReference private SecretRefData password;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return List.of(this);
  }

  @Override
  public TGTGenerationSpec toEntity() {
    return TGTPasswordSpec.builder().password(getPassword()).build();
  }
}
