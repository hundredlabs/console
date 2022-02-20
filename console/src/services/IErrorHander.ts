import { message, Modal } from "antd";
import { IllegalParam, UnAuthorized } from "./Workspace";

export interface IErrorType {
  message: string;
  statusCode: number;
}

export interface IException {
  message: string;
}

export const getDefaultError = (request: string): IErrorType => ({
  message: `Unable to process the request : ${request}. Please try again later`,
  statusCode: 500,
});

export class IErrorHandler {
  public catchAll = (response: ErrResponse, statusCode: number) => {
    if (statusCode === 400) {
      this.showError(`${statusCode} ${(response as IllegalParam).message}`);
    } else if (statusCode == 401) {
      this.showUnAuthorizedError(`${(response as UnAuthorized).requestedResource}`);
    }
  };
  public showError = (message: string) => {
    Modal.error({
      title: "Oops! Something went wrong",
      content: message,
    });
  };

  public notifyError = (err: string) => {
    message.error(err);
  };

  public showUnAuthorizedError = (resource: string) => {
    Modal.error({
      title: "401 - Unauthorized access",
      content: `Access to the resource ${resource} is restricted. Kindly retry after login.`,
    });
  };

  public getDefaultError = (request: string): IErrorType => ({
    message: `Unable to process the request : ${request}. Please try again later`,
    statusCode: 500,
  });
}
export type ErrResponse = IllegalParam | UnAuthorized | IErrorType;
