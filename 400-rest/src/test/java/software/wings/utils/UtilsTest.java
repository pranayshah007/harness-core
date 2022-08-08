/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.KAMAL;

import static software.wings.utils.Utils.escapifyString;
import static software.wings.utils.Utils.getNameWithNextRevision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.NameValuePair;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testToProperties() {
    List<NameValuePair> nameValuePairList = new ArrayList<>();
    nameValuePairList.add(NameValuePair.builder().name("n1").value("v1").build());
    nameValuePairList.add(NameValuePair.builder().name("n2").value("v2").build());
    nameValuePairList.add(NameValuePair.builder().name("n3").value(null).build());

    Map map = Utils.toProperties(nameValuePairList);
    assertThat(map).isNotNull();
    assertThat(map.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testEscapifyString() {
    assertThat(escapifyString("ab\\")).isEqualTo("ab\\\\");
    assertThat(escapifyString("ab\\cd")).isEqualTo("ab\\cd");
    assertThat(escapifyString("a\"b")).isEqualTo("a\\\"b");
    assertThat(escapifyString("a'b")).isEqualTo("a'b");
    assertThat(escapifyString("a`b")).isEqualTo("a\\`b");
    assertThat(escapifyString("a(b")).isEqualTo("a(b");
    assertThat(escapifyString("a)b")).isEqualTo("a)b");
    assertThat(escapifyString("a|b")).isEqualTo("a|b");
    assertThat(escapifyString("a<b")).isEqualTo("a<b");
    assertThat(escapifyString("a>b")).isEqualTo("a>b");
    assertThat(escapifyString("a;b")).isEqualTo("a;b");
    assertThat(escapifyString("a b")).isEqualTo("a b");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void testGetNameWithNextRevision() {
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def"), "abc-def")).isEqualTo("abc-def-1");
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def-1", "abc-def"), "abc-def")).isEqualTo("abc-def-2");
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def-"), "abc-def-")).isEqualTo("abc-def--1");
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def-", "abc-def--1"), "abc-def-")).isEqualTo("abc-def--2");
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def-1"), "abc-def-1")).isEqualTo("abc-def-1-1");
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def-1", "abc-def-1-1"), "abc-def-1"))
        .isEqualTo("abc-def-1-2");
    assertThat(getNameWithNextRevision(ImmutableList.of("abc-def", "abc-def-2", "abc-def-3", "abc-def-1"), "abc-def"))
        .isEqualTo("abc-def-4");
    assertThat(getNameWithNextRevision(
                   ImmutableList.of("abc-def", "abc-def-2", "abc-def-3", "abc-def-1", "abc-def-5", "abc-def-6",
                       "abc-def-4", "abc-def-8", "abc-def-7", "abc-def-9", "abc-def-10", "abc-def-12", "abc-def-11"),
                   "abc-def"))
        .isEqualTo("abc-def-13");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testAppendPathToBaseUrl() {
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com", "hello")).isEqualTo("https://example.com/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com/", "hello")).isEqualTo("https://example.com/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com/", "/hello")).isEqualTo("https://example.com/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com:8080", "hello"))
        .isEqualTo("https://example.com:8080/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com:8080/", "hello"))
        .isEqualTo("https://example.com:8080/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com:8080/", "/hello"))
        .isEqualTo("https://example.com:8080/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com/path1/path2", "hello"))
        .isEqualTo("https://example.com/path1/path2/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com/path1/path2", "/hello"))
        .isEqualTo("https://example.com/path1/path2/hello");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com/path1/path2/", "/hello/"))
        .isEqualTo("https://example.com/path1/path2/hello/");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com/path1/path2/", "/hello/q=abc&abc=%20abc"))
        .isEqualTo("https://example.com/path1/path2/hello/q=abc&abc=%20abc");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com", "q=abc&abc=%20abc"))
        .isEqualTo("https://example.com/q=abc&abc=%20abc");
    assertThat(CVUtils.appendPathToBaseUrl("https://example.com", "")).isEqualTo("https://example.com/");
    assertThat(
        CVUtils.appendPathToBaseUrl("http://35.239.148.216:8080/",
            "/api/v1/query_range?start=1592243889&end=1592245089&step=60s&query=container_cpu_usage_seconds_total{container=\"harness-example\",pod=\"harness-example-prod-deployment-canary-7d458cfcb7-z95b6\"}"))
        .isEqualTo(
            "http://35.239.148.216:8080/api/v1/query_range?start=1592243889&end=1592245089&step=60s&query=container_cpu_usage_seconds_total{container=\"harness-example\",pod=\"harness-example-prod-deployment-canary-7d458cfcb7-z95b6\"}");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testEmptyIfNull() {
    assertThat(Utils.emptyIfNull(null)).isEqualTo("");
    assertThat(Utils.emptyIfNull("")).isEqualTo("");
    assertThat(Utils.emptyIfNull("value")).isEqualTo("value");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testSplitCommaSeparatedFilePath() {
    assertThatThrownBy(() -> Utils.splitCommaSeparatedFilePath(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Utils.splitCommaSeparatedFilePath("")).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> Utils.splitCommaSeparatedFilePath("null")).isInstanceOf(InvalidRequestException.class);
    assertThat(Utils.splitCommaSeparatedFilePath("file1")).hasSize(1);
    assertThat(Utils.splitCommaSeparatedFilePath("file1, file2")).hasSize(2);
    assertThat(Utils.splitCommaSeparatedFilePath(" file1 , file2 ")).containsExactlyInAnyOrder("file1", "file2");
    assertThat(Utils.splitCommaSeparatedFilePath(" p2/file1 , p1/file2 "))
        .containsExactlyInAnyOrder("p2/file1", "p1/file2");
    assertThat(Utils.splitCommaSeparatedFilePath(" file1 , file1 ")).containsExactlyInAnyOrder("file1", "file1");
  }
}
