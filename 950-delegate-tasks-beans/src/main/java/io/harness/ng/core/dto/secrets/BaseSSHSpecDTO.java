/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.ng.core.models.BaseSSHSpec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", visible = true)
@JsonSubTypes(value =
    {
      @JsonSubTypes.Type(value = SSHConfigDTO.class, name = "SSH")
      , @JsonSubTypes.Type(value = KerberosConfigDTO.class, name = "Kerberos"),
    })
@Schema(name = "BaseSSHSpec", description = "This is the SSH specification details as defined in Harness.")
public interface BaseSSHSpecDTO {
  BaseSSHSpec toEntity();
}
