import React, { useState, useEffect } from "react";
import Spinner from "../components/Icons/Spinner";
import { useLocation } from "react-router-dom";
import { history } from "../configureStore";
import AuthService, { MemberProfile, MemberInfo } from "../services/AuthService";
import { InitialUser, User, UserContext } from "../store/User";

const UserApp: React.FC = ({ children }) => {
  const location = useLocation();
  const [appState, setAppState] = useState<{ user: User; loading: boolean }>({
    user: InitialUser,
    loading: true,
  });

  const updateUser = (id: number, name: string, email: string, loggedIn: boolean, profile?: MemberProfile) => {
    setAppState({
      ...appState,
      user: {
        id: id,
        name: name,
        email: email,
        loggedIn: loggedIn,
        profile: profile,
      },
      loading: false,
    });
  };

  useEffect(() => {
    if (location.pathname.includes("signup")) {
      setAppState({ ...appState, loading: false });
    } else if (!location.pathname.includes("oauth")) {
      AuthService.accountInfo().then((account) => {
        if (account.exist) {
          const member = account as MemberInfo;
          updateUser(member.id, member.name, member.email, true, member.profile);
        } else {
          if (!location.pathname.includes("oauth") && !location.pathname.includes("login")) {
            history.push("/login");
          }
          setAppState({ ...appState, loading: false });
        }
      });
    } else {
      setAppState({ ...appState, loading: false });
    }
  }, []);

  return (
    <UserContext.Provider value={{ currentUser: appState.user, loading: appState.loading, updateUser: updateUser }}>
      {appState.loading && <Spinner />}
      {!appState.loading && <>{children}</>}
    </UserContext.Provider>
  );
};

export default UserApp;
