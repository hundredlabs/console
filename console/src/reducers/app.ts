import { APP_LOADED, AppActionTypes, APPS_FETCHED, JOB_STATUS_UPDATED, PROJECT_DELETED } from "../actions/app";
import { App } from "../store/App";
import { Status } from "../services/Workspace";
const initialState: App = {
  isAppLoaded: false,
};

const appReducer = (state: App = initialState, action: AppActionTypes): App => {
  switch (action.type) {
    case APP_LOADED:
      return {
        ...state,
        isAppLoaded: true,
      };

    default:
      return state;
  }
};

export default appReducer;
