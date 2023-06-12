/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;

import java.io.IOException;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testValidUrl() {
    assertThat(Http.validUrl("http://localhost")).isTrue();
    assertThat(Http.validUrl("https://localhost")).isTrue();
    assertThat(Http.validUrl("http://localhost/")).isTrue();
    assertThat(Http.validUrl("https://localhost/")).isTrue();
    assertThat(Http.validUrl("http://localhost.com")).isTrue();
    assertThat(Http.validUrl("https://localhost.com")).isTrue();
    assertThat(Http.validUrl("http://127.0.0.1")).isTrue();
    assertThat(Http.validUrl("https://127.0.0.1")).isTrue();
    assertThat(Http.validUrl("http://google.com")).isTrue();
    assertThat(Http.validUrl("https://google.com")).isTrue();
    assertThat(Http.validUrl("http://shortenedUrl")).isTrue();
    assertThat(Http.validUrl("https://shortenedUrl/")).isTrue();
    assertThat(Http.validUrl("http://toli:123")).isTrue();

    assertThat(Http.validUrl("invalidUrl")).isFalse();
    assertThat(Http.validUrl("invalidUrl")).isFalse();
    assertThat(Http.validUrl("abc://invalid.com")).isFalse();
    assertThat(Http.validUrl("abc://invalid.com")).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testShouldUseNonProxy() {
    assertThat(Http.shouldUseNonProxy("http://wings.jenkins.com", "*.jenkins.com|*.localhost|*.sumologic.com"))
        .isTrue();
    assertThat(
        Http.shouldUseNonProxy("http://wings.jenkins.com", "*wings.jenkins.com|*.localhost|*wings.sumologic.com"))
        .isTrue();
    assertThat(Http.shouldUseNonProxy("http://wings.jenkins.com:80", "*.jenkins.com|*localhost.com|*.sumologic.com"))
        .isTrue();
    assertThat(Http.shouldUseNonProxy("http://wings.jenkins.com", "*localhost.com|*.sumologic.com")).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetDomain() {
    assertThat(Http.getDomain("http://localhost.com/temp")).isEqualTo("localhost.com");
    assertThat(Http.getDomain("https://localhost.com/temp")).isEqualTo("localhost.com");
    assertThat(Http.getDomain("localhost.com:8080/temp")).isEqualTo("localhost.com");
    assertThat(Http.getDomain("localhost.com:8080")).isEqualTo("localhost.com");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetDomainWithPort() {
    assertThat(Http.getDomainWithPort("http://localhost.com/temp")).isEqualTo("localhost.com");
    assertThat(Http.getDomainWithPort("http://localhost.com/")).isEqualTo("localhost.com");
    assertThat(Http.getDomainWithPort("https://localhost.com/temp")).isEqualTo("localhost.com");
    assertThat(Http.getDomainWithPort("http://localhost.com:5000/temp")).isEqualTo("localhost.com:5000");
    assertThat(Http.getDomainWithPort("localhost.com:8080/temp")).isEqualTo("localhost.com:8080");
    assertThat(Http.getDomainWithPort("localhost.com:8080")).isEqualTo("localhost.com:8080");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    assertThat(Http.getBaseUrl("http://localhost.com/temp")).isEqualTo("http://localhost.com/");
    assertThat(Http.getBaseUrl("http://localhost.com/")).isEqualTo("http://localhost.com/");
    assertThat(Http.getBaseUrl("https://localhost.com/temp")).isEqualTo("https://localhost.com/");
    assertThat(Http.getBaseUrl("http://localhost.com:5000/temp")).isEqualTo("http://localhost.com:5000/");
    assertThat(Http.getBaseUrl("localhost.com:8080/temp")).isEqualTo("http://localhost.com:8080/");
    assertThat(Http.getBaseUrl("https://localhost.com:8443")).isEqualTo("https://localhost.com:8443/");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testJoinHostPort() {
    assertThat(Http.joinHostPort("localhost", "443")).isEqualTo("localhost:443");
    assertThat(Http.joinHostPort("127.0.0.1", "443")).isEqualTo("127.0.0.1:443");
    assertThat(Http.joinHostPort("2001:0db8:85a3:0000:0000:8a2e:0370:7334", "443"))
        .isEqualTo("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:443");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void concurrencyTest() {
    Concurrent.test(5, i -> { OkHttpClient client = Http.getUnsafeOkHttpClient("https://harness.io"); });
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testConnectableHttpUrl() throws IOException {
    assertThat(Http.checkResponseCode(200, false)).isTrue();
    assertThat(Http.checkResponseCode(400, false)).isFalse();
    assertThat(Http.checkResponseCode(500, false)).isTrue();
  }
}
