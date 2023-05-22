/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.harness;

import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.USERNAME_AND_TOKEN;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = HarnessUsernameTokenDTO.class, name = USERNAME_AND_TOKEN) })
@Schema(name = "HarnessHttpCredentialsSpec",
    description =
        "This is a interface for details of the Harness credentials Specs such as references of username and password")
public interface HarnessHttpCredentialsSpecDTO extends DecryptableEntity {}
