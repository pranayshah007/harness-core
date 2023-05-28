/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.http;

import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HttpCertificate;
import io.harness.beans.HttpCertificateNG;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class HttpTaskNGTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetHttpCertificateIfCertificateNotSet() {
    HttpTaskNG httpTaskNG = mock(HttpTaskNG.class);
    HttpTaskParametersNg httpTaskParametersNg = HttpTaskParametersNg.builder().build();
    HttpCertificate httpCertificate = httpTaskNG.getHttpCertificate(httpTaskParametersNg);
    assertThat(httpCertificate).isNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetHttpCertificateIfCertificateSet() {
    HttpTaskNG httpTaskNG = mock(HttpTaskNG.class);
    HttpTaskParametersNg httpTaskParametersNg =
        HttpTaskParametersNg.builder()
            .certificateNG(
                HttpCertificateNG.builder().certificate("certificate").certificateKey("certificateKey").build())
            .build();
    HttpCertificate httpCertificate = httpTaskNG.getHttpCertificate(httpTaskParametersNg);
    assertThat(httpCertificate).isNotNull();
    assertThat(httpCertificate.getCert()).isNotNull();
  }
}