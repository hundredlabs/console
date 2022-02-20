import { MemberProfile } from "../services/AuthService";
import React from "react";

export interface User {
  id: number;
  email: string;
  name: string;
  loggedIn: boolean;
  profile?: MemberProfile;
}

interface AppContext {
  currentUser: User;
  loading: boolean;
  updateUser: (id: number, name: string, email: string, loggedIn: boolean, profile?: MemberProfile) => void;
}

export const InitialUser: User = {
  id: 0,
  name: "",
  email: "",
  loggedIn: false,
};

const UserContext = React.createContext<AppContext>({
  currentUser: InitialUser,
  loading: true,
  updateUser: () => {},
});

export { UserContext };
