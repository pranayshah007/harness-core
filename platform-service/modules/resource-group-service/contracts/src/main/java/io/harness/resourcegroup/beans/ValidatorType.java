/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.beans;

public enum ValidatorType {
  BY_RESOURCE_IDENTIFIER,
  BY_RESOURCE_TYPE,
  BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES,
  BY_ATTRIBUTE_INCLUDING_CHILD_SCOPES,
  BY_ATTRIBUTE
}
