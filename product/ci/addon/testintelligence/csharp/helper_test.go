// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package csharp

import (
	"testing"

	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/ti-client/types"
	"github.com/stretchr/testify/assert"
)

func Test_ParseCsharpNode(t *testing.T) {
	testGlobs := []string{"path/to/test*/*.cs"}
	testCases := []struct {
		// Input
		FileName string
		TestGlob []string
		// Verify
		Class    string
		NodeType utils.NodeType
	}{
		{"path/to/test1/t1.cs", testGlobs, "t1", utils.NodeType_TEST},
		{"path/to/test2/t2.cs", testGlobs, "t2", utils.NodeType_TEST},
		{"path/to/test3/t3.cs", testGlobs, "t3", utils.NodeType_TEST},
		{"path/to/test4/t4.cs", testGlobs, "t4", utils.NodeType_TEST},
		{"path/to/src1/s1.cs", testGlobs, "s1", utils.NodeType_SOURCE},
	}
	for _, tc := range testCases {
		f := types.File{Name: tc.FileName}
		n, _ := utils.ParseCsharpNode(f, tc.TestGlob)
		assert.Equal(t, n.Class, tc.Class)
		assert.Equal(t, tc.NodeType, n.Type)
	}
}
