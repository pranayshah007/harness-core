// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"bufio"
	"fmt"
	"testing"

	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestFetchOutputVars(t *testing.T) {
	config := zap.NewProductionConfig()
	log, _ := config.Build()
	fs := filesystem.NewOSFileSystem(log.Sugar())

	tests := []struct {
		Name       string
		OutputFile string
		EnvMap     map[string]string
		Error      error
	}{
		{
			Name:       "env_variable_long",
			OutputFile: "testdata/long_output.txt",
			EnvMap:     nil,
			Error:      fmt.Errorf("output variable length is more than %d bytes", bufio.MaxScanTokenSize),
		},
		{
			Name:       "env_variable_short",
			OutputFile: "testdata/short_output.txt",
			EnvMap:     map[string]string{"SHORT_ENV_VAR": "value"},
			Error:      nil,
		},
	}

	for _, tc := range tests {
		t.Run(tc.Name, func(t *testing.T) {
			envMap, err := fetchOutputVariables(tc.OutputFile, fs, log.Sugar())
			assert.Equal(t, tc.EnvMap, envMap)
			assert.Equal(t, tc.Error, err)
		})
	}
}
