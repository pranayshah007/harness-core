/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PemReaderTest {
  private final String pem = "-----BEGIN CERTIFICATE-----\n"
      + "MIIDDDCCAfQCCQDUIERPrOc3ZDANBgkqhkiG9w0BAQsFADBIMQswCQYDVQQGEwJV\n"
      + "UzETMBEGA1UECAwKQ2FsaWZvcm5pYTEQMA4GA1UECgwHSGFybmVzczESMBAGA1UE\n"
      + "AwwJbG9jYWxob3N0MB4XDTIyMTIwNjExMzExMFoXDTIzMTIwNjExMzExMFowSDEL\n"
      + "MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEDAOBgNVBAoMB0hhcm5l\n"
      + "c3MxEjAQBgNVBAMMCWxvY2FsaG9zdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\n"
      + "AQoCggEBALrdjfH9DV+mCQwvz90SOiwhb1quoaLKgRCNe5F2V8XZmanILZj1OHFK\n"
      + "UCBSzQrja7BXeT0OOK6rVc60IFjiPBTvKE1dSpy+09oqHQHqrUySbXxzgDvuu9k3\n"
      + "fe+A8tVG3j+KoAQ7RwGh3/EkPfKG1v3RnJaKGiF53F0VgbvOYk87zqLzPmvL27NV\n"
      + "bxcC24MGQRmAalHmRg6m4vSRK7syauQw3PLxv5IHi1oUWKU01GprYmOLZKVHRp7m\n"
      + "icVE+msg0SL3uRikUOpVrEmbEFpPqKJjczbdU4qG0lt9KwiXg85tyqwwvoDd2ffp\n"
      + "adMMkrzEULwxHhqPwtNP3pTs0p9tyJ8CAwEAATANBgkqhkiG9w0BAQsFAAOCAQEA\n"
      + "tzgAYECtdQKvO+y/cLpqAs0dyl7bhT1QpQ1vcNtbpR/Y3Qxp5xoGBoICpNgrvTmd\n"
      + "CSPe6ozEa7bGtUmY/20AgFGkfAAk93JhjBgs8G4Zjx2r+QA5f3Enty9NSiTEzEXh\n"
      + "4WaOijVbm/fwr0Ggzp4TfuhAo5fm5LfBfn30hIcD46Bw4AOlZi5Sa/0wJIILOoz3\n"
      + "wu4QsWlVY/X3cAXFi4wb+N35Glsr3kgU/tEtyTuNpnUnzd8Q0UTtyoJSJzHgxTkh\n"
      + "r6lnkb9MUPB/zLDjOSdY6g0ihPmNax31mcqbYXgO0O1wxkSa5Axpl11P4EicPkEY\n"
      + "hbZIVdfJfBGJPwQ76bFBdA==\n"
      + "-----END CERTIFICATE-----\n"
      + "-----BEGIN CERTIFICATE-----\n"
      + "MIIDDDCCAfQCCQDUIERPrOc3ZDANBgkqhkiG9w0BAQsFADBIMQswCQYDVQQGEwJV\n"
      + "UzETMBEGA1UECAwKQ2FsaWZvcm5pYTEQMA4GA1UECgwHSGFybmVzczESMBAGA1UE\n"
      + "AwwJbG9jYWxob3N0MB4XDTIyMTIwNjExMzExMFoXDTIzMTIwNjExMzExMFowSDEL\n"
      + "MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEDAOBgNVBAoMB0hhcm5l\n"
      + "c3MxEjAQBgNVBAMMCWxvY2FsaG9zdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\n"
      + "AQoCggEBALrdjfH9DV+mCQwvz90SOiwhb1quoaLKgRCNe5F2V8XZmanILZj1OHFK\n"
      + "UCBSzQrja7BXeT0OOK6rVc60IFjiPBTvKE1dSpy+09oqHQHqrUySbXxzgDvuu9k3\n"
      + "fe+A8tVG3j+KoAQ7RwGh3/EkPfKG1v3RnJaKGiF53F0VgbvOYk87zqLzPmvL27NV\n"
      + "bxcC24MGQRmAalHmRg6m4vSRK7syauQw3PLxv5IHi1oUWKU01GprYmOLZKVHRp7m\n"
      + "icVE+msg0SL3uRikUOpVrEmbEFpPqKJjczbdU4qG0lt9KwiXg85tyqwwvoDd2ffp\n"
      + "adMMkrzEULwxHhqPwtNP3pTs0p9tyJ8CAwEAATANBgkqhkiG9w0BAQsFAAOCAQEA\n"
      + "tzgAYECtdQKvO+y/cLpqAs0dyl7bhT1QpQ1vcNtbpR/Y3Qxp5xoGBoICpNgrvTmd\n"
      + "CSPe6ozEa7bGtUmY/20AgFGkfAAk93JhjBgs8G4Zjx2r+QA5f3Enty9NSiTEzEXh\n"
      + "4WaOijVbm/fwr0Ggzp4TfuhAo5fm5LfBfn30hIcD46Bw4AOlZi5Sa/0wJIILOoz3\n"
      + "wu4QsWlVY/X3cAXFi4wb+N35Glsr3kgU/tEtyTuNpnUnzd8Q0UTtyoJSJzHgxTkh\n"
      + "r6lnkb9MUPB/zLDjOSdY6g0ihPmNax31mcqbYXgO0O1wxkSa5Axpl11P4EicPkEY\n"
      + "hbZIVdfJfBGJPwQ76bFBdA==\n"
      + "-----END CERTIFICATE-----";

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldReadMultipleCertificates() throws CertificateException {
    ByteArrayInputStream in = new ByteArrayInputStream(pem.getBytes());
    final ByteArrayInputStream[] certs = PemReader.readCertificates(in);
    assertThat(certs).isNotEmpty();
    assertThat(certs).hasSize(2);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateCertificates() throws CertificateException {
    ByteArrayInputStream is = new ByteArrayInputStream(pem.getBytes());
    final X509Certificate[] certs = PemReader.getCertificates(is);
    assertThat(certs).isNotEmpty();
    assertThat(certs).hasSize(2);
  }
}
