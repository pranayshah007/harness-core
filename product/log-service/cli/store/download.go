// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package store

import (
	"io"
	"os"

	"github.com/harness/harness-core/product/log-service/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type downloadCommand struct {
	key       string
	accountID string
	server    string
	token     string
}

func (c *downloadCommand) run(*kingpin.ParseContext) error {
	client := client.NewHTTPClient(c.server, c.accountID, c.token, false, "")
	out, err := client.Download(nocontext, c.key)
	if out != nil {
		defer out.Close()
	}
	if err != nil {
		return err
	}
	_, err = io.Copy(os.Stdout, out)
	return err
}

func registerDownload(app *kingpin.CmdClause) {
	c := new(downloadCommand)

	cmd := app.Command("download", "download logs").
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
