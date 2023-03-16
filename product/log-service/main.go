// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

import (
	"fmt"
	"runtime"

	"github.com/harness/harness-core/product/log-service/cli"

	_ "github.com/joho/godotenv/autoload"
	_ "go.uber.org/automaxprocs"
)

func main() {

	defer func() {
		if r := recover(); r != nil {
			fmt.Printf("Handling Panic before exit: %v", r)

			stack := make([]byte, 1024*1024)
			length := runtime.Stack(stack, true)

			// Print the stack trace to stdout
			fmt.Printf("Stack Trace thread dump= %s\n", stack[:length])
		}
	}()

	cli.Command()
}
