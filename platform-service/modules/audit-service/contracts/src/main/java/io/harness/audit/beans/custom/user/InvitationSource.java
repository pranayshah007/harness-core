/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans.custom.user;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.beans.custom.user.InvitationSource.TYPE_NAME;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(TYPE_NAME)
@TypeAlias("InvitationSource")
public class InvitationSource extends Source {
  public static final String TYPE_NAME = "Invitation";

  @Builder
  public InvitationSource() {
    this.type = TYPE_NAME;
  }
}
