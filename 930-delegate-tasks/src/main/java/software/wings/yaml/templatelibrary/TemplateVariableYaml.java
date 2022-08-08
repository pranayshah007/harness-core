/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.templatelibrary;

import lombok.Builder;
import lombok.Data;

/**
 * @author abhinav
 */

@Data
@Builder
public class TemplateVariableYaml {
  private String name;
  private String description;
  private String value;
}
