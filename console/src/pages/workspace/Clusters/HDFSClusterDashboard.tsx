import { Alert, Button, Skeleton, Tabs } from "antd";
import React, { FC, useState, useEffect } from "react";
import "../WorkspaceDashboard/WorkspaceDashboard.scss";
import "../Workspace.scss";
import "./SparkDashboard.scss";
import { HDFSClusterMetric } from "../../../services/Workspace";
import HDFSStorageFile from "./HDFSStorageFile/HDFSStorageFile";

import ClusterHeader from "../../../components/clusters/ClusterHeader";
import { history } from "../../../configureStore";
import WebService from "../../../services/WebService";
import { bytesToSize, calculatePer, getLocalStorage } from "../../../services/Utils";
import Workspace from "../../../services/Workspace";
import SparkSummary from "../../../components/clusters/spark/SparkSummary";

const { TabPane } = Tabs;

export interface DownloadStatus {
  downPer: number;
  downValue: string;
  totalValue: string;
}
export type activeTab = "fs" | "summary";
const HDFSClusterDashboard: FC<{ orgSlugId: string; workspaceId: number; clusterId: number }> = ({ orgSlugId, workspaceId, clusterId }) => {
  const [clusterState, setClusterState] = useState<{
    metric?: HDFSClusterMetric;
    loading: boolean;
    showedFetchMetricsErr: boolean;
    needFetch: boolean;
  }>({
    loading: false,
    showedFetchMetricsErr: false,
    needFetch: false,
  });

  const [isExpand, setExpand] = React.useState<boolean>(getLocalStorage("isHeaderExpand") ?? true);

  const [clusterView, setclusterView] = useState<{ activeTab: activeTab }>({
    activeTab: "fs",
  });

  const [downloadStatus, setDownloadStatus] = React.useState<DownloadStatus>({
    downPer: 0,
    downValue: "",
    totalValue: "",
  });

  const onTabsChange = (v: string) => {
    setclusterView({ ...clusterView, activeTab: v as activeTab });
  };

  const onClusterDelete = (id: number) => {
    Workspace.deleteCluster(id, (r) => {
      if (r) {
        history.push(`/${orgSlugId}/workspace/${workspaceId}/clusters`);
      }
    });
  };

  const onClusterStart = (clsId: number) => {
    setClusterState((prv) => ({ ...prv, metric: { ...clusterState.metric, status: "starting" } }));
    Workspace.startCluster(clsId, "hadoop", (r) => {});
  };
  const onClusterStop = (clsId: number) => {
    setClusterState((prv) => ({ ...prv, metric: { ...clusterState.metric, status: "terminating" } }));
    Workspace.stopCluster(clsId, (r) => {});
  };

  useEffect(() => {
    const web = new WebService();
    const ws = new WebSocket(`${web.getWSEndpoint()}/ws/hadoop/${clusterId}`);
    Workspace.getHDFSClusterMetric(clusterId, (metric) => {
      setClusterState((prv) => ({
        ...prv,
        loading: false,
        metric: metric,
      }));
      if (metric.status === "downloading" && metric.statusDetail && metric.statusDetail !== "") {
        const splitNo: Array<string> = metric.statusDetail.split("/");
        if (Number(splitNo[0]) > 0 && Number(splitNo[1]) > 0) {
          const downPer = calculatePer(Number(splitNo[0]), Number(splitNo[1]));
          const downValue = bytesToSize(Number(splitNo[0]));
          const totalValue = bytesToSize(Number(splitNo[1]));
          setDownloadStatus({
            downPer: downPer,
            downValue: downValue,
            totalValue: totalValue,
          });
        }
      }
    });
    ws.onopen = function (event) {
      ws.send("");
    };
    ws.onmessage = (ev: MessageEvent) => {
      const metric = JSON.parse(ev.data) as HDFSClusterMetric;
      // console.log(metric);
      if (
        (clusterState.metric?.status === "starting" && metric.status === "new") ||
        (clusterState.metric?.status === "starting" && metric.status === "terminated") ||
        (clusterState.metric?.status === "terminating" && metric.status === "running")
      ) {
        console.log("don't update");
      } else {
        setClusterState((prv) => ({
          ...prv,
          loading: false,
          metric: metric,
        }));
        if (metric.status === "downloading" && metric.statusDetail && metric.statusDetail !== "") {
          const splitNo: Array<string> = metric.statusDetail.split("/");
          if (Number(splitNo[0]) > 0 && Number(splitNo[1]) > 0) {
            const downPer = calculatePer(Number(splitNo[0]), Number(splitNo[1]));
            const downValue = bytesToSize(Number(splitNo[0]));
            const totalValue = bytesToSize(Number(splitNo[1]));
            setDownloadStatus({
              downPer: downPer,
              downValue: downValue,
              totalValue: totalValue,
            });
          }
        }
      }
    };
    return () => {
      ws && ws.close(1000);
    };
  }, []);

  const getHeaderExpand = (isExpand: boolean) => {
    setExpand(isExpand);
  };

  return (
    <div className='workspace-wrapper dashboard-container'>
      <Skeleton avatar active loading={typeof clusterState.metric === "undefined"} paragraph={{ rows: 2 }}>
        {clusterState.metric && (
          <>
            <ClusterHeader
              serviceName='hadoop'
              clusterId={clusterId}
              handleClusterDel={onClusterDelete}
              handleClusterStart={onClusterStart}
              handleClusterStop={onClusterStop}
              metric={clusterState.metric}
              downloadStatus={downloadStatus}
              getHeaderExpand={getHeaderExpand}
            />
            {clusterState.metric.status === "terminated_with_errors" && <Alert type='error' message={clusterState.metric.statusDetail} banner />}
          </>
        )}
      </Skeleton>

      <div className='tabs-section card-shadow-light' style={{ minHeight: "500px", backgroundColor: "#fff" }}>
        <Tabs defaultActiveKey='fs' activeKey={clusterView.activeTab} onChange={onTabsChange} className='jobs-tabs cluster-tabs'>
          {clusterState.metric && clusterState.metric.status && (
            <TabPane tab='Browse HDFS' key='fs' className='jobs-tab-pane' style={{ height: isExpand ? "calc(100vh - 265px)" : "calc(100vh - 220px)" }}>
              <HDFSStorageFile activeTab={clusterView.activeTab} clusterId={clusterId} status={clusterState.metric.status} metric={clusterState.metric} />
            </TabPane>
          )}
        </Tabs>
      </div>
    </div>
  );
};
export default HDFSClusterDashboard;
