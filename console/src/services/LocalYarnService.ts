import WebService from "./WebService";
import { IErrorHandler, ErrResponse } from "./IErrorHander";
export interface YarnNodeStats {
  id: string;
  usedMemoryMB: number;
  availMemoryMB: number;
  usedVirtualCores: number;
  availableVirtualCores: number;
}
export interface YarnClusterMetrics {
  appsCompleted: number;
  appsRunning: number;
  appsPending: number;
  appsKilled: number;
}

export interface YarnNodesResponse {
  nodes: {
    node: YarnNodeStats[];
  };
}

export interface YarnClusterMetricsResponse {
  clusterMetrics: YarnClusterMetrics;
}

export const YarnConstants = {
  resourceManager: "Resource Manager",
  hdfsWeb: "HDFS Web UI",
  dataNode: "Data Node",
  hdfsService: "HDFS Servcie",
};

export class LocalYarnService extends IErrorHandler {
  protected webAPI: WebService;

  constructor(port: number) {
    super();
    this.webAPI = new WebService("localhost", port, "http", false);
  }

  getYarnClusterMetrics = async (
    onSuccess: (clusterMetrics: YarnClusterMetricsResponse, nodes: YarnNodesResponse) => void,
    onFailure: (err: string) => void
  ) => {
    const err = this.getDefaultError("Fetching Yarn Cluster stats");
    try {
      const metricResponse = this.webAPI.get<YarnClusterMetricsResponse>(`/ws/v1/cluster/metrics`);

      metricResponse
        .then((r) => {
          const metrics = r.parsedBody as YarnClusterMetricsResponse;
          const nodesResponse = this.webAPI.get<YarnNodesResponse>(`/ws/v1/cluster/nodes`);
          nodesResponse
            .then((n) => {
              const nodes = n.parsedBody as YarnNodesResponse;
              onSuccess(metrics, nodes);
            })
            .catch((err) => {
              onFailure(err.message);
            });
        })
        .catch((err) => {
          onFailure(err.message);
        });

      // const r = await metricResponse;
      // const n = await nodesResponse;
      // if (r.status === 200 && r.parsedBody && n.status === 200 && n.parsedBody) {
      //   const metrics = r.parsedBody as YarnClusterMetricsResponse;
      //   const nodes = n.parsedBody as YarnNodesResponse;
      //   onSuccess(metrics, nodes);
      // } else {
      //   onFailure(err.message);
      // }
    } catch (e) {
      onFailure(err.message);
    }
  };
}
