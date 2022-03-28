import { message } from "antd";
import { FailedFileListing, FileListing } from "../models/FileBrowser";
import { PubKeyResponse } from "./AuthService";
import { IErrorHandler } from "./IErrorHander";
import { InternalServerError } from "./SparkService";
import WebService from "./WebService";
import { FailedResponse } from "./Workspace";
const _sodium = require("libsodium-wrappers");

interface OpResult {
  success: boolean;
}

export interface ConnectionMeta {
  name: string;
  category: string;
  description: string;
}

export interface ConnectionView {
  id: number;
  name: string;
  schemaVersion: string;
  provider: string;
  providerCategory: string;
  dateCreated: number;
}

class ConnectionService extends IErrorHandler {
  private webAPI: WebService = new WebService();

  saveConnection = async (name: string, provider: string, props_str: string, schemaVersion: number, onSuccess: (id: number) => void) => {
    await _sodium.ready;
    const sodium = _sodium;

    const pubKeyResponse = await this.webAPI.get<PubKeyResponse>(`/web/secrets/pub-key`);
    if (pubKeyResponse.parsedBody) {
      const pubKey = pubKeyResponse.parsedBody.key;

      const pubKeyUint8 = new Uint8Array(Buffer.from(pubKey, "hex"));
      const cipherProps = sodium.crypto_box_seal(props_str, pubKeyUint8, "hex");

      const savedResponse = await this.webAPI.post<{ connectionId: number } | FailedResponse>(`/web/v1/connections`, {
        name: name,
        provider: provider,
        encProperties: cipherProps,
        schemaVersion: schemaVersion,
      });

      if (savedResponse.status === 201 && savedResponse.parsedBody) {
        const creationResult = savedResponse.parsedBody as { connectionId: number };
        onSuccess(creationResult.connectionId);
      } else {
        message.error((savedResponse.parsedBody as FailedResponse).message);
      }
    }
  };

  getConnectionProviders = async (onSuccess: (topic: ConnectionMeta[]) => void) => {
    try {
      const response = this.webAPI.get<ConnectionMeta[] | InternalServerError>(`/web/v1/connection-providers`);

      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as ConnectionMeta[];
        onSuccess(result);
      } else if (r.parsedBody) {
        const body = r.parsedBody as InternalServerError;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the connections ");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  listConnections = async (onSuccess: (topic: ConnectionView[]) => void) => {
    try {
      const response = this.webAPI.get<ConnectionView[] | InternalServerError>(`/web/v1/connections`);

      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as ConnectionView[];
        onSuccess(result);
      } else if (r.parsedBody) {
        const body = r.parsedBody as InternalServerError;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the connections");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  listRootDirs = async (connectionId: number, onSuccess: (topic: FileListing) => void, OnFailure: (err: FailedFileListing) => void) => {
    try {
      const response = this.webAPI.get<FileListing | FailedFileListing>(`/web/v1/fs/${connectionId}`);

      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as FileListing;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as FailedFileListing;
        OnFailure(body);
      } else {
        const err = this.getDefaultError("Fetching the files");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  listFiles = async (
    connectionId: number,
    path: string,
    onSuccess: (topic: FileListing) => void,
    OnFailure: (err: FailedFileListing) => void
  ) => {
    try {
      const response = this.webAPI.get<FileListing | FailedFileListing>(`/web/v1/fs/${connectionId}/path/${path}`);

      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as FileListing;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as FailedFileListing;
        OnFailure(body);
      } else {
        const err = this.getDefaultError("Fetching the files");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  deleteConnection = async (connectionId: number, onSuccess: (result: OpResult) => void) => {
    try {
      const response = this.webAPI.delete<OpResult | InternalServerError>(`/web/v1/connections/${connectionId}`, {});

      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as OpResult;
        onSuccess(result);
      } else if (r.parsedBody) {
        const body = r.parsedBody as InternalServerError;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the files");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  updateConnection = async (
    connectionId: number,
    name: string,
    provider: string,
    props_str: string,
    schemaVersion: number,
    onSuccess: (result: OpResult) => void
  ) => {
    await _sodium.ready;
    const sodium = _sodium;

    const pubKeyResponse = await this.webAPI.get<PubKeyResponse>(`/web/secrets/pub-key`);
    if (pubKeyResponse.parsedBody) {
      const pubKey = pubKeyResponse.parsedBody.key;

      const pubKeyUint8 = new Uint8Array(Buffer.from(pubKey, "hex"));
      const cipherProps = sodium.crypto_box_seal(props_str, pubKeyUint8, "hex");

      const savedResponse = await this.webAPI.put<OpResult | FailedResponse>(`/web/v1/connections/${connectionId}`, {
        name: name,
        provider: provider,
        encProperties: cipherProps,
        schemaVersion: schemaVersion,
      });

      if (savedResponse.status === 201 && savedResponse.parsedBody) {
        const updateResult = savedResponse.parsedBody as OpResult;
        onSuccess(updateResult);
      } else {
        message.error((savedResponse.parsedBody as FailedResponse).message);
      }
    }
  };
}

export default new ConnectionService();
