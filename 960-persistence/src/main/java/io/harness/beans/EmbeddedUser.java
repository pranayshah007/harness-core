/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldNameConstants(innerTypeName = "EmbeddedUserKeys")
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("embeddedUser")
public class EmbeddedUser {
  private String uuid;
  private String name;
  private String email;
  private String externalUserId;

  @JsonIgnore
  public boolean existNameAndEmail() {
    return !isEmpty(this.getEmail()) && !isEmpty(this.getName());
  }
}
