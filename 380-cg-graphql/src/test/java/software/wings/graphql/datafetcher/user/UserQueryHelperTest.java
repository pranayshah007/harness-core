/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.graphql.datafetcher.user;

import static io.harness.rule.OwnerRule.RAFAEL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.User;
import software.wings.graphql.schema.type.aggregation.user.QLUserFilter;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class UserQueryHelperTest extends WingsBaseTest {
  @Inject private UserQueryHelper userQueryHelper;

  @Mock private Query<User> userQuery;

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldNotInvokeCriteriaWhenFilterIsNull() {
    QLUserFilter qlUserFilter = QLUserFilter.builder().build();
    List<QLUserFilter> qlUserFilterList = List.of(qlUserFilter);
    userQueryHelper.setShowDisabledFilter(qlUserFilterList, userQuery);

    verify(userQuery, times(0)).criteria(any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldNotInvokeCriteriaWhenFilterIncludeDisabledIsTrue() {
    QLUserFilter qlUserFilter = QLUserFilter.builder().includeDisabled(true).build();
    List<QLUserFilter> qlUserFilterList = List.of(qlUserFilter);
    userQueryHelper.setQuery(qlUserFilterList, userQuery);
    userQueryHelper.setShowDisabledFilter(qlUserFilterList, userQuery);

    verify(userQuery, times(0)).criteria(any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldNotInvokeCriteriaWhenFilterIncludeDisabledIsFalse() {
    QLUserFilter qlUserFilter = QLUserFilter.builder().includeDisabled(false).build();
    List<QLUserFilter> qlUserFilterList = List.of(qlUserFilter);

    FieldEnd fieldEndMock = mock(FieldEnd.class);
    doReturn(null).when(fieldEndMock).notEqual(true);
    doReturn(fieldEndMock).when(userQuery).criteria(any());

    userQueryHelper.setShowDisabledFilter(qlUserFilterList, userQuery);

    verify(userQuery, times(1)).criteria(any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldNotInvokeCriteriaWhenFilterIsEmpty() {
    FieldEnd fieldEndMock = mock(FieldEnd.class);
    doReturn(null).when(fieldEndMock).notEqual(true);
    doReturn(fieldEndMock).when(userQuery).criteria(any());

    userQueryHelper.setShowDisabledFilter(null, userQuery);

    verify(userQuery, times(1)).criteria(any());
  }
}
