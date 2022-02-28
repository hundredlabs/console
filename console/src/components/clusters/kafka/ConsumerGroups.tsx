import { Skeleton, Table, Tag } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC, useState, useEffect } from "react";
import Workspace, { ClusterStatus, ConsumerGroupInfo, ConsumerMember } from "../../../services/Workspace";
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

const ConsumerInfoTable: FC<{ members: ConsumerMember[] }> = ({ members }) => {
  const [loading, setLoading] = useState<boolean>(false);

  return (
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={members}
        rowKey={(c: ConsumerMember) => c.partition}
        pagination={false}
        locale={{
          emptyText: "Consumer Info not found!",
        }}
        className='consumer-info-table tbl-applications'
        style={{ backgroundColor: "#fff" }}>
        <Column width='10%' title='PARTITION' dataIndex='partition' key='id' className='table-cell-light' />
        <Column width='20%' title='ASSIGNED MEMEBER' dataIndex='assignedMember' key='id' className='table-cell-light' />
        <Column width='40%' title='HOST' dataIndex='host' key='id' className='table-cell-light' />

        <Column width='10%' title='LOG END OFFSET' dataIndex='topicPartitionOffset' key='id' className='table-cell-light' />
        <Column width='10%' title='GROUP OFFSET' dataIndex='consumedOffset' key='id' className='table-cell-light' />
        <Column
          width='10%'
          title='LAG'
          dataIndex=''
          render={(r: ConsumerMember) => <>{r.topicPartitionOffset - r.consumedOffset}</>}
          key='id'
          className='table-cell-light'
        />
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
  const [allConsumerGroups, setAllConsumerGroup] = useState<ConsumerGroupInfo[]>([]);

  useEffect(() => {
    if (status && status === "running") {
      Workspace.getKafkaConsumerGroups(clusterId, (r) => {
        setAllConsumerGroup(r);
      });
    }
  }, [status]);

  return (
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={status && status === "running" ? allConsumerGroups : [...allConsumerGroups]}
        rowKey={(c: ConsumerGroupInfo) => c.id}
        expandable={{
          expandedRowRender: (record) => <ConsumerInfoTable members={record.members} />,
          expandIconColumnIndex: 4,
        }}
        pagination={{ size: "small", total: allConsumerGroups.length }}
        locale={{
          emptyText: `${
            status && status !== "running"
              ? "Cluster is not running. Start the cluster to view the Consumer Groups"
              : "Consumer Groups not found!"
          }`,
        }}
        className='jobs-container tbl-applications consumer-groups-table'
        style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
        <Column
          width='10%'
          title='STATE'
          dataIndex=''
          key='id'
          render={(row) => <ConsumerState state={row.state} />}
          className='table-cell-light'
        />
        <Column width='50%' title='ID' dataIndex='id' key='id' className='table-cell-light' />
        <Column
          title='COODINATOR'
          dataIndex=''
          render={(row) => <span className='coodinator-value-box'>{row.coordinator}</span>}
          key='id'
          className='table-cell-light'
        />

        <Column width='15%' title='LAG(SUM)' dataIndex='lag' key='id' className='table-cell-light' />
        <Column
          width='15%'
          title='MEMBERS'
          render={(row: ConsumerGroupInfo) => <span className='coodinator-value-box'>{row.members.length}</span>}
          dataIndex=''
          key='id'
          className='table-cell-light'
        />
      </Table>
    </Skeleton>
  );
};

export default ConsumerGroups;
