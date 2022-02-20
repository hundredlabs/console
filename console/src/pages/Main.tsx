import * as React from "react";
import { UserContext } from "../store/User";
import { history } from "../configureStore";

export const MainRedirect: React.FC = () => {
  const context = React.useContext(UserContext);

  React.useEffect(() => {
    console.log("in main redirect");
    console.log(context.currentUser);
    if (context.currentUser.loggedIn && context.currentUser.profile) {
      history.push(`/${context.currentUser.profile.orgSlugId}/workspace/${context.currentUser.profile.workspaceId}/clusters`);
    } else if (context.currentUser.loggedIn) {
      history.push(`/onboard`);
    } else {
      console.log("in login email redirect");
      history.push(`/login`);
    }
  }, []);

  return <></>;
};
