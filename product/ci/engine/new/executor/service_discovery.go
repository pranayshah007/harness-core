// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"fmt"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"os"
	"strconv"
)

func GetContainerPort(step *pb.UnitStep) uint {
	portEnvVar := fmt.Sprintf("%s_SERVICE_PORT", step.GetTaskId())
	env, ok := os.LookupEnv(portEnvVar)
	if !ok {
		return uint(step.GetContainerPort())
	}
	intEnv, err := strconv.ParseUint(env, 10, 32)
	if err != nil {
		return uint(step.GetContainerPort())
	}
	return uint(intEnv)
}
