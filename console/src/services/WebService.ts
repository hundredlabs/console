import dotenv from "dotenv";
export interface IHttpResponse<T> extends Response {
  parsedBody?: T;
}

export default class WebService {
  private host: string;
  private port: number;
  private protocol: string;
  private apiEndpoint: string;
  private wsEndpoint: string;
  private isExternal: boolean;
  constructor(host: string = "api.example.com", port: number = 80, protocol: string = "http", isExternal: boolean = true) {
    this.host = host;
    this.port = port;
    this.protocol = protocol;
    this.isExternal = isExternal;
    dotenv.config();
    if (isExternal) {
      if (window.location.host.includes("localhost") || window.location.host.includes("127.0.0.1")) {
        this.apiEndpoint = process.env.REACT_APP_API_ENDPOINT;
        this.wsEndpoint = process.env.REACT_APP_API_WS_ENDPOINT;
      } else {
        const remoteEndPoint = `${window.location.protocol}//${window.location.hostname}${
          window.location.port ? ":" + window.location.port : ""
        }`;
        let wsProtocol = "ws";
        if (window.location.protocol.endsWith("s:")) {
          wsProtocol = "wss";
        }
        this.apiEndpoint = remoteEndPoint;
        this.wsEndpoint = `${wsProtocol}://${window.location.hostname}${window.location.port ? ":" + window.location.port : ""}`;
      }
    } else {
      this.apiEndpoint = `${protocol}://${host}:${port}`;
      this.wsEndpoint = process.env.REACT_APP_API_WS_ENDPOINT;
    }
  }

  getWSEndpoint = () => {
    return this.wsEndpoint;
  };

  getEndpoint = () => {
    return this.apiEndpoint;
  };

  private getPath = (path: string): string => {
    return `${this.apiEndpoint}${path}`;
  };

  http = <T>(request: RequestInfo): Promise<IHttpResponse<T>> => {
    return new Promise((resolve, reject) => {
      let response: IHttpResponse<T>;
      fetch(request)
        .then((res) => {
          response = res;
          return res.json() as Promise<T>;
        })
        .then((body) => {
          if (response.status !== 500) {
            response.parsedBody = body;
            resolve(response);
          } else {
            reject(response);
          }
        })
        .catch((err) => {
          console.log(err);
          reject(err);
        });
    });
  };

  get = async <T>(
    path: string,
    args: RequestInit = {
      method: "get",
      credentials: "include",
      mode: "cors",
      headers: {
        "X-TIMEZONE": Intl.DateTimeFormat().resolvedOptions().timeZone,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
    }
  ): Promise<IHttpResponse<T>> => {
    return await this.http<T>(new Request(this.getPath(path), args));
  };

  delete = async <T>(
    path: string,
    body: any,
    args: RequestInit = {
      method: "delete",
      credentials: "include",
      mode: "cors",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    }
  ): Promise<IHttpResponse<T>> => {
    return await this.http<T>(new Request(this.getPath(path), args));
  };

  authPost = async <T>(path: string, args: RequestInit): Promise<IHttpResponse<T>> => {
    return await this.http<T>(new Request(this.getPath(path), args));
  };

  post = async <T>(
    path: string,
    body: any,
    args: RequestInit = {
      method: "post",
      mode: "cors", // no-cors, *cors, same-origin
      cache: "no-cache", // *default, no-cache, reload, force-cache, only-if-cached
      credentials: "include",
      headers: {
        "X-TIMEZONE": Intl.DateTimeFormat().resolvedOptions().timeZone,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      redirect: "follow", // manual, *follow, error
      body: JSON.stringify(body),
    }
  ): Promise<IHttpResponse<T>> => {
    return await this.http<T>(new Request(this.getPath(path), args));
  };

  put = async <T>(
    path: string,
    body: any,
    args: RequestInit = {
      method: "put",
      body: JSON.stringify(body),
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
    }
  ): Promise<IHttpResponse<T>> => {
    return await this.http<T>(new Request(this.getPath(path), args));
  };
}
