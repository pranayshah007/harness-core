/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonSubTypes({ @JsonSubTypes.Type(value = GcpManualDetailsDTO.class, name = GcpConstants.MANUAL_CONFIG) })
@ApiModel("GcpCredentialSpec")
@Schema(name = "GcpCredentialSpec", description = "This contains GCP ConnectorDisconnectHandler credentials spec")
public interface GcpCredentialSpecDTO extends DecryptableEntity {}
