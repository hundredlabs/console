import WebService from "../WebService";
import { IErrorHandler } from "../IErrorHander";
import { UnAuthorized } from "../Workspace";
import { IllegalParam, InternalServerError } from "../SparkService";

export type SparkProcessStatus = "ALIVE" | "STANDBY" | "NOT-STARTED";

export interface SparkMasterStats {
  url: string;
  status: SparkProcessStatus;
  coresAvailable: number;
  aliveWorkers: number;
  coresUsed: number;
  memoryAvailable: string;
  memoryUsed: string;
  workers: Array<SparkWorker>;
}
export interface SparkWorker {
  workerId: string;
  address: string;
  status: SparkProcessStatus;
  coresAvailable: number;
  coresUsed: number;
  memoryAvailable: number;
  memoryUsed: number;
}

class SparkService extends IErrorHandler {
  private webAPI: WebService = new WebService();
  getSparkSummaryDetails = async (cId: number, version: string, onSuccess: (sparkMasterStats: SparkMasterStats[]) => void) => {
    try {
      const response = this.webAPI.get<SparkMasterStats[] | IllegalParam | UnAuthorized | InternalServerError>(
        `/web/v1/spark/${cId}/master/version/${version}`
      );

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkMasterStats[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the spark summary");
        this.showError(err.message);
      }
    } catch (e) {}
  };
}

export default new SparkService();
