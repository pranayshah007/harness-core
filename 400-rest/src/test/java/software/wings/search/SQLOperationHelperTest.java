/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SQLOperationHelperTest extends WingsBaseTest {
  private Map<String, Object> columnValueMapping = new HashMap<>();
  private String tableName = "cg_cloud_providers";

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testInsertSQL() {
    columnValueMapping.put("name", "Casey O'Kane");
    columnValueMapping.put("age", "81");
    String actual = SQLOperationHelper.insertSQL(tableName, columnValueMapping);
    assertThat(actual).isEqualTo("INSERT INTO cg_cloud_providers (name,age) VALUES('Casey O''Kane','81')");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testUpdateSQL() {
    columnValueMapping.put("name", "Casey O'Kane");
    columnValueMapping.put("age", "81");
    String actual = SQLOperationHelper.updateSQL(tableName, columnValueMapping, new HashMap<>(), Arrays.asList("id"));
    assertThat(actual).isEqualTo(
        "INSERT INTO cg_cloud_providers (name,age) VALUES('Casey O''Kane','81') ON CONFLICT (id) Do UPDATE  SET name='Casey O''Kane',age='81'");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testDeleteSQL() {
    columnValueMapping.put("name", "Casey O'Kane");
    columnValueMapping.put("age", "81");
    String actual = SQLOperationHelper.deleteSQL(tableName, columnValueMapping);
    assertThat(actual).isEqualTo("DELETE FROM cg_cloud_providers WHERE name='Casey O''Kane' AND age='81'");
  }
}
