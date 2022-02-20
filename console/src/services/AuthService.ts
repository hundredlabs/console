import WebService from "./WebService";
import { IErrorHandler, IErrorType, getDefaultError } from "./IErrorHander";

import { ClusterProvider, CloudCluster, UnAuthorized, FailedResponse } from "./Workspace";

export interface IErrors {
  [key: string]: string;
}
type LoginResponse = {
  success: boolean;
  email: string;
  statusCode: number;
  userName: string;
  message: string;
  errors: IErrors;
};
type EmailNotFound = {
  message: string;
};
type InternalServerError = {
  path: string;
  message: string;
};
type ForbiddenError = {
  correctPath: string;
  message: string;
};
const errLoginResponse: LoginResponse = {
  success: false,
  email: "",
  statusCode: 500,
  userName: "",
  message: "Internal server error",
  errors: {},
};

type LogoutResponse = {
  success: boolean;
  message: string;
};
export interface MemberProfile {
  orgId: number;
  orgName: string;
  orgSlugId: string;
  workspaceId: number;
  workspaceName: string;
  webTheme: string;
  desktopTheme: string;
  orgThumbnail?: string;
}
export type MemberInfo = {
  exist: boolean;
  hasProfile: boolean;
  profile?: MemberProfile;
  id: number;
  name: string;
  email: string;
  message: string;
};

export type MemberInfoResponse = MemberInfo | MemberNotFound;
export type ConfirmEmailResponse = {
  message: string;
  success: boolean;
};

export type ReqId = {
  reqId: number;
};
export type ReqIdResponse = ReqId | IErrorType;
export type MemberNotFound = {
  exist: boolean;
  message: string;
};
const defaultMemmber: MemberNotFound = {
  exist: false,
  message: "User not authenticated",
};
const defaultLogOutResp: LogoutResponse = {
  success: true,
  message: "",
};

export type IBetaRequest = {
  reqId: number;
  name: string;
  email: string;
  serverRegion: string;
  activationCode: string;
  activationStatus: boolean;
  dtRequested: string;
};
export type IBetaRequestResp = {
  total: number;
  requests: Array<IBetaRequest>;
};
export interface AlphaReqType {
  reqId: number;
  email: string;
  dtRequested: string;
}
export type IAlphaRequestResp = {
  total: number;
  requests: Array<AlphaReqType>;
};
export type RequestApproved = {
  approved: boolean;
};
interface EmailByCode {
  email: string;
}
interface SignupResponse {
  success: boolean;
  message: string;
}
export type OrgResponse = {
  orgId: number;
  orgName: string;
  key: string;
  secret: string;
  willExpireIn: string;
};
export type UpdateName = {
  nameChanged: boolean;
};
export type UpdatePassword = {
  passwordChanged: boolean;
};
export type EmailByCodeResponse = EmailByCode | EmailNotFound | InternalServerError | ForbiddenError;
export type IBetaRequestResponse = IBetaRequestResp | IErrorType;
export type IAlphaRequestResponse = IAlphaRequestResp | IErrorType;
export type IApprovedReqResponse = RequestApproved | IErrorType;
export type IOrgResponse = Array<OrgResponse> | IErrorType;
type IUpdateNameResponse = UpdateName | IErrorType | UnAuthorized;
type IUpdatePasswordResponse = UpdatePassword | LoginResponse;

export interface PubKeyResponse {
  key: string;
}

interface IntegrationKeyGenResponse {
  keyId: number;
  pubkey: string;
}

let existingWindow: Window | null = null;
class AuthService extends IErrorHandler {
  private webAPI: WebService = new WebService();
  apiEndpoint = this.webAPI.getEndpoint();
  getLoginResponse = (body?: LoginResponse) => {
    if (body) {
      return body;
    } else {
      return errLoginResponse;
    }
  };

  login = async (email: string, password: string, rememberMe: boolean): Promise<LoginResponse> => {
    const authHeader = btoa(`${email}:${password}`);

    let authReq: RequestInit = {
      method: "post",
      mode: "cors", // no-cors, *cors, same-origin
      cache: "no-cache", // *default, no-cache, reload, force-cache, only-if-cached
      credentials: "include",
      headers: {
        Authorization: `Basic ${authHeader}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      redirect: "follow", // manual, *follow, error
      body: JSON.stringify({
        rememberMe: rememberMe,
      }),
    };
    try {
      const response = this.webAPI.authPost<LoginResponse>("/web/signin", authReq);

      const r = await response;
      return this.getLoginResponse(r.parsedBody);
    } catch (e: any) {
      const errorResp: LoginResponse = this.getLoginResponse(e.parsedBody);
      this.showError(errorResp.message + " caused while processing login request. Please try again later.");
      return this.getLoginResponse(errorResp);
    }
  };

  logOut = async (): Promise<LogoutResponse> => {
    try {
      const response = this.webAPI.get<LogoutResponse>("/web/signout");
      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else return defaultLogOutResp;
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
      return new Promise((resolve) => {
        const errLogOutRes: LogoutResponse = {
          success: false,
          message: "Internal server error",
        };
        resolve(errLogOutRes);
      });
    }
  };

  desktopLogin = async (token: string): Promise<LoginResponse> => {
    try {
      let authReq: RequestInit = {
        method: "post",
        mode: "cors", // no-cors, *cors, same-origin
        cache: "no-cache", // *default, no-cache, reload, force-cache, only-if-cached
        credentials: "include",
        headers: {
          DESKTOP_TOKEN: `${token}`,
          Accept: "application/json",
          "Content-Type": "application/json",
          "Access-Control-Allow-Credentials": "true",
        },
        redirect: "follow", // manual, *follow, error
      };
      const response = this.webAPI.authPost<LoginResponse>("/web/desktop-signin", authReq);

      const r = await response;
      return this.getLoginResponse(r.parsedBody);
    } catch (e: any) {
      const errorResp: LoginResponse = this.getLoginResponse(e.parsedBody);
      this.showError(errorResp.message + " caused while processing login request. Please try again later.");
      return this.getLoginResponse(errorResp);
    }
  };

  accountInfo = async (): Promise<MemberInfoResponse> => {
    try {
      const response = this.webAPI.get<MemberInfoResponse>("/web/account");

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else return defaultMemmber;
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
      return new Promise((resolve) => {
        resolve(defaultMemmber);
      });
    }
  };

  confirmEmail = async (token: string): Promise<ConfirmEmailResponse> => {
    try {
      const response = this.webAPI.get<ConfirmEmailResponse>(`/web/confirm-email/${token}`);

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else return { message: "Unable to serve the request", success: false };
    } catch (e) {
      return new Promise((resolve) => {
        resolve({ message: "Unable to serve the request", success: false });
      });
    }
  };

  accessRequest = async (name: string, email: string, serverRegion: string): Promise<ReqIdResponse> => {
    try {
      const response = this.webAPI.post<ReqId>("/web/access-request", {
        name: name,
        email: email,
        serverRegion: serverRegion,
      });

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else return getDefaultError("access request");
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
      return new Promise((resolve) => {
        resolve(getDefaultError("access request"));
      });
    }
  };

  updateName = async (name: string, onSuccess: (result: UpdateName) => void) => {
    try {
      const response = this.webAPI.post<IUpdateNameResponse>("/web/v1/name", {
        name: name,
      });

      const r = await response;
      if (r.status == 200 && r.parsedBody) {
        const result = r.parsedBody as UpdateName;
        onSuccess(result);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("Update name");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  updatePassword = async (
    email: string,
    oldPwd: string,
    newPwd: string,
    onSuccess: (result: UpdatePassword) => void,
    onFailure: (result: LoginResponse) => void
  ) => {
    try {
      const response = this.webAPI.post<IUpdatePasswordResponse>("/web/v1/password", {
        email: email,
        oldPassword: oldPwd,
        newPassword: newPwd,
      });

      const r = await response;
      if (r.status == 200 && r.parsedBody) {
        const result = r.parsedBody as UpdatePassword;
        onSuccess(result);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as LoginResponse;
        onFailure(body);
      } else {
        const err = this.getDefaultError("Update password");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  approveRequest = async (email: string, onSuccess: (result: RequestApproved) => void) => {
    try {
      const response = this.webAPI.post<IApprovedReqResponse>("/web/admin/approve-request", {
        email: email,
      });

      const r = await response;
      if (r.status == 200 && r.parsedBody) {
        const result = r.parsedBody as RequestApproved;
        onSuccess(result);
      } else throw "Error";
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
    }
  };
  listAlphaRequests = async (pageNum: number, pageSize: number, onSuccess: (result: IAlphaRequestResp) => void) => {
    try {
      const response = this.webAPI.post<IAlphaRequestResponse>("/web/admin/list-alpha-requests", {
        pageNum: pageNum,
        pageSize: pageSize,
      });

      const r = await response;
      if (r.status == 200 && r.parsedBody) {
        const result = r.parsedBody as IAlphaRequestResp;
        onSuccess(result);
      } else throw "Error";
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
    }
  };

  listOrgs = async (onSuccess: (result: Array<OrgResponse>) => void) => {
    try {
      const response = this.webAPI.get<IOrgResponse>(`/web/v1/orgs`);
      const r = await response;
      if (r.status == 200 && r.parsedBody) {
        const result = r.parsedBody as Array<OrgResponse>;
        onSuccess(result);
      } else {
        const err = this.getDefaultError("Access Keys Details");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Detailed Run time Executor Metrics");
      this.showError(err.message);
    }
  };

  listRequests = async (pageNum: number, pageSize: number): Promise<IBetaRequestResponse> => {
    try {
      const response = this.webAPI.post<IBetaRequestResp>("/web/list-approved-requests", {
        pageNum: pageNum,
        pageSize: pageSize,
      });

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else return getDefaultError("list requests");
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
      return new Promise((resolve) => {
        resolve(getDefaultError("list requests"));
      });
    }
  };

  betaRegister = async (
    name: string,
    email: string,
    password: string,
    receiveUpdates: boolean,
    onSuccess: (result: SignupResponse) => void,
    onComplete: () => void
  ) => {
    try {
      const response = this.webAPI.put<SignupResponse>(`/web/betaregister`, {
        name: name,
        email: email,
        password: password,
        receiveUpdates: receiveUpdates,
      });

      const r = await response;
      if (r.status === 400 && r.parsedBody) {
        this.showError((r.parsedBody as SignupResponse).message);
      } else if (r.status === 200 && r.parsedBody && !(r.parsedBody as SignupResponse).success) {
        this.showError((r.parsedBody as SignupResponse).message);
      } else if (r.status === 200 && r.parsedBody) {
        const b = r.parsedBody as SignupResponse;
        onSuccess(b);
      } else {
        throw "Error";
      }
      onComplete();
    } catch (e) {
      this.showError("Unable to process access code request. Please try again later.");
    }
  };

  handleSocialAuth = (provider: string) => {
    const w = 500;
    const h = 500;
    const dualScreenLeft = window.screenLeft != undefined ? window.screenLeft : window.screenX;
    const dualScreenTop = window.screenTop != undefined ? window.screenTop : window.screenY;
    if (existingWindow) {
      existingWindow.close();
    }
    const authUrl = `${this.apiEndpoint}/web/authenticate/${provider}`;
    const width = window.innerWidth
      ? window.innerWidth
      : document.documentElement.clientWidth
      ? document.documentElement.clientWidth
      : window.screen.width;
    const height = window.innerHeight
      ? window.innerHeight
      : document.documentElement.clientHeight
      ? document.documentElement.clientHeight
      : window.screen.height;

    const systemZoom = width / window.screen.availWidth;
    const left = (width - w) / 2 / systemZoom + dualScreenLeft;
    const top = (height - h) / 2 / systemZoom + dualScreenTop;

    existingWindow = window.open(
      authUrl,
      "Authentication",
      "scrollbars=yes, width=" + w / systemZoom + ", height=" + h / systemZoom + ", top=" + top + ", left=" + left
    );
  };

  updateAuthState = async (provider: string, params: string): Promise<LoginResponse> => {
    try {
      const response = this.webAPI.get<LoginResponse>(`/web/authenticate/${provider}${params}`);
      const r = await response;
      return this.getLoginResponse(r.parsedBody);
    } catch (e: any) {
      const errorResp: LoginResponse = this.getLoginResponse(e.parsedBody);
      return this.getLoginResponse(errorResp);
    }
  };

  // saveSecret = async (apiKey: string, apiSecretKey: string) => {
  //   await _sodium.ready;
  //   const sodium = _sodium;

  //   const pubKeyResponse = await this.webAPI.get<PubKeyResponse>(`/web/secrets/pub-key`);
  //   if (pubKeyResponse.parsedBody) {
  //     const pubKey = pubKeyResponse.parsedBody.key;

  //     const pubKeyUint8 = new Uint8Array(Buffer.from(pubKey, "hex"));
  //     const ciperApiKey = sodium.crypto_box_seal("API_AWS_KEY", pubKeyUint8, "hex");
  //     const cipherApiSecret = sodium.crypto_box_seal("SECRET_KEY", pubKeyUint8, "hex");
  //     const savedResponse = await this.webAPI.post<{}>(`/web/secrets/save`, {
  //       apiKey: ciperApiKey,
  //       apiKeySecret: cipherApiSecret,
  //     });
  //   }
  // };

  // saveAndValidateAWS = async (
  //   apiKey: string,
  //   apiSecretKey: string,
  //   onSuccess: (clusters: CloudCluster[]) => void,
  //   onFailure: (message: string) => void
  // ) => {
  //   await _sodium.ready;
  //   const sodium = _sodium;

  //   const pubKeyResponse = await this.webAPI.get<IntegrationKeyGenResponse>(`/web/v1/secrets/generate/integration/aws`);
  //   if (pubKeyResponse.status === 200 && pubKeyResponse.parsedBody) {
  //     const pubKey = pubKeyResponse.parsedBody.pubkey;

  //     const pubKeyUint8 = new Uint8Array(Buffer.from(pubKey, "hex"));
  //     const ciperApiKey = sodium.crypto_box_seal(apiKey, pubKeyUint8, "hex");
  //     const cipherApiSecret = sodium.crypto_box_seal(apiSecretKey, pubKeyUint8, "hex");
  //     const emrClustersResponse = await this.webAPI.post<CloudCluster[] | FailedResponse>(`/web/v1/secrets/integration/aws`, {
  //       keyId: pubKeyResponse.parsedBody.keyId,
  //       cipherApiKey: ciperApiKey,
  //       cipherApiSecretKey: cipherApiSecret,
  //     });
  //     if (emrClustersResponse.status === 200 && emrClustersResponse.parsedBody) {
  //       onSuccess(emrClustersResponse.parsedBody as CloudCluster[]);
  //     } else {
  //       onFailure((emrClustersResponse.parsedBody as FailedResponse).message);
  //     }
  //   }
  // };

  listRegionsByAccount = async (
    provider: ClusterProvider,
    onSuccess: (regions: string[]) => void,
    onFailure: (message: string) => void,
    onNotFound: () => void
  ) => {
    const regionsFetchResponse = await this.webAPI.get<string[] | FailedResponse>(`/web/v1/clusters/provider/${provider}/regions`);

    if (regionsFetchResponse.status === 200 && regionsFetchResponse.parsedBody) {
      onSuccess(regionsFetchResponse.parsedBody as string[]);
    } else if (regionsFetchResponse.status === 400) {
      onFailure((regionsFetchResponse.parsedBody as FailedResponse).message);
    } else if (regionsFetchResponse.status === 404) {
      onNotFound();
    }
  };

  listClustersByAccount = async (
    provider: ClusterProvider,
    region: string,
    onSuccess: (clusters: CloudCluster[]) => void,
    onFailure: (message: string) => void,
    onNotFound: () => void
  ) => {
    const clusterFetchResponse = await this.webAPI.get<CloudCluster[] | FailedResponse>(
      `/web/v1/secrets/integration/${provider}/region/${region}`
    );

    if (clusterFetchResponse.status === 200 && clusterFetchResponse.parsedBody) {
      onSuccess(clusterFetchResponse.parsedBody as CloudCluster[]);
    } else if (clusterFetchResponse.status === 400) {
      onFailure((clusterFetchResponse.parsedBody as FailedResponse).message);
    } else if (clusterFetchResponse.status === 404) {
      onNotFound();
    }
  };

  getEmailByCode = async (code: string, onSuccess: (email: EmailByCode) => void, onFailure: (message: string) => void) => {
    try {
      const response = this.webAPI.get<EmailByCodeResponse>(`/web/activate/${code}`);

      const r = await response;
      if (r.status === 404 && r.parsedBody) {
        onFailure((r.parsedBody as EmailNotFound).message);
      } else if (r.status === 500 && r.parsedBody) {
        onFailure((r.parsedBody as InternalServerError).message);
      } else if (r.status == 403 && r.parsedBody) {
        onFailure((r.parsedBody as ForbiddenError).message);
      } else if (r.status === 200 && r.parsedBody) {
        const b = r.parsedBody as EmailByCode;
        onSuccess(b);
      } else {
        throw "Error";
      }
    } catch (e) {
      this.showError("Unable to process access code request. Please try again later.");
    }
  };
}

export default new AuthService();
