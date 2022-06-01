/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.Collection;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class LdapGroupResponse {
  @NotBlank String dn;
  @NotBlank String name;
  String description;
  int totalMembers;
  boolean selectable;
  String message;
  @Default Collection<LdapUserResponse> users = Collections.emptyList();
}
