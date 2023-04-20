/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.licensing;

import static io.harness.rule.OwnerRule.JAMIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.beans.licensing.api.CILicenseHistoryDTO;
import io.harness.beans.licensing.api.CILicenseType;
import io.harness.beans.licensing.api.CILicenseUsageDTO;
import io.harness.category.element.UnitTests;
import io.harness.licensing.CILicenseUsageImpl;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

public class CILicenseUsageTest extends CategoryTest {
  private static final long DAY_IN_MS = 60 * 60 * 1000 * 24L;
  @Mock TimeScaleDBService timeScaleDBService;
  ResultSet resultSet = mock(ResultSet.class);
  Connection connection = mock(Connection.class);
  PreparedStatement statement = mock(PreparedStatement.class);
  @InjectMocks @Spy private CILicenseUsageImpl ciLicenseUsage;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(connection.prepareStatement(any())).thenReturn(statement);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetLicenseHistoryUsage() throws SQLException {
    final int[] count = {0};
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] <= 30) {
        count[0]++;
        return true;
      }
      return false;
    });
    when(resultSet.getInt("total")).then((Answer<Integer>) invocation -> count[0]);

    Map<String, Integer> expectedMap = new HashMap<>();
    long time = System.currentTimeMillis();
    for (int i = 0; i < 30; i++) {
      long cursorTime = time - i * DAY_IN_MS;
      SimpleDateFormat dmyFormat = new SimpleDateFormat("yyyy-MM-dd");
      String date = dmyFormat.format(new Date(cursorTime));
      expectedMap.put(date, i + 1);
    }
    CILicenseHistoryDTO result = ciLicenseUsage.getLicenseHistoryUsage("accountId", CILicenseType.DEVELOPERS, null);
    assertThat(CILicenseType.DEVELOPERS).isEqualTo(result.getLicenseType());
    assertThat(result.getLicenseUsage()).isEqualTo(expectedMap);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetDeveloperList() throws SQLException {
    final int[] count = {0};
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] <= 30) {
        count[0]++;
        return true;
      }
      return false;
    });
    when(resultSet.getString("moduleinfo_author_id")).then((Answer<String>) invocation -> "developerID" + count[0] % 3);

    long time = System.currentTimeMillis();
    Set<String> expected = Set.of("developerID0", "developerID1", "developerID2");

    Set<String> result = ciLicenseUsage.listActiveDevelopers("accountId", time);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetLicenseUsage() throws SQLException {
    final int[] count = {0};
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] <= 2) {
        count[0]++;
        return true;
      }
      return false;
    });
    when(resultSet.getString("moduleinfo_author_id")).then((Answer<String>) invocation -> "developerID" + count[0] % 2);
    when(resultSet.getString("projectidentifier")).then((Answer<String>) invocation -> "project" + count[0]);
    when(resultSet.getString("orgidentifier")).then((Answer<String>) invocation -> "org" + count[0]);

    long time = System.currentTimeMillis();
    ReferenceDTO user0 = ReferenceDTO.builder()
                             .count(1)
                             .identifier("developerID1")
                             .projectIdentifier("project1")
                             .orgIdentifier("org1")
                             .accountIdentifier("accountId")
                             .build();
    ReferenceDTO user1 = ReferenceDTO.builder()
                             .count(1)
                             .identifier("developerID0")
                             .projectIdentifier("project2")
                             .orgIdentifier("org2")
                             .accountIdentifier("accountId")
                             .build();
    ReferenceDTO user2 = ReferenceDTO.builder()
                             .count(1)
                             .identifier("developerID1")
                             .projectIdentifier("project3")
                             .orgIdentifier("org3")
                             .accountIdentifier("accountId")
                             .build();

    CILicenseUsageDTO result = ciLicenseUsage.getLicenseUsage("accountId", ModuleType.CI, time, null);
    assertThat(result.getActiveCommitters().getCount()).isEqualTo(2);
    assertThat(result.getActiveCommitters().getReferences().size()).isEqualTo(3);
    assertThat(result.getActiveCommitters().getReferences()).isEqualTo(List.of(user0, user1, user2));
    assertThat(result.getCiLicenseType()).isEqualTo(CILicenseType.DEVELOPERS);
  }
}
