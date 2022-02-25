import { Skeleton, Table, Tag } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC, useState, useEffect } from "react";
import { ClusterStatus } from "../../../services/Workspace";
import workspace, { KafkaClusterBrokers } from "../../../services/Workspace";
import "./ConsumerGroups.scss";

type ConsumerState = "Stable" | "Empty" | "Unknown" | "Dead" | "PreparingRebalance" | "CompletingRebalance";

export interface ConsumerGroups {
  id: string;
  state: ConsumerState;
  coodinator: number;
  member: number;
  lag: string;
}
export interface ConsumerInfoTable {
  id: string;
  partition: number;
  assignedMemeber: string;
  host: string;
  logEndOffset: number;
  groupOffset: number;
  lag: number;
}

export const ConsumerState: FC<{ state: ConsumerState }> = ({ state }) => {
  let view = <div></div>;
  switch (state) {
    case "Stable":
      view = <Tag color='green'>{state}</Tag>;
      break;
    case "Dead":
      view = <Tag>{state}</Tag>;
      break;
    case "Unknown":
      view = <Tag>{state}</Tag>;
      break;
    case "PreparingRebalance":
      view = <Tag>{state}</Tag>;
      break;
    case "CompletingRebalance":
      view = <Tag>{state}</Tag>;
      break;
    case "Empty":
      view = <Tag color='orange'>{state}</Tag>;
      break;
    default:
      break;
  }
  return view;
};

const ConsumerInfoTable: FC<{ consumerId: string }> = ({ consumerId }) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [consumerInfo, setConsumerInfo] = useState<ConsumerInfoTable[]>([
    {
      id: "23",
      partition: 2,
      assignedMemeber: "sdgsd33",
      host: "sdfsdfsd-sdf-sdfsd",
      logEndOffset: 842255,
      groupOffset: 52555,
      lag: 3,
    },
  ]);

  useEffect(() => {
    //   workspace.getKafkaConsumerGroup(clusterId, (r) => {
    //     setAllConsumerGroup(r);
    //   });
  }, []);
  return (
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={consumerInfo}
        rowKey={(c: ConsumerInfoTable) => c.id}
        pagination={false}
        locale={{
          emptyText: "Consumer Info not found!",
        }}
        className='consumer-info-table tbl-applications'
        style={{ backgroundColor: "#fff" }}>
        <Column width='10%' title='PARTITION' dataIndex='partition' key='id' className='table-cell-light' />
        <Column width='20%' title='ASSIGNED MEMEBER' dataIndex='assignedMemeber' key='id' className='table-cell-light' />
        <Column width='40%' title='HOST' dataIndex='host' key='id' className='table-cell-light' />

        <Column width='10%' title='LOG END OFFSET' dataIndex='logEndOffset' key='id' className='table-cell-light' />
        <Column width='10%' title='GROUP OFFSET' dataIndex='groupOffset' key='id' className='table-cell-light' />
        <Column width='10%' title='LAG' dataIndex='lag' key='id' className='table-cell-light' />
      </Table>
    </Skeleton>
  );
};

const ConsumerGroups: FC<{
  orgSlugId: string;
  workspaceId: number;
  clusterId: number;
  status?: ClusterStatus;
}> = ({ orgSlugId, workspaceId, clusterId, status }) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [allConsumerGroups, setAllConsumerGroup] = useState<ConsumerGroups[]>([
    {
      id: "sdfsdf1sdfs",
      state: "Stable",
      coodinator: 3,
      member: 4,
      lag: "0",
    },
  ]);

  useEffect(() => {
    if (status && status === "running") {
      //   workspace.getKafkaConsumerGroup(clusterId, (r) => {
      //     setAllConsumerGroup(r);
      //   });
    }
  }, [status]);

  return (
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={status && status === "running" ? allConsumerGroups : [...allConsumerGroups]}
        rowKey={(c: ConsumerGroups) => c.id}
        expandable={{
          expandedRowRender: (record) => <ConsumerInfoTable consumerId={record.id} />,
          expandIconColumnIndex: 4,
        }}
        pagination={{ size: "small", total: allConsumerGroups.length }}
        locale={{
          emptyText: `${status && status !== "running" ? "Cluster is not running. Start the cluster to view the Consumer Groups" : "Consumer Groups not found!"}`,
        }}
        className='jobs-container tbl-applications consumer-groups-table'
        style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
        <Column width='10%' title='STATE' dataIndex='' key='id' render={(row) => <ConsumerState state={row.state} />} className='table-cell-light' />
        <Column width='50%' title='ID' dataIndex='id' key='id' className='table-cell-light' />
        <Column title='COODINATOR' dataIndex='' render={(row) => <span className='coodinator-value-box'>{row.coodinator}</span>} key='id' className='table-cell-light' />

        <Column width='15%' title='LAG(SUM)' dataIndex='lag' key='id' className='table-cell-light' />
        <Column width='15%' title='MEMBER' dataIndex='member' key='id' className='table-cell-light' />
      </Table>
    </Skeleton>
  );
};

export default ConsumerGroups;
