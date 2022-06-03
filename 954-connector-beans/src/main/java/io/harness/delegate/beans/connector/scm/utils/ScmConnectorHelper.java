package io.harness.delegate.beans.connector.scm.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ScmConnectorHelper {
  public static void validateGetFileUrlParams(String branchName, String filePath) {
    if (isEmpty(branchName)) {
      throw new InvalidRequestException("Branch name should not be empty or null.");
    }
    if (isEmpty(filePath)) {
      throw new InvalidRequestException("File path should not ne empty or null.");
    }
  }
}
