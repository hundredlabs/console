import { Status } from "../services/Workspace";

export const APP_LOADED = "APP_LOADED";
export const APPS_FETCHED = "APPS_FETCHED";
export const JOB_STATUS_UPDATED = "JOB_STATUS_UPDATED";
export const PROJECT_DELETED = "PROJECT_DELETED";

interface AppLoadedAction {
  type: typeof APP_LOADED;
}

interface AppsFetchedAction {
  type: typeof APPS_FETCHED;
}

export type AppActionTypes = AppLoadedAction;

export const appLoaded = (): AppActionTypes => {
  return {
    type: APP_LOADED,
  };
};
