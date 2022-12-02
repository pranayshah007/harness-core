package io.harness.logging;

import static io.harness.expression.SecretString.SECRET_MASK;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogSanitizerHelper {
  public static final String JWT_REGEX = "[\\w-]*\\.[\\w-]*\\.[\\w-]*";
  public static final Pattern pattern = Pattern.compile(JWT_REGEX);
  public static String sanitizeJWT(String message) {
    String finalMessage = message;
    List<String> regexMatches = pattern.matcher(message)
                                    .results()
                                    .map(matchResult -> finalMessage.substring(matchResult.start(), matchResult.end()))
                                    .collect(Collectors.toList());
    for (String regexMatch : regexMatches) {
      try {
        JWT.decode(regexMatch);
        message = message.replaceAll(regexMatch, SECRET_MASK);
      } catch (JWTDecodeException ignored) {
      } catch (Exception ex) {
        log.error("Error while trying to decode JWT", ex);
      }
    }
    return message;
  }
}
