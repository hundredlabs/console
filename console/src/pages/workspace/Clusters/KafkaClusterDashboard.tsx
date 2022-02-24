import { Skeleton, Tabs, Button, Modal, Form, Input, InputNumber, Alert } from "antd";
import React, { FC, useState, useEffect } from "react";
import "../WorkspaceDashboard/WorkspaceDashboard.scss";
import "../Workspace.scss";
import "./Clusters.scss";
import { SparkClusterMetric } from "../../../services/Workspace";

import ClusterHeader from "../../../components/clusters/ClusterHeader";
import { history } from "../../../configureStore";
import WebService from "../../../services/WebService";
import { bytesToSize, calculatePer } from "../../../services/Utils";

import Workspace from "../../../services/Workspace";
import KafkaBrokersTable from "../../../components/clusters/kafka/BrokersTable";
import { KafkaTopicsTable } from "../../../components/clusters/kafka/TopicTable";
import TopicsDetails from "../../../components/clusters/kafka/TopicsDetails";
import ConsumerGroups from "../../../components/clusters/kafka/ConsumerGroups";

const { TabPane } = Tabs;
export type activeTab = "broker" | "topic" | "consumer-groups";

export interface DownloadStatus {
  downPer: number;
  downValue: string;
  totalValue: string;
}

const KafkaClusterDashboard: FC<{ orgSlugId: string; workspaceId: number; clusterId: number }> = ({ orgSlugId, workspaceId, clusterId }) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [topicId, setTopicId] = useState<string>(null);

  const [topicBuilder, setTopicBuilder] = useState<{ showModal: boolean; hasTopicCreated: boolean }>({
    showModal: false,
    hasTopicCreated: true,
  });
  const [topicForm] = Form.useForm();

  const [clusterState, setClusterState] = useState<{ metric?: SparkClusterMetric; loading: boolean; showedFetchMetricsErr: boolean }>({
    loading: false,
    showedFetchMetricsErr: false,
  });
  const [tabsView, setTabsView] = useState<{ activeTab: activeTab }>({
    activeTab: "broker",
  });

  const [downloadStatus, setDownloadStatus] = React.useState<DownloadStatus>({
    downPer: 0,
    downValue: "",
    totalValue: "",
  });
  const onTabsChange = (v: string) => {
    setTabsView({ ...tabsView, activeTab: v as activeTab });
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
    Workspace.startCluster(clsId, "kafka", (r) => {});
  };
  const onClusterStop = (clsId: number) => {
    setClusterState((prv) => ({ ...prv, metric: { ...clusterState.metric, status: "terminating" } }));
    Workspace.stopCluster(clsId, (r) => {});
  };

  useEffect(() => {
    const web = new WebService();
    const ws = new WebSocket(`${web.getWSEndpoint()}/ws/kafka/${clusterId}`);
    Workspace.getKafkaClusterMetric(clusterId, (metric) => {
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
      if (
        (clusterState.metric?.status === "starting" && metric.status === "new") ||
        (clusterState.metric?.status === "starting" && metric.status === "terminated") ||
        (clusterState.metric?.status === "terminating" && metric.status === "running")
      ) {
        console.log("don't update");
      } else {
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
        } else {
          setClusterState((prv) => ({
            ...prv,
            loading: false,
            metric: metric,
          }));
        }
      }
    };
    return () => {
      ws && ws.close(1000);
    };
  }, []);

  const onCreateTopic = (topicInfo: any) => {
    setLoading(true);
    Workspace.createKafkaTopic(clusterId, topicInfo.name, topicInfo.partitions, topicInfo.replicas, (r) => {
      setLoading(false);
      setTopicBuilder({ showModal: false, hasTopicCreated: true });
    });
  };

  const onSelectTopic = (id: string) => {
    setTopicId(id);
  };

  const removeTopic = () => {
    setTopicId(null);
  };

  const onTabClick = (key: string) => {
    if (key === "topic") {
      setTopicId(null);
    }
  };

  return (
    <div className='workspace-wrapper dashboard-container'>
      <Skeleton avatar active loading={typeof clusterState.metric === "undefined"} paragraph={{ rows: 2 }}>
        {clusterState.metric && (
          <>
            <ClusterHeader
              serviceName='kafka'
              clusterId={clusterId}
              handleClusterDel={onClusterDelete}
              handleClusterStart={onClusterStart}
              handleClusterStop={onClusterStop}
              metric={clusterState.metric}
              downloadStatus={downloadStatus}
            />
            {clusterState.metric.status === "terminated_with_errors" && <Alert type='error' message={clusterState.metric.statusDetail} banner />}
          </>
        )}
      </Skeleton>

      <div className='tabs-section card-shadow-light' style={{ minHeight: "500px", backgroundColor: "#fff" }}>
        <Tabs
          defaultActiveKey='broker'
          activeKey={tabsView.activeTab}
          onTabClick={onTabClick}
          onChange={onTabsChange}
          className='jobs-tabs cluster-tabs'
          tabBarExtraContent={
            tabsView.activeTab === "topic" && (
              <Button type='primary' onClick={() => setTopicBuilder({ showModal: true, hasTopicCreated: false })} disabled={clusterState.metric && clusterState.metric.status !== "running"}>
                Create Topic
              </Button>
            )
          }>
          <TabPane tab='Brokers' key='broker' className='jobs-tab-pane' style={{ minHeight: "50vh" }}>
            {tabsView.activeTab === "broker" && <KafkaBrokersTable orgSlugId={orgSlugId} workspaceId={workspaceId} clusterId={clusterId} status={clusterState.metric?.status} />}
          </TabPane>
          <TabPane tab='Topics' key='topic' className='jobs-tab-pane' style={{ minHeight: "50vh" }}>
            {topicId === null && tabsView.activeTab === "topic" && (
              <KafkaTopicsTable
                orgSlugId={orgSlugId}
                workspaceId={workspaceId}
                clusterId={clusterId}
                hasTopicCreated={topicBuilder.hasTopicCreated}
                status={clusterState.metric?.status}
                onSelectTopic={onSelectTopic}
              />
            )}
            {topicId && <TopicsDetails orgSlugId={orgSlugId} workspaceId={workspaceId} clusterId={clusterId} status={clusterState.metric?.status} topic={topicId} removeTopicId={removeTopic} />}
          </TabPane>
          <TabPane tab='Consumer Groups' key='consumer-groups' className='jobs-tab-pane' style={{ minHeight: true ? "calc(100vh - 245px)" : "calc(100vh - 200px)" }}>
            <ConsumerGroups orgSlugId={orgSlugId} workspaceId={workspaceId} clusterId={clusterId} status={clusterState.metric?.status} />
          </TabPane>
        </Tabs>
        <Modal
          maskClosable={false}
          destroyOnClose={true}
          title='Create a Topic'
          visible={topicBuilder.showModal}
          footer={null}
          onCancel={() => {
            setTopicBuilder({ showModal: false, hasTopicCreated: false });
            topicForm.resetFields();
          }}>
          <Form layout={"vertical"} name='topic-form' requiredMark={false} initialValues={{ replicas: 1, partitions: 1 }} form={topicForm} onFinish={onCreateTopic} className='kafka-topic-form'>
            <Form.Item name='name' label='Name' rules={[{ required: true, message: "Please enter the Topic name" }]}>
              <Input placeholder='Name' />
            </Form.Item>
            <Form.Item name='partitions' label='Partitions' rules={[{ required: true, message: "Please enter the partitions , min:1 and max:10" }]}>
              <InputNumber style={{ width: "100%", border: "none" }} placeholder='Partitions' min={1} max={10} />
            </Form.Item>
            <Form.Item name='replicas' label='Replicas' rules={[{ required: true, message: "Please enter the replicas , min:1 and max:10" }]}>
              <InputNumber style={{ width: "100%", border: "none" }} placeholder='No of replicas' min={1} max={10} />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Button type='primary' loading={loading} htmlType='submit' style={{ float: "right" }} className='btn-action-light'>
                Create
              </Button>
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </div>
  );
};
export default KafkaClusterDashboard;
