// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package stream

import (
	"context"
	"fmt"
	"os"
	"os/signal"

	"github.com/harness/harness-core/product/log-service/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type tailCommand struct {
	key       string
	accountID string
	server    string
	token     string
}

func (c *tailCommand) run(*kingpin.ParseContext) error {
	ctx := context.Background()
	ctx, cancel := context.WithCancel(ctx)
	s := make(chan os.Signal, 1)
	signal.Notify(s, os.Interrupt)
	defer func() {
		println("")
		println("closing stream ...")
		signal.Stop(s)
		cancel()
	}()
	go func() {
		select {
		case <-s:
			cancel()
		case <-ctx.Done():
		}
	}()

	client := client.NewHTTPClient(c.server, c.accountID, c.token, false, "")
	linec, errc := client.Tail(ctx, c.key)
	for {
		select {
		case <-ctx.Done():
			return nil
		case err := <-errc:
			if err != context.Canceled {
				return nil
			}
		case line := <-linec:
			fmt.Println(line)
		}
	}
}

func registerTail(app *kingpin.CmdClause) {
	c := new(tailCommand)

	cmd := app.Command("tail", "tail the log stream").
		Action(c.run)

	cmd.Arg("accountID", "project identifier").
		Required().
		StringVar(&c.accountID)

	cmd.Arg("token", "server token").
		Required().
		StringVar(&c.token)

	cmd.Arg("key", "key identifier").
		Required().
		StringVar(&c.key)

	cmd.Flag("server", "server endpoint").
		Default("http://localhost:8079").
		StringVar(&c.server)
}
