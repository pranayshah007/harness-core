// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"os"
	"testing"
)

func TestGetContainerPort(t *testing.T) {

	tests := []struct {
		name    string
		expOut  uint
		step    *pb.UnitStep
		envVars map[string]string
	}{
		{
			name:   "Test get port from env var",
			expOut: 20000,
			step: &pb.UnitStep{
				Id:            "testId",
				ContainerPort: 10000,
				TaskId:        "taskID",
			},
			envVars: map[string]string{
				"taskID_SERVICE_PORT": "20000",
			},
		},
		{
			name:   "Test get port from step",
			expOut: 10000,
			step: &pb.UnitStep{
				Id:            "testId",
				ContainerPort: 10000,
				TaskId:        "taskID",
			},
		},
		{
			name:   "Test invalid env var",
			expOut: 10000,
			step: &pb.UnitStep{
				Id:            "testId",
				ContainerPort: 10000,
				TaskId:        "taskID",
			},
			envVars: map[string]string{
				"taskID_SERVICE_PORT": "invalid",
			},
		},
	}
	for _, test := range tests {
		// Set Env vars for test
		if test.envVars != nil {
			for k, v := range test.envVars {
				if err := os.Setenv(k, v); err != nil {
					t.Fatalf("%s: failed to set environment variable: %s, %s", test.name, k, v)
				}
			}
		}

		got := GetContainerPort(test.step)

		if test.expOut != got {
			t.Errorf("%s: expected: %d, but got: %d", test.name, test.expOut, got)
		}

		// Unset env vars for test
		if test.envVars != nil {
			for k := range test.envVars {
				if err := os.Unsetenv(k); err != nil {
					t.Fatalf("%s: failed to unset environment variable: %s", test.name, k)
				}
			}
		}
	}
}
