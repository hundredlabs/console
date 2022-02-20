import WebService from "./WebService";
import { IErrorHandler } from "./IErrorHander";
export type FileType = "FILE" | "DIRECTORY" | "EMPTY";
export interface HDFSFile {
  replication: number;
  accessTime?: number;
  blockSize?: number;
  group?: string;
  length?: number;
  modificationTime?: number;
  pathSuffix?: string;
  owner?: string;
  permission?: number;
  type?: FileType;
  size?: number;
}

export interface HDFSFileStatus {
  FileStatus: HDFSFile[];
}
export interface FileStatus {
  FileStatus: HDFSFile;
}

export interface HDFSStrogeFiles {
  FileStatuses: HDFSFileStatus;
}

interface ActionResponse {
  boolean: boolean;
}

export class LocalHDFSFileService extends IErrorHandler {
  protected webAPI: WebService;
  protected port: number;
  constructor() {
    super();
    this.webAPI = new WebService();
  }

  listAllHDFSStorageFiles = async (
    path: string,
    clsId: number,
    onSuccess: (files: HDFSStrogeFiles) => void,
    onFailure: (err: string) => void
  ) => {
    const err = this.getDefaultError("Fetch HDFS Files");
    try {
      const response = this.webAPI.get<HDFSStrogeFiles>(`/web/v1/hadoop/${clsId}/webhdfs/v1/${path}?op=LISTSTATUS`);
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as HDFSStrogeFiles;
        onSuccess(result);
      } else if (r.status === 404 && r.parsedBody) {
        const result = r.parsedBody as any;
      } else {
        onFailure(err.message);
      }
    } catch (e) {
      onFailure(err.message);
    }
  };

  getFileStatus = async (path: string, clsId: number, onSuccess: (files: FileStatus) => void, onFailure: (err: string) => void) => {
    const err = this.getDefaultError("Fetch HDFS Files");
    try {
      const response = this.webAPI.get<FileStatus>(`/web/v1/hadoop/${clsId}/webhdfs/v1/${path}?op=GETFILESTATUS`);
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as FileStatus;
        onSuccess(result);
      } else if (r.status === 404 && r.parsedBody) {
        const result = r.parsedBody as any;
      } else {
        onFailure(err.message);
      }
    } catch (e) {
      onFailure(err.message);
    }
  };

  deleteStorageFile = async (path: string, clsId: number, onSuccess: (r: ActionResponse) => void, onFailure: (err: string) => void) => {
    const err = this.getDefaultError(`Delete HDFS File name '${path}'`);
    try {
      const response = this.webAPI.delete<ActionResponse>(
        `/web/v1/hadoop/${clsId}/webhdfs/v1/${path}?op=DELETE&user.name=user_name&recursive=true`,
        {}
      );
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as ActionResponse;
        onSuccess(result);
      } else if (r.status === 404 && r.parsedBody) {
        const result = r.parsedBody as any;
        onFailure(result.RemoteException.message);
      } else {
        onFailure(err.message);
      }
    } catch (e) {
      onFailure(err.message);
    }
  };
  renameStorageFile = async (
    oldPath: string,
    newPath: string,
    clsId: number,
    onSuccess: (r: ActionResponse) => void,
    onFailure: (err: string) => void
  ) => {
    const err = this.getDefaultError(`Rename HDFS File name '${oldPath}'`);
    try {
      const response = this.webAPI.put<ActionResponse>(
        `/web/v1/hadoop/${clsId}/modify/webhdfs/v1/${oldPath}?op=RENAME&user.name=user_name&destination=/${newPath}`,
        {}
      );
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as ActionResponse;
        onSuccess(result);
      } else if (r.status === 404 && r.parsedBody) {
        const result = r.parsedBody as any;
        onFailure(result.RemoteException.message);
      } else {
        onFailure(err.message);
      }
    } catch (e) {
      onFailure(err.message);
    }
  };

  createStorageFile = async (path: string, clsId: number, onSuccess: (r: any) => void, onFailure: (err: string) => void) => {
    const err = this.getDefaultError(`Create HDFS File name '${path}'`);
    try {
      const response = this.webAPI.put<any>(`/web/v1/hadoop/${clsId}/modify/webhdfs/v1/${path}?user.name=user_name&op=MKDIRS`, {});

      const r = await response;

      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as any;
        onSuccess(result);
      } else if (r.status === 404 && r.parsedBody) {
        const result = r.parsedBody as any;
        onFailure(result.RemoteException.message);
      } else {
        onFailure(err.message);
      }
    } catch (e) {
      onFailure(err.message);
    }
  };
}
