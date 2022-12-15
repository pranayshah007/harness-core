/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import net.sf.json.JSONObject;

@Data
@FieldNameConstants(innerTypeName = "JiraStatusCategoryKeys")
public class JiraStatusCategory {
  private String id;
  private String key;
  private String name;

  public JiraStatusCategory(JSONObject jsonObject) {
    this.id = jsonObject.getString(JiraStatusCategoryKeys.id);
    this.key = jsonObject.getString(JiraStatusCategoryKeys.key);
    this.name = jsonObject.getString(JiraStatusCategoryKeys.name);
  }

  public JiraStatusCategory(JiraStatusCategoryNG jiraStatusCategoryNG) {
    this.id = jiraStatusCategoryNG.getId().toString();
    this.key = jiraStatusCategoryNG.getKey();
    this.name = jiraStatusCategoryNG.getName();
  }
}
