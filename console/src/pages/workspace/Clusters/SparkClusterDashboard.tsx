import { Alert, Button, Skeleton, Table, Tabs } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC, useState, useEffect } from "react";
import "../WorkspaceDashboard/WorkspaceDashboard.scss";
import "../Workspace.scss";
import "./SparkDashboard.scss";
import { StatusTag } from "../../../components/StatusTag/StatusTag";
import WorkspaceService, { SparkClusterMetric, SparkClusterHistory, ServicePort, ClusterAttempts } from "../../../services/Workspace";

import ClusterHeader from "../../../components/clusters/ClusterHeader";
import { history } from "../../../configureStore";
import WebService from "../../../services/WebService";
import { bytesToSize, getReadableTime, calculatePer } from "../../../services/Utils";
import type { ClusterStatus, Status } from "../../../services/Workspace";

import moment from "moment";
import Workspace from "../../../services/Workspace";
import SparkSummary from "../../../components/clusters/spark/SparkSummary";

const { TabPane } = Tabs;

export interface IAppCluster {
  id: string;
  name: string;
  user: string;
  state: any;
  finalStatus: any;
  runtime: string;
  containers: string;
  vCpu: string;
}
export interface DownloadStatus {
  downPer: number;
  downValue: string;
  totalValue: string;
}

const DeploymentHistoryTable: FC<{
  orgSlugId: string;
  workspaceId: number;
  clusterId: number;
  metric: SparkClusterMetric;
  selectedTab: activeTab;
  needFetch: boolean;
  containerState?: ClusterStatus;
  ports?: ServicePort[];
}> = ({ orgSlugId, workspaceId, clusterId, metric, needFetch, containerState, selectedTab, ports }) => {
  const web = new WebService();
  const [depHistory, setDepHistory] = useState<{ history: SparkClusterHistory[]; loading: boolean }>({
    history: [],
    loading: false,
  });

  const startedCluster = (v: ClusterAttempts[]) => {
    return moment(v[0].startTimeEpoch).fromNow();
  };

  const getSparkClusterRuntime = (a: ClusterAttempts[]) => {
    let ms;
    if (a[0].completed) {
      ms = a[0].endTimeEpoch - a[0].startTimeEpoch;
    } else {
      let endTime = 0;
      let startTime = a[0].startTimeEpoch;
      a.forEach((t) => {
        if (t.endTimeEpoch === -1) {
          endTime = moment().valueOf();
        } else if (t.endTimeEpoch > endTime) {
          endTime = t.endTimeEpoch;
        }
      });
      a.forEach((t) => {
        if (t.startTimeEpoch < startTime) {
          startTime = t.startTimeEpoch;
        }
      });
      ms = endTime - startTime;
    }
    return getReadableTime(ms);
  };

  useEffect(() => {
    if (metric.status === "running") {
      WorkspaceService.getSparkClustertHistory(clusterId, (r) => {
        setDepHistory((prv) => ({ ...prv, history: r }));
      });
    } else {
      setDepHistory((prv) => ({ ...prv, history: [] }));
    }
  }, [metric.status, selectedTab, needFetch]);

  const getHistoryStatus = (v: ClusterAttempts[]) => {
    let status: Status;
    v.map((c) => {
      if (c.completed) {
        status = "succeeded";
      } else {
        status = "running";
      }
    });
    return status;
  };
  return (
    <Skeleton loading={depHistory.loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={depHistory.history}
        rowKey={(c: SparkClusterHistory) => c.id}
        pagination={false}
        locale={{ emptyText: `${metric.status !== "running" ? "Cluster is not running!" : "No Application is found!"}` }}
        className='jobs-container tbl-applications'
        style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
        <Column
          title='ID'
          dataIndex=''
          key='id'
          className='table-cell-light'
          render={(v: SparkClusterHistory) => (
            <a href={`${web.getEndpoint()}/web/v1/spark/${clusterId}/redirect/history/${v.id}/jobs/`} target='_blank'>
              {v.id}
            </a>
          )}
        />
        <Column title='APP NAME' dataIndex='name' key='id' className='table-cell-light' />

        <Column
          title='STATUS'
          dataIndex=''
          key='status'
          className='table-cell-light'
          render={(v: SparkClusterHistory) => <StatusTag status={getHistoryStatus(v.attempts)} />}
        />

        <Column
          title='DATE STARTED'
          dataIndex=''
          key='started'
          className='table-cell-light'
          render={(v: SparkClusterHistory) => startedCluster(v.attempts)}
        />
        <Column
          title='RUNTIME'
          dataIndex=''
          align='center'
          key='runtime'
          className='table-cell-light'
          render={(v: SparkClusterHistory) => getSparkClusterRuntime(v.attempts)}
        />
      </Table>
    </Skeleton>
  );
};

export type activeTab = "history" | "summary";

export interface IContainer {
  containerId: string;
  state: ClusterStatus;
}
export interface IContainerState {
  environments: Array<string>;
  services: ServicePort[];
  image?: string;
  state: ClusterStatus;
  containerId?: string;
}

const SparkClusterDashboard: FC<{ orgSlugId: string; workspaceId: number; clusterId: number }> = ({
  orgSlugId,
  workspaceId,
  clusterId,
}) => {
  const [containerImage, setContainerImage] = useState<IContainerState>({
    environments: [],
    services: [],
    state: "inactive",
  });

  const [clusterState, setClusterState] = useState<{
    metric?: SparkClusterMetric;
    loading: boolean;
    showedFetchMetricsErr: boolean;
    needFetch: boolean;
  }>({
    loading: false,
    showedFetchMetricsErr: false,
    needFetch: false,
  });

  const [clusterView, setclusterView] = useState<{ activeTab: activeTab }>({
    activeTab: "summary",
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
    Workspace.startCluster(clsId, "spark", (r) => {});
  };
  const onClusterStop = (clsId: number) => {
    setClusterState((prv) => ({ ...prv, metric: { ...clusterState.metric, status: "terminating" } }));
    Workspace.stopCluster(clsId, (r) => {});
  };

  useEffect(() => {
    const web = new WebService();
    const ws = new WebSocket(`${web.getWSEndpoint()}/ws/spark/${clusterId}`);
    Workspace.getSparkClusterMetric(clusterId, (metric) => {
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
      const metric = JSON.parse(ev.data) as SparkClusterMetric;
      console.log(metric);
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

  return (
    <div className='workspace-wrapper dashboard-container'>
      <Skeleton avatar active loading={typeof clusterState.metric === "undefined"} paragraph={{ rows: 2 }}>
        {clusterState.metric && (
          <>
            <ClusterHeader
              serviceName='spark'
              clusterId={clusterId}
              handleClusterDel={onClusterDelete}
              handleClusterStart={onClusterStart}
              handleClusterStop={onClusterStop}
              metric={clusterState.metric}
              downloadStatus={downloadStatus}
            />
            {clusterState.metric.status === "terminated_with_errors" && (
              <Alert type='error' message={clusterState.metric.statusDetail} banner />
            )}
          </>
        )}
      </Skeleton>

      <div className='tabs-section card-shadow-light' style={{ minHeight: "500px", backgroundColor: "#fff" }}>
        <Tabs
          defaultActiveKey='summary'
          activeKey={clusterView.activeTab}
          onChange={onTabsChange}
          className='jobs-tabs cluster-tabs'
          tabBarExtraContent={
            <Button
              type='primary'
              onClick={() => setClusterState((prv) => ({ ...prv, metric: { ...prv.metric }, needFetch: !prv.needFetch }))}
              disabled={clusterState.metric && clusterState.metric.status !== "running"}>
              Refresh
            </Button>
          }>
          <TabPane tab='Summary' key='summary' className='jobs-tab-pane'>
            {clusterState.metric && clusterState.metric.status && clusterView.activeTab === "summary" && (
              <SparkSummary
                orgSlugId={orgSlugId}
                workspaceId={workspaceId}
                clusterId={clusterId}
                status={clusterState.metric.status}
                version={clusterState.metric.version}
                needFetch={clusterState.needFetch}
              />
            )}
          </TabPane>
          <TabPane tab='History' key='history' className='jobs-tab-pane'>
            {clusterState.metric && clusterState.metric.status && clusterView.activeTab === "history" && (
              <DeploymentHistoryTable
                orgSlugId={orgSlugId}
                workspaceId={workspaceId}
                clusterId={clusterId}
                metric={clusterState.metric}
                needFetch={clusterState.needFetch}
                selectedTab={clusterView.activeTab}
                containerState={containerImage.state}
                ports={containerImage.services}
              />
            )}
            {clusterState.metric && typeof clusterState.metric.status === "undefined" && (
              <DeploymentHistoryTable
                orgSlugId={orgSlugId}
                workspaceId={workspaceId}
                clusterId={0}
                needFetch={clusterState.needFetch}
                metric={clusterState.metric}
                selectedTab={clusterView.activeTab}
              />
            )}
          </TabPane>
        </Tabs>
      </div>
    </div>
  );
};
export default SparkClusterDashboard;
