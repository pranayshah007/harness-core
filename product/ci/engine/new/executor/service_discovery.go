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
	"strings"
)

func GetContainerPort(step *pb.UnitStep) uint {
	// env var name can't start with a digit, the validation regex is [-._a-zA-Z][-._a-zA-Z0-9]*
	normalizedTaskId :=strings.TrimLeft(step.GetTaskId(), "0123456789")
	portEnvVar := fmt.Sprintf("%s_SERVICE_PORT", normalizedTaskId)
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
