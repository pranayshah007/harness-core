/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.beans.connector.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(HarnessTeam.CDP)
@JsonSubTypes({ @JsonSubTypes.Type(value = TasManualDetailsDTO.class, name = TasConstants.MANUAL_CONFIG) })
@ApiModel("TasCredentialSpec")
@Schema(name = "TasCredentialSpec", description = "This contains Tas ConnectorDisconnectHandler credentials spec")
public interface TasCredentialSpecDTO extends DecryptableEntity {}
