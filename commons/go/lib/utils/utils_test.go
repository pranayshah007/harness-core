// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package utils

import (
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
		filename string
		node     Node
	}{
		{
			name:     "ParseJavaNode_JavaSourceFile",
			filename: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java",
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_JavaTestFile",
			filename: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/ConnectorUtilsTest.java",
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_JavaResourceFile",
			filename: "320-ci-execution/src/test/resources/all.json",
			node: Node{
				Type: NodeType_RESOURCE,
				Lang: LangType_JAVA,
				File: "all.json",
			},
		},
		{
			name:     "ParseJavaNode_ScalaSourceFile",
			filename: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.scala",
			node: Node{
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_ScalaTestFile_ScalaTestPath",
			filename: "320-ci-execution/src/test/scala/io/harness/stateutils/buildstate/ConnectorUtilsTest.scala",
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_ScalaTestFile_JavaTestPath",
			filename: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/ConnectorUtilsTest.scala",
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinSourceFile",
			filename: "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.kt",
			node: Node{
				Class: "ConnectorUtils",
				Type:  NodeType_SOURCE,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinTestFile_KotlinTestPath",
			filename: "320-ci-execution/src/test/kotlin/io/harness/stateutils/buildstate/ConnectorUtilsTest.kt",
			node: Node{
				Pkg:   "io.harness.stateutils.buildstate",
				Class: "ConnectorUtilsTest",
				Type:  NodeType_TEST,
				Lang:  LangType_JAVA,
			},
		},
		{
			name:     "ParseJavaNode_KotlinTestFile_JavaTestPath",
			filename: "320-ci-execution/src/test/java/io/harness/stateutils/buildstate/ConnectorUtilsTest.kt",
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
			n, _ := ParseJavaNode(tt.filename)
			assert.Equal(t, tt.node, *n, "extracted java node does not match")
		})
	}
}

func Test_ParseFileNames(t *testing.T) {

	files := []string{
		"320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java",     // Source file
		"320-ci-execution/src/test/java/io/harness/stateutils/buildstate/TestConnectorUtils.java", // Test file
		"810-ci-manager/src/test/resources/data/ng-trigger-config.yaml",                           // Resource file
		"332-ci-manager/pom.xml",
		"320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils", //.java extension is missing
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

	nodesExpected := []Node{node1, node2, node3, unknown, unknown}

	assert.Equal(t, nodesExpected, nodes, "extracted nodes don't match")
}
