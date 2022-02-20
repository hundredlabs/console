import { Tabs, Row, Col, Breadcrumb, Divider, Select, Space, Table, Menu } from "antd";
import React, { FC, useEffect, useState } from "react";
import { ClusterStatus } from "../../../services/Workspace";
import "./TopicsDetails.scss";
import ReactJsonView from "react-json-view";
import { MdCalendarViewMonth, MdEmail, MdSettings } from "react-icons/md";
import Workspace, { PartitionDetails, TopicConfigDetail, TopicMessage } from "../../../services/Workspace";
import Column from "antd/lib/table/Column";
import moment from "moment";
const { TabPane } = Tabs;
const { Option } = Select;

type TabView = "message" | "partitions" | "summary" | "configurations";

const RowExpandable: FC<{ record: any }> = () => {
  return (
    <div className='expandable-container'>
      <div className='key-value-stat'>
        <Space size={40}>
          <div>
            <div className='key'>Key</div>
            <div className='label'>Text(36B)</div>
          </div>
          <div>
            <div className='key-label'>Value</div>
            <div className='label'>json (217 B)</div>
          </div>
          <div>
            <div className='key-label'>Header</div>
            <div className='label'>1</div>
          </div>
          <div>
            <div className='key-label'>Compression</div>
            <div className='label'>snappy</div>
          </div>
          <div>
            <div className='key-label'>Transactional</div>
            <div className='label'>false</div>
          </div>
        </Space>
        <Divider style={{ margin: "10px 0 0 0" }} />
      </div>
      <Tabs defaultActiveKey='2' onChange={() => {}}>
        <TabPane tab='Key' key='1'></TabPane>
        <TabPane tab='Value' key='2'>
          <ReactJsonView src={{ name: "Shadab" }} />
        </TabPane>
        <TabPane tab='Headers' key='3'>
          Content of Tab Pane 3
        </TabPane>
      </Tabs>
    </div>
  );
};

const TopicMessages: FC<{ clusterId: number; topic: string; deselectTopic: () => void; status?: ClusterStatus }> = ({
  clusterId,
  topic,
  deselectTopic,
  status,
}) => {
  const [topicMessages, setTopicMessages] = useState<TopicMessage[]>([]);
  useEffect(() => {
    Workspace.getKafkaTopicMessages(clusterId, topic, (r) => {
      setTopicMessages(r);
    });
  }, []);
  return (
    <Row>
      <Col span={24}>
        <Breadcrumb separator='>' className='topic-breadcrumb'>
          <Breadcrumb.Item onClick={() => deselectTopic()}>
            <span style={{ color: "#5f72f2", cursor: "pointer" }}>Topics</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>{topic}</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>Messages</span>
          </Breadcrumb.Item>
        </Breadcrumb>
        <Divider style={{ margin: "0" }} />
      </Col>
      <Col span={24} className='topic-message-list'>
        <Table
          dataSource={status && status === "running" ? topicMessages : []}
          rowKey={(c: TopicMessage) => `${c.offset}-${c.partition}`}
          pagination={false}
          locale={{
            emptyText: `${
              status && status !== "running" ? "Cluster is not running. Start the cluster to view the messages" : "No messages found."
            }`,
          }}
          className='jobs-container tbl-applications'>
          <Column title='OFFSET' dataIndex='offset' key='timestamp' className='table-cell-light' />

          <Column title='PARTITION' dataIndex='partition' key='timestamp' className='table-cell-light' />
          <Column
            title='TIMESTAMP'
            dataIndex=''
            key='timestamp'
            className='table-cell-light'
            render={(r: TopicMessage) => <span>{moment(r.timestamp).format("DD-MM-YYYY HH:mm:ss.SSS")}</span>}
          />
          <Column title='KEY' dataIndex='key' key='timestamp' className='table-cell-light' />
          <Column title='VALUE' dataIndex='message' key='timestamp' className='table-cell-light' />
        </Table>
      </Col>
    </Row>
  );
};
const TopicConfigList: FC<{ clusterId: number; topic: string; deselectTopic: () => void; status?: ClusterStatus }> = ({
  clusterId,
  topic,
  deselectTopic,
  status,
}) => {
  const [topicConfigs, setTopicConfigs] = useState<TopicConfigDetail[]>([]);
  useEffect(() => {
    Workspace.getKafkaTopicConfigs(clusterId, topic, (r) => {
      setTopicConfigs(r);
    });
  }, []);

  return (
    <Row>
      <Col span={24}>
        <Breadcrumb separator='>' className='topic-breadcrumb'>
          <Breadcrumb.Item onClick={() => deselectTopic()}>
            <span style={{ color: "#5f72f2", cursor: "pointer" }}>Topics</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>{topic}</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>Partitions</span>
          </Breadcrumb.Item>
        </Breadcrumb>
        <Divider style={{ margin: "0" }} />
      </Col>
      <Col span={24} className='topic-partitions-list'>
        <Table
          dataSource={status && status === "running" ? topicConfigs : []}
          rowKey={(c: TopicConfigDetail) => c.config}
          pagination={false}
          id='topic-tbl'
          locale={{
            emptyText: `${
              status && status !== "running"
                ? "Cluster is not running. Start the cluster to view the topic configurations"
                : "No configurations found."
            }`,
          }}
          className='jobs-container tbl-applications partition-list-table'
          style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
          <Column title='CONFIG' dataIndex='config' key='id' className='table-cell-light' />
          <Column title='VALUE' dataIndex='value' key='id' className='table-cell-light' />
          <Column title='TYPE' dataIndex='type' key='id' className='table-cell-light' />
          <Column title='SOURCE' dataIndex='source' key='id' className='table-cell-light' />
        </Table>
      </Col>
    </Row>
  );
};

const TopicPartitions: FC<{ clusterId: number; topic: string; deselectTopic: () => void; status?: ClusterStatus }> = ({
  clusterId,
  topic,
  deselectTopic,
  status,
}) => {
  const [topicPartitions, setTopicPartitions] = useState<PartitionDetails[]>([]);
  useEffect(() => {
    Workspace.getKafkaTopicPartitions(clusterId, topic, (r) => {
      setTopicPartitions(r);
    });
  }, []);

  return (
    <Row>
      <Col span={24}>
        <Breadcrumb separator='>' className='topic-breadcrumb'>
          <Breadcrumb.Item onClick={() => deselectTopic()}>
            <span style={{ color: "#5f72f2", cursor: "pointer" }}>Topics</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>{topic}</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>Partitions</span>
          </Breadcrumb.Item>
        </Breadcrumb>
        <Divider style={{ margin: "0" }} />
      </Col>
      <Col span={24} className='topic-partitions-list'>
        <Table
          dataSource={status && status === "running" ? topicPartitions : []}
          rowKey={(c: PartitionDetails) => c.id}
          pagination={false}
          id='topic-tbl'
          locale={{
            emptyText: `${
              status && status !== "running" ? "Cluster is not running. Start the cluster to view the topics" : "No partitions found."
            }`,
          }}
          className='jobs-container tbl-applications partition-list-table'
          style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
          <Column title='PARTITION ID' dataIndex='id' key='id' className='table-cell-light' />

          <Column title='STARTING OFFSET' dataIndex='startingOffset' key='id' className='table-cell-light' />
          <Column title='ENDING OFFSET' dataIndex='endingOffset' key='id' className='table-cell-light' />
          <Column title='MESSAGES' dataIndex='messages' key='id' className='table-cell-light' />
          <Column
            title='BROKERS'
            dataIndex=''
            key='id'
            render={(rec: PartitionDetails) => (
              <Space>
                {rec.replicas.map((v) => (
                  <span key={v} className='broker-id'>
                    {v}
                  </span>
                ))}
              </Space>
            )}
            className='table-cell-light'
          />
        </Table>
      </Col>
    </Row>
  );
};
const TopicSummary: FC<{ removeTopicId: (id: null) => void }> = ({ removeTopicId }) => {
  const [loading, setLoading] = useState<boolean>(false);

  return (
    <Row>
      <Col span={24}>
        <Breadcrumb separator='>' className='topic-breadcrumb'>
          <Breadcrumb.Item onClick={() => removeTopicId(null)}>
            <span style={{ color: "#5f72f2", cursor: "pointer" }}>Topics</span>
          </Breadcrumb.Item>
          <Breadcrumb.Item>
            <span style={{ color: "#3b4a73" }}>topicName</span>
          </Breadcrumb.Item>
        </Breadcrumb>
        <Divider style={{ margin: "0" }} />
      </Col>
      <Col span={24} className='topic-partitions-list'>
        <>Empty</>
      </Col>
    </Row>
  );
};

const TopicsDetails: FC<{
  orgSlugId: string;
  workspaceId: number;
  clusterId: number;
  topic: string;
  status?: ClusterStatus;
  removeTopicId: () => void;
}> = ({ clusterId, topic, status, removeTopicId }) => {
  const [tabView, setTabView] = useState<TabView>("message");

  const switchTabView = (tabView: TabView) => {
    switch (tabView) {
      case "message":
        return <TopicMessages clusterId={clusterId} topic={topic} status={status} deselectTopic={removeTopicId} />;
      case "partitions":
        return <TopicPartitions clusterId={clusterId} topic={topic} status={status} deselectTopic={removeTopicId} />;
      case "configurations":
        return <TopicConfigList clusterId={clusterId} topic={topic} status={status} deselectTopic={removeTopicId} />;
      case "summary":
        return <TopicSummary removeTopicId={removeTopicId} />;
      default:
        return <TopicMessages clusterId={clusterId} topic={topic} status={status} deselectTopic={removeTopicId} />;
    }
  };

  return (
    <div className='topics-details-tabs'>
      <div className='topic-menu'>
        <Menu
          defaultSelectedKeys={["message"]}
          defaultOpenKeys={["message"]}
          mode='inline'
          onSelect={({ key }) => setTabView(key as TabView)}
          theme='light'>
          <Menu.Item key='message' icon={<MdEmail style={{ fontSize: 18 }} />}>
            Messages
          </Menu.Item>
          <Menu.Item key='partitions' icon={<MdCalendarViewMonth style={{ fontSize: 18 }} />}>
            Partitions
          </Menu.Item>
          <Menu.Item key='configurations' icon={<MdSettings style={{ fontSize: 18 }} />}>
            Configurations
          </Menu.Item>
        </Menu>
      </div>
      <div className='topic-menu-content'>{switchTabView(tabView)}</div>
    </div>
  );
};

export default TopicsDetails;
