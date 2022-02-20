import { combineReducers } from "redux";
import { StateType } from "typesafe-actions";
import { History } from "history";
import { connectRouter } from "connected-react-router";
import authReducer from "./authReducer";
import appReducer from "./app";

const rootReducer = (history: History) =>
  combineReducers({
    auth: authReducer,
    app: appReducer,
    router: connectRouter(history),
  });

export type AppState = StateType<ReturnType<typeof rootReducer>>;
export default rootReducer;
