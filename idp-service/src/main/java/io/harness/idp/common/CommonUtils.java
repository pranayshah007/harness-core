package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CommonUtils {
  public static String removeAccountFromIdentifier(String identifier) {
    String[] arrOfStr = identifier.split("[.]");
    if (arrOfStr.length == 2 && arrOfStr[0].equals("account")) {
      return arrOfStr[1];
    }
    return arrOfStr[0];
  }
}
