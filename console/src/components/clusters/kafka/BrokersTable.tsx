import { Skeleton, Table } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC, useState, useEffect } from "react";
import { ClusterStatus } from "../../../services/Workspace";
import workspace, { KafkaClusterBrokers } from "../../../services/Workspace";

const KafkaBrokersTable: FC<{
  orgSlugId: string;
  workspaceId: number;
  clusterId: number;
  status?: ClusterStatus;
}> = ({ orgSlugId, workspaceId, clusterId, status }) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [allBrokers, setAllBrokers] = useState<KafkaClusterBrokers[]>([]);

  useEffect(() => {
    if (status && status === "running") {
      workspace.getKafkaClusterBrokers(clusterId, (r) => {
        setAllBrokers(r);
      });
    }
  }, [status]);

  return (
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <Table
        dataSource={status && status === "running" ? allBrokers : []}
        rowKey={(c: KafkaClusterBrokers) => c.id}
        pagination={false}
        locale={{
          emptyText: `${
            status && status !== "running" ? "Cluster is not running. Start the cluster to view the brokers" : "No Brokers found!"
          }`,
        }}
        className='jobs-container tbl-applications'
        style={{ minHeight: "50vh", backgroundColor: "#fff" }}>
        <Column title='ID' dataIndex='id' key='id' className='table-cell-light' />
        <Column
          title='ADDRESS'
          dataIndex=''
          key='id'
          render={(b: KafkaClusterBrokers) => (
            <div>
              {b.host}:{b.port}
            </div>
          )}
          className='table-cell-light'
        />

        <Column title='RACK' dataIndex='rack' key='id' className='table-cell-light' />
      </Table>
    </Skeleton>
  );
};

export default KafkaBrokersTable;
