/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.awscodecommit.outcome;

import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AwsCodeCommitConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable {
  @NotNull AwsCodeCommitUrlType type;
  @NotNull @NotBlank String url;
  @Valid @NotNull AwsCodeCommitAuthenticationOutcomeDTO authentication;
  Set<String> delegateSelectors;
  String gitConnectionUrl;

  @Builder
  public AwsCodeCommitConnectorOutcomeDTO(AwsCodeCommitUrlType type, String url,
      AwsCodeCommitAuthenticationOutcomeDTO authentication, Set<String> delegateSelectors) {
    this.type = type;
    this.url = url;
    this.authentication = authentication;
    this.delegateSelectors = delegateSelectors;
  }
}
