/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
@FieldNameConstants(innerTypeName = "ScimGroupKeys")
public class ScimGroup extends ScimBaseResource {
  private Set<String> schemas = new HashSet<>(Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:Group"));
  private String displayName;
  private List<Member> members;
  private String harnessScopes;
}
