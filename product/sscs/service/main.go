package main

import (
	"fmt"
	"github.com/harness/harness-core/product/sscs/service/sbom"
	"runtime"
)

func main() {
	fmt.Printf("go version: %s\n", runtime.Version())
	sbom.Placeholder()
}
