/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
@TargetModule(_950_NG_SIGNUP)
public class PwnedPasswordCheckerTest extends WingsBaseTest {
  @Inject private PwnedPasswordChecker pwnedPasswordChecker;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCheckIfPwned() throws IOException, IllegalAccessException {
    String dummyPassword = "password";
    String mockResponse = "1D2DA4053E34E76F6576ED1DA63134B5E2A:2\r\n"
        + "1D72CD07550416C216D8AD296BF5C0AE8E0:10\r\n"
        + "1E2AAA439972480CEC7F16C795BBB429372:1\r\n"
        + "1E3687A61BFCE35F69B7408158101C8E414:1\r\n"
        + "1E4C9B93F3F0682250B6CF8331B7EE68FD8:3730471\r\n"
        + "1F2B668E8AABEF1C59E9EC6F82E3F3CD786:1\r\n"
        + "20597F5AC10A2F67701B4AD1D3A09F72250:3\r\n"
        + "20AEBCE40E55EDA1CE07D175EC293150A7E:1\r\n"
        + "20FFB975547F6A33C2882CFF8CE2BC49720:1\r\n"
        + "21901C19C92442A5B1C45419F7887722FCF:1\r\n"
        + "22158C3C153B18E085F0AE99105605AA1F3:3\r\n"
        + "2274735627699B58FFF7728CB090A819AF6:1";
    byte[] mockResponseBytes = mockResponse.getBytes(Charset.forName("UTF-8"));

    OkHttpClient mockHttpClient = mock(OkHttpClient.class);
    Call call = mock(Call.class);
    Request request = new Request.Builder().url("https://dummyurl.com").method("GET", null).build();
    Response response = new Response.Builder()
                            .code(200)
                            .body(ResponseBody.create(MediaType.parse("text/plain"), mockResponseBytes))
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .message("OK")
                            .build();

    when(mockHttpClient.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(response);

    FieldUtils.writeField(pwnedPasswordChecker, "httpClient", mockHttpClient, true);
    boolean isPasswordPwned = pwnedPasswordChecker.checkIfPwned(dummyPassword.toCharArray());
    assertThat(isPasswordPwned).isTrue();
  }
}
