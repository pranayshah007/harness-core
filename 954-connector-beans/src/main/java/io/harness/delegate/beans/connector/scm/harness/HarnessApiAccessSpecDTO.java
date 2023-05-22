/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.harness;

import static io.harness.delegate.beans.connector.scm.harness.HarnessConnectorConstants.JWT_TOKEN;
import static io.harness.delegate.beans.connector.scm.harness.HarnessConnectorConstants.TOKEN;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = HarnessTokenSpecDTO.class, name = TOKEN)
  , @JsonSubTypes.Type(value = HarnessJWTTokenSpecDTO.class, name = JWT_TOKEN)
})
@Schema(name = "HarnessApiAccessSpec",
    description =
        "This contains details of the information such as references of username and password needed for Harness API access")
public interface HarnessApiAccessSpecDTO extends DecryptableEntity {}
