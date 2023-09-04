# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#USAGE: This file is getting reffered in the WORKSPACE file and controls the URLs of artifactory as per the Build Env.
#For Local and CI Builds in GKE -> GKE hosted artifactory will be selected.
#For CI Builds in Hosted Machines -> OVH hosted artifactory will be selected.

REPOSITORY = "harness-artifactory"
