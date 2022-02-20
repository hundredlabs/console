import { Skeleton, Space, Table } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC, useState, useEffect, useContext } from "react";
import { ClusterStatus } from "../../../services/Workspace";
import { bytesToSize } from "../../../services/Utils";
import Workspace, { KafkaPartitions, KafkaClusterTopic } from "../../../services/Workspace";
import { UserContext } from "../../../store/User";

const Partitions: FC<{ partitions: KafkaPartitions[] }> = ({ partitions }) => {
  return (
    <div>
      <Table
        dataSource={partitions}
        rowKey={(c: KafkaPartitions) => c.partitions}
        pagination={false}
        locale={{ emptyText: `${"running" !== "running" ? "Cluster is not running!" : "No topics were!"}` }}
        className='partitions-tbl-container'
        style={{ backgroundColor: "#fff" }}>
        <Column
          title='ID'
          dataIndex=''
          key='partitions'
          render={(p: KafkaPartitions, row, index: number) => <div>{index + 1}</div>}
          className='table-cell-light'
        />
        <Column title='LEADER' dataIndex='leader' key='partitions' className='table-cell-light' />
        <Column
          title='REPLICAS'
          dataIndex=''
          key='partitions'
          render={(t: KafkaPartitions) => <div>{t.replicas.length}</div>}
          className='table-cell-light'
        />
      </Table>
    </div>
  );
};

export const KafkaTopicsTable: FC<{
  orgSlugId: string;
  workspaceId: number;
  clusterId: number;
  hasTopicCreated: boolean;
  status?: ClusterStatus;
  onSelectTopic: (name: string) => void;
}> = ({ orgSlugId, workspaceId, hasTopicCreated, clusterId, status, onSelectTopic }) => {
  const context = useContext(UserContext);

  const [loading, setLoading] = useState<boolean>(false);
  const [allTopics, setAllTopics] = useState<KafkaClusterTopic[]>([]);

  useEffect(() => {
    if (status && status === "running") {
      Workspace.getKafkaClusterTopic(clusterId, (r) => {
        setAllTopics(r);
      });
    }
  }, [status, hasTopicCreated]);

  return (
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={status && status === "running" ? allTopics : []}
        rowKey={(c: KafkaClusterTopic) => c.id}
        pagination={false}
        id='topic-tbl'
        locale={{
          emptyText: `${
            status && status !== "running" ? "Cluster is not running. Start the cluster to view the topics" : "No topics were created!"
          }`,
        }}
        className='jobs-container tbl-applications topic-list-table'
        style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
        <Column
          title='NAME'
          dataIndex=''
          render={(row: KafkaClusterTopic) => (
            <div className='topic-name' onClick={() => onSelectTopic(row.name)}>
              {row.name}
            </div>
          )}
          key='id'
          className='table-cell-light'
        />

        <Column
          title='PARTITIONS'
          dataIndex=''
          render={(rec: KafkaClusterTopic) => <>{rec.partitions.length}</>}
          key='id'
          className='table-cell-light'
        />
        <Column
          title='REPLICATIONS'
          dataIndex=''
          render={(rec: KafkaClusterTopic) => <>{rec.replications.length}</>}
          key='id'
          className='table-cell-light'
        />
        <Column title='MESSAGES' dataIndex='messages' key='id' className='table-cell-light' />
        <Column
          title='SIZE'
          dataIndex=''
          key='id'
          render={(rec: KafkaClusterTopic) => <>{bytesToSize(rec.size)}</>}
          className='table-cell-light'
        />
      </Table>
    </Skeleton>
  );
};
