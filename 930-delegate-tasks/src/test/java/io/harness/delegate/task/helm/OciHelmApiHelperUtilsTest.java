/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.OciHelmDockerApiException;
import io.harness.rule.Owner;

import java.net.URISyntaxException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(CDP)
public class OciHelmApiHelperUtilsTest extends CategoryTest {
  @InjectMocks private OciHelmApiHelperUtils ociHelmApiHelperUtils = spy(OciHelmApiHelperUtils.class);

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testNormalizeFieldData() {
    String normalizedData = ociHelmApiHelperUtils.normalizeFieldData("test");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("/test");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("/test/");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("test/");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("//test/");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("//test//");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("//test///");
    assertThat(normalizedData).isEqualTo("test");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("/");
    assertThat(normalizedData).isEmpty();

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData("");
    assertThat(normalizedData).isEqualTo("");

    normalizedData = ociHelmApiHelperUtils.normalizeFieldData(null);
    assertThat(normalizedData).isEqualTo(null);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testNormalizeUrl() throws URISyntaxException {
    String normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("https://test.registry.io");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("https://test.registry.io/");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("http://test.registry.io");
    assertThat(normalizedUrl).isEqualTo("http://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("test.registry.io");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("oci://test.registry.io");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("test://test.registry.io");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("oci://test.registry.io:345");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io:345");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("oci://test.registry.io:123/asd/qwe/ert");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io:123");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("oci://test.registry.io/4gdfvs/dcacqwdq");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("test.registry.io/4gdfvs/dcacqwdq");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("https://test.registry.io/asffa/asfasfa");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");

    assertThatThrownBy(() -> ociHelmApiHelperUtils.normalizeUrl(""))
        .isInstanceOf(OciHelmDockerApiException.class)
        .hasMessage("Hostname provided in URL field of OCI Helm connector is invalid");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("/");
    assertThat(normalizedUrl).isEqualTo("https:///");

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("asd");
    assertThat(normalizedUrl).isEqualTo("https://asd");

    assertThatThrownBy(() -> ociHelmApiHelperUtils.normalizeUrl("https://")).isInstanceOf(URISyntaxException.class);

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("https://a");
    assertThat(normalizedUrl).isEqualTo("https://a");

    assertThatThrownBy(() -> ociHelmApiHelperUtils.normalizeUrl("https:")).isInstanceOf(URISyntaxException.class);

    normalizedUrl = ociHelmApiHelperUtils.normalizeUrl("test.registry.io/");
    assertThat(normalizedUrl).isEqualTo("https://test.registry.io");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testFormatChartNameWithUrlPath() throws URISyntaxException {
    String chartName = "chartName";

    String url1 = "test.io/path1/path2";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url1, chartName)).isEqualTo("path1/path2/chartName");

    String url2 = "https://test.io/path1/path2";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url2, chartName)).isEqualTo("path1/path2/chartName");

    String url3 = "oc://test.io/path1/path2";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url3, chartName)).isEqualTo("path1/path2/chartName");

    String url4 = "test.io/path1/path2/";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url4, chartName)).isEqualTo("path1/path2/chartName");

    String url5 = "https://test.io/path1/path2/";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url5, chartName)).isEqualTo("path1/path2/chartName");

    String url6 = "https://test.io";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url6, chartName)).isEqualTo("chartName");

    String url7 = "oc://test.io";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url7, chartName)).isEqualTo("chartName");

    String url8 = "oc://test.io/";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url8, chartName)).isEqualTo("chartName");

    String url9 = "test.io/";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url9, chartName)).isEqualTo("chartName");

    String url10 = "invalid_url:";
    assertThatThrownBy(() -> ociHelmApiHelperUtils.formatChartNameWithUrlPath(url10, chartName))
        .isInstanceOf(URISyntaxException.class);

    String url11 = "oci://test.registry.io:123/path1/path2";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url11, chartName)).isEqualTo("path1/path2/chartName");

    String url12 = "oci://test.registry.io:123//path1/path2";
    assertThat(ociHelmApiHelperUtils.formatChartNameWithUrlPath(url12, chartName)).isEqualTo("path1/path2/chartName");
  }
}
