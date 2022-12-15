// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package store

import (
	"github.com/harness/harness-core/product/log-service/client"
	"gopkg.in/alecthomas/kingpin.v2"
)

type uploadLinkCommand struct {
	key       string
	accountID string
	server    string
	token     string
}

func (c *uploadLinkCommand) run(*kingpin.ParseContext) error {
	client := client.NewHTTPClient(c.server, c.accountID, c.token, false, "")
	link, err := client.UploadLink(nocontext, c.key)
	if err != nil {
		return err
	}
	println(link.Value)
	return nil
}

func registerUploadLink(app *kingpin.CmdClause) {
	c := new(uploadLinkCommand)

	cmd := app.Command("upload-link", "generate an upload link").
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
