/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfig;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"connectorRef"})
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CustomSecretManagerConfigDTO extends SecretManagerConfigDTO implements DelegateSelectable {
  Set<String> delegateSelectors;
  private boolean onDelegate;

  @Schema(description = SecretManagerDescriptionConstants.CUSTOM_AUTH_TOKEN) private String connectorRef;
  private String host;
  private String workingDirectory;
  @NotNull private TemplateLinkConfig template;
  private String script;
}
