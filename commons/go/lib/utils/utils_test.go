// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package utils

import (
	"github.com/harness/ti-client/types"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func Test_Ms(t *testing.T) {
	expected := float64(60000)
	assert.Equal(t, expected, Ms(time.Minute))

	expected = float64(1000)
	assert.Equal(t, expected, Ms(time.Second))
}

func Test_NoOp(t *testing.T) {
	assert.Equal(t, nil, NoOp())
}

func Test_ParseJavaNode(t *testing.T) {
	tests := []struct {
		name     string
		file 	 types.File
		node     Node
	}{
		{
			name:     "ParseJavaNode_JavaSourceFile",
			file: types.File{
				Name: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_JavaSourceFile_BadPath",
			file: types.File{
				Name: "320-ci-execution/src/main/java/io/harness/stateutils/ConnectorUtils.java",
				Package: "io.harness.stateutils.buildstate",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_JavaTestFile",
			file: types.File{
				Name: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/ConnectorUtilsTest.java",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_JavaTestFile_BadPath",
			file: types.File{
				Name: "320-ci-execution/src/test/java/io/harness/buildstate/ConnectorUtilsTest.java",
				Package: "io.harness.stateutils.buildstate",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_JavaResourceFile",
			file: types.File{
				Name: "320-ci-execution/src/test/resources/all.json",
			},
			node: Node{
				Type: NodeType_RESOURCE,
				Lang: LangType_JAVA,
				File: "all.json",
			},
		},
		{
			name:     "ParseJavaNode_ScalaSourceFile",
			file: types.File{
				Name: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.scala",
			},
			node: Node{
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_ScalaSourceFile_WithPkg",
			file: types.File{
				Name: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.scala",
				Package: "io.harness.stateutils.buildstate",
			},
			node: Node{
				Pkg: "io.harness.stateutils.buildstate",
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_ScalaTestFile_ScalaTestPath",
			file: types.File{
				Name: "320-ci-execution/src/test/scala/io/harness/stateutils/buildstate/ConnectorUtilsTest.scala",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_ScalaTestFile_ScalaTestPath_WithPkg",
			file: types.File{
				Name: "320-ci-execution/src/test/scala/io/harness/stateutils/ConnectorUtilsTest.scala",
				Package: "io.harness.stateutils.buildstate",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_ScalaTestFile_JavaTestPath",
			file: types.File{
				Name: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/ConnectorUtilsTest.scala",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinSourceFile",
			file: types.File{
				Name: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.kt",
			},
			node: Node{
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinSourceFile_WithPkg",
			file: types.File{
				Name: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.kt",
				Package: "io.harness.stateutils.buildstate",
			},
			node: Node{
				Class: "ConnectorUtils",
				Pkg: "io.harness.stateutils.buildstate",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinTestFile_KotlinTestPath",
			file: types.File{
				Name: "320-ci-execution/src/test/kotlin/io/harness/stateutils/buildstate/ConnectorUtilsTest.kt",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinTestFile_JavaTestPath",
			file: types.File{
				Name: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/ConnectorUtilsTest.kt",
			},
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			n, _ := ParseJavaNode(tt.file)
			assert.Equal(t, tt.node, *n, "extracted java node does not match")
		})
	}
}

func Test_ParseFileNames(t *testing.T) {

	files := []types.File{
		{
			Name: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java", // Source file
		},
		{
			Name: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/TestConnectorUtils.java", // Test file
		},
		{
			Name: "810-ci-manager/src/test/resources/data/ng-trigger-config.yaml",                           // Resource file
		},
		{
			Name: "332-ci-manager/pom.xml",
		},
		{
			Name:"320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils", //.java extension is missing
		},
		{
			Name: "320-ci-execution/src/main/java/io/harness/stateutils/ConnectorUtils.java", // Source file with different patb
			Package: "io.harness.stateutils.buildstate",
		},


	}
	node1 := Node{
		Pkg:   "io.harness.stateutils.buildstate",
		Class: "ConnectorUtils",
		Type:  NodeType_SOURCE,
		Lang:  LangType_JAVA,
	}

	node2 := Node{
		Pkg:   "io.harness.stateutils.buildstate",
		Class: "TestConnectorUtils",
		Type:  NodeType_TEST,
		Lang:  LangType_JAVA,
	}

	node3 := Node{
		Type: NodeType_RESOURCE,
		Lang: LangType_JAVA,
		File: "ng-trigger-config.yaml",
	}

	unknown := Node{
		Type: NodeType_OTHER,
		Lang: LangType_UNKNOWN,
	}

	nodes, _ := ParseFileNames(files)

	nodesExpected := []Node{node1, node2, node3, unknown, unknown, node1}

	assert.Equal(t, nodesExpected, nodes, "extracted nodes don't match")
}
