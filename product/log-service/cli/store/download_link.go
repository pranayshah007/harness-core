// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package store

import (
	"github.com/harness/harness-core/product/log-service/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type downloadLinkCommand struct {
	key       string
	accountID string
	server    string
	token     string
}

func (c *downloadLinkCommand) run(*kingpin.ParseContext) error {
	client := client.NewHTTPClient(c.server, c.accountID, c.token, false, "")
	link, err := client.DownloadLink(nocontext, c.key)
	if err != nil {
		return err
	}
	println(link.Value)
	return nil
}

func registerDownloadLink(app *kingpin.CmdClause) {
	c := new(downloadLinkCommand)

	cmd := app.Command("download-link", "generate a download link").
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
