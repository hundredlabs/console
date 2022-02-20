import { Card, Table } from "antd";
import Column from "antd/lib/table/Column";
import * as React from "react";
import { Link } from "react-router-dom";
import { WorkspaceStatus, WorkspaceStatusTag } from "../../components/StatusTag/StatusTag";
import "./Workspace.scss";
import { UserContext } from "../../store/User";

interface WorkspaceView {
  id: string;
  name: string;
  workspaceType: string;
  status: WorkspaceStatus;
  provider: string;
  created: string;
}

export const WorkspaceGrid: React.FC<{ workspaces: Array<WorkspaceView> }> = ({ workspaces }) => {
  const renderCurrentStatus = (t: any, r: WorkspaceView, i: number) => {
    return (
      <div style={{ display: "flex", alignItems: "center" }}>
        <WorkspaceStatusTag status={r.status} />
      </div>
    );
  };
  return (
    <div className='workspace-wrapper'>
      <Card
        title='Workspaces'
        className='card-shadow-light'
        bordered={false}
        extra={
          <Link to='/workspaces/create' className='ant-btn ant-btn-primary'>
            Create
          </Link>
        }>
        <Table dataSource={workspaces} rowKey={(r: WorkspaceView) => r.id} pagination={false} className='tbl-cluster'>
          <Column title='NAME' dataIndex='name' key='id' className='table-cell-light' />
          <Column title='STATUS' dataIndex='status' key='id' className='table-cell-light' render={renderCurrentStatus} />
          <Column title='TYPE' dataIndex='workspaceType' key='id' className='table-cell-light' />
          <Column title='PROVIDER' dataIndex='provider' key='id' className='table-cell-light' />
          <Column title='CREATED' dataIndex='created' key='created' className='table-cell-light' />
        </Table>
      </Card>
    </div>
  );
};
export const WorkspaceOverview: React.FC<{ slugId: string }> = ({ slugId }) => {
  const context = React.useContext(UserContext);

  const workspaces: WorkspaceView[] = [
    {
      id: "1",
      name: "customer-segments-cluster",
      workspaceType: "kubernetes",
      provider: "AWS",
      status: "ACTIVE",
      created: "2 days ago",
    },
    {
      id: "2",
      name: "prod-data-quality",
      workspaceType: "EMR",
      provider: "AWS",
      status: "TERMINATED",
      created: "10 days ago",
    },
    {
      id: "3",
      name: "customer-marketing-cluster",
      workspaceType: "kubernetes",
      provider: "GCP",
      status: "TERMINATING",
      created: "2 days ago",
    },
  ];
  return (
    <div>
      <WorkspaceGrid workspaces={workspaces} />
    </div>
  );
};
