/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.servicenow;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

public enum ServiceNowFieldType {
  DATE_TIME(Arrays.asList("glide_date_time", "due_date", "glide_date", "glide_time")),
  INTEGER(Collections.singletonList("integer")),
  BOOLEAN(Collections.singletonList("boolean")),
  STRING(Collections.singletonList("string"));
  @Getter private List<String> snowInternalTypes;
  ServiceNowFieldType(List<String> types) {
    snowInternalTypes = types;
  }
}
