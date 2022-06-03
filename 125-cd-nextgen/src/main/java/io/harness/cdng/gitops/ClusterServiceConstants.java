package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(GITOPS)
@UtilityClass
public class ClusterServiceConstants {
  static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Cluster [%s] under Project[%s], Organization [%s] in Account [%s] already exists";
  static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Cluster [%s] under Organization [%s] in Account [%s] already exists";
  static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT = "Cluster [%s] in Account [%s] already exists";
  static final String CLUSTER_DOES_NOT_EXIST = "Cluster [%s] under Project[%s], Organization [%s] does not exist";
}
