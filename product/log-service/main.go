// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"runtime"
	"runtime/pprof"
	"syscall"

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

	go threadDump()

	cli.Command()
}

func threadDump() {
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt, os.Kill, syscall.SIGUSR1, syscall.SIGKILL, syscall.SIGTERM)

	// Wait for a signal
	<-sig

	// Get a stack trace for each goroutine
	buf := make([]byte, 1<<16)
	length := runtime.Stack(buf, true)

	podName := os.Getenv("POD_NAME")
	// Write the stack trace to a file or log it
	f, err := os.Create(podName + "thread_dump.txt")
	if err != nil {
		log.Println(err)
	}
	defer f.Close()

	if _, err := f.Write(buf); err != nil {
		log.Println(err)
	}
	log.Println("thread dump before exit = %s\n", buf[:length])

	log.Println("generating heap dump for ", podName)

	heap, err := os.Create(podName + "heap.dump")
	if err != nil {
		log.Println("Failed to create heap dump file: %v", err)
	}
	if err := pprof.WriteHeapProfile(heap); err != nil {
		log.Println("Failed to write heap profile: %v", err)
	}
	log.Println("Heap dump generated for", podName)
}
