import { Card, Skeleton, Space, Table } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC, useEffect, useState } from "react";
import { ClusterStatus } from "../../../services/Workspace";
import SparkService, { SparkMasterStats, SparkWorker } from "../../../services/SparkService/SparkService";
import { SparkProcessStatusTag } from "../../StatusTag/StatusTag";

const AliveWorkerList: FC<{ workers: SparkWorker[]; loading: boolean; status: ClusterStatus }> = ({ workers, loading, status }) => {
  return (
    <>
      <div className='worker-title'>WORKERS</div>
      <div className='spark-wrokers-list'>
        <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
          <Table
            dataSource={workers}
            rowKey={(w: SparkWorker) => w.workerId}
            pagination={false}
            locale={{ emptyText: `${status !== "running" ? "Cluster is not running!" : "No Broker is found!"}` }}
            className='jobs-container tbl-applications spark-summary-table'
            style={{ backgroundColor: "#fff" }}>
            <Column title='WORKER ID' dataIndex='workerId' key='id' className='table-cell-light' />
            <Column title='ADDRESS' dataIndex='address' key='id' className='table-cell-light' />
            <Column
              title='CORES'
              dataIndex=''
              key='id'
              render={(w: SparkWorker) => (
                <div>
                  {w.coresAvailable} {`(${w.coresUsed} Used)`}
                </div>
              )}
              className='table-cell-light'
            />
            <Column
              title='MEMEORY'
              dataIndex=''
              render={(w: SparkWorker) => (
                <div>
                  {w.coresUsed} GB {`(${w.memoryUsed} GB Used)`}
                </div>
              )}
              key='id'
              className='table-cell-light'
            />
          </Table>
        </Skeleton>
      </div>
    </>
  );
};

const SparkStatsCard: FC<{ stat: any; label: string }> = ({ stat, label }) => {
  return (
    <Card size='small' className='spark-stat-card'>
      <Space align='center' direction='vertical'>
        <div className='alive-cores'>{stat}</div>
        <div className='spark-stat-label'>{label}</div>
      </Space>
    </Card>
  );
};

const SparkMasterCard: FC<{ sparkMasterStat: SparkMasterStats; loading: boolean; status: ClusterStatus; isAlive: boolean }> = ({
  isAlive,
  sparkMasterStat,
  loading,
  status,
}) => {
  return (
    <Card
      size='small'
      title={
        <Space size={-8} align='center'>
          <SparkProcessStatusTag status={sparkMasterStat.status} /> <div className='master-address'>Master at {sparkMasterStat.url}</div>
        </Space>
      }
      className='spark-master-card'
      bordered={true}
      style={{ width: "100%" }}>
      <Space className='spark-stats-cards' size='large' wrap>
        <SparkStatsCard stat={sparkMasterStat.workers.filter((w) => w.status === "ALIVE").length} label='Alive Workers' />
        <SparkStatsCard stat={`${sparkMasterStat.coresUsed}/${sparkMasterStat.coresAvailable}`} label='Cores Used' />
        <SparkStatsCard
          stat={
            <div>
              {sparkMasterStat.memoryUsed.split(" ")[0].trim()}
              <span> {sparkMasterStat.memoryUsed.split(" ")[1].trim()}</span> / {sparkMasterStat.memoryAvailable.split(" ")[0].trim()}
              <span> {sparkMasterStat.memoryAvailable.split(" ")[1].trim()}</span>
            </div>
          }
          label='Memory Used'
        />
      </Space>

      {isAlive && <AliveWorkerList workers={sparkMasterStat.workers} loading={loading} status={status} />}
    </Card>
  );
};

const SparkSummary: FC<{
  orgSlugId: string;
  workspaceId: number;
  clusterId: number;
  version?: string;
  needFetch: boolean;
  status?: ClusterStatus;
}> = ({ orgSlugId, workspaceId, clusterId, version, needFetch, status }) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [sparkMasterStats, setSparkMasterStats] = useState<SparkMasterStats[]>([]);
  const [aliveSparkMaster, setAliveSparkMaster] = useState<SparkMasterStats[]>([]);

  useEffect(() => {
    if (status && version && status === "running") {
      SparkService.getSparkSummaryDetails(clusterId, version, (r) => {
        const aliveMaster = r.filter((s, i) => s.status === "ALIVE");
        const standbyMaster = r.filter((s, i) => s.status !== "ALIVE");
        setAliveSparkMaster(aliveMaster);
        setSparkMasterStats(standbyMaster);
      });
    }
  }, [status, needFetch]);

  return (
    <div className='spark-summary-wrapper'>
      {status && status !== "running" && <div>Cluster is not in the running state.</div>}
      {status && status === "running" && aliveSparkMaster.length > 0 && (
        <>
          {aliveSparkMaster.map((m, i) => (
            <SparkMasterCard key={i} sparkMasterStat={m} isAlive={true} loading={loading} status={status} />
          ))}
        </>
      )}
    </div>
  );
};

export default SparkSummary;
