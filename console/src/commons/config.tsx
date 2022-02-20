import * as React from "react";

export const config = {
  githubRequests: "https://github.com/GigahexHQ/requests/issues/new",
  slackInvite: "https://join.slack.com/t/gigahexcomm/shared_invite/enQtODQ2NDAzODgyMDA0LWQxZjllZmIyODE1MjNkNDM5MWEwOTk2NzU1ZGVkMjBmOGQ3MDc1MGNhYzVkNDUyMjgzZDc1NDc1YTMzMWI3MzQ",
  twitterUsername: "GigahexApp",
  copyright: `Copyright © ${new Date().getFullYear()} Gigahex LLP`,
};

export interface ConfigType {
  key: string;
  name: string;
  description: string;
  eta: string;
  icon: React.ReactNode;
}
