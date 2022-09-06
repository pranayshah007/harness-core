/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.codec.digest.DigestUtils;

@OwnedBy(PL)
@Slf4j
@Singleton
@TargetModule(_950_NG_SIGNUP)
public class PwnedPasswordChecker {
  private volatile OkHttpClient httpClient;
  private static final String BASE_API_URL = "https://api.pwnedpasswords.com/range/";
  private static final String RESPONSE_DELIMITER = ":";
  private static final int CONNECTION_TIMEOUT_SECONDS = 5;
  private static final int READ_TIMEOUT_SECONDS = 5;
  private static final int SHA1_PASS_PREFIX_LENGTH = 5;
  private static final int MIN_OCCURENCES = 5;
  private static final int SHA1_LENGTH = 40;

  public PwnedPasswordChecker() {}

  boolean checkIfPwned(char[] password) throws IOException {
    if (password.length == 0) {
      throw new InvalidArgumentsException("Password cannot by empty", USER);
    }

    String sha1password = DigestUtils.sha1Hex(String.valueOf(password));
    String sha1PasswordPrefix = sha1password.substring(0, SHA1_PASS_PREFIX_LENGTH);
    String sha1PasswordSuffix = sha1password.substring(SHA1_PASS_PREFIX_LENGTH, SHA1_LENGTH);
    String[] potentialMatches = getPotentialMatches(sha1PasswordPrefix);
    Stream<String> trimmedPotentialMatches =
        Arrays.stream(potentialMatches)
            .filter(s -> Integer.parseInt(s.split(RESPONSE_DELIMITER)[1]) > MIN_OCCURENCES)
            .map(s -> s.split(RESPONSE_DELIMITER)[0]);
    return trimmedPotentialMatches.anyMatch(sha1PasswordSuffix::equalsIgnoreCase);
  }

  /**
   * @param sha1PasswordPrefix The first five characters of the sha1 of the password.
   * @return Array of the last 35 characters of the sha1 of the matched passwords and their occurrences.
   *         An example return array would be somewhat like this
   *         00FE1ADAEE5558382B791DB7C31CC84008D:2
   *         01164BBFE5C5DA8863F844E3FFA86610F4E:1
   *         013B5D57AFF7C1242D186BEF14CBDC88E2A:1
   * @throws IOException when the connection times out or the read from the request times out.
   */
  private String[] getPotentialMatches(String sha1PasswordPrefix) throws IOException {
    Request httpGetRequest =
        new Request.Builder().url(String.format("%s%s", BASE_API_URL, sha1PasswordPrefix)).method("GET", null).build();
    try (Response response = getHttpClient().newCall(httpGetRequest).execute()) {
      if (response != null && response.isSuccessful()) {
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
          return responseBody.string().split("[\\r\\n]+");
        }
        return new String[0];
      }
    }
    return new String[0];
  }

  private OkHttpClient getHttpClient() {
    if (httpClient == null) {
      httpClient =
          Http.getSafeOkHttpClientBuilder(BASE_API_URL, CONNECTION_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS).build();
    }
    return httpClient;
  }
}
