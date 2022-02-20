import { Action } from "redux";
import { UPDATE_LOGIN, DELETE_USER_SESSION, AuthActionTypes } from "../actions/auth";
import { User } from "../store/User";
const initialState: User = {
  loggedIn: false,
  email: "",
  id: -1,
  name: "",
};
const authReducer = (state: User = initialState, action: AuthActionTypes): User => {
  switch (action.type) {
    case UPDATE_LOGIN:
      return {
        loggedIn: true,
        id: action.memberId,
        email: action.email,
        name: action.name,
      };
    case DELETE_USER_SESSION:
      return initialState;
    default:
      return state;
  }
};

export default authReducer;
