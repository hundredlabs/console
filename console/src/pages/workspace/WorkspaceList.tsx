import React, { useState, useEffect } from "react";
import { Skeleton, Table, Card } from "antd";
import Workspace, { WorkspaceMiniView } from "../../services/Workspace";
const { Column } = Table;

const WorkspaceList: React.FC<{}> = () => {
  const [workspaceState, setWorkspaces] = useState<{ workspaces: Array<WorkspaceMiniView>; loading: boolean }>({
    loading: true,
    workspaces: [],
  });

  useEffect(() => {
    Workspace.listWorkspaces((r) => {
      setWorkspaces({ workspaces: r, loading: false });
    });
  }, []);
  return (
    <div className='workspace-wrapper'>
      <Card title='Workspaces' className='card-shadow-light' bordered={false}>
        <Skeleton loading={workspaceState.loading} active paragraph={{ rows: 10 }}>
          <Table dataSource={workspaceState.workspaces} rowKey={(r: WorkspaceMiniView) => r.id} pagination={false} className='tbl-cluster'>
            <Column title='NAME' dataIndex='name' key='name' className='table-cell-light' />
            <Column title='CREATED' dataIndex='created' key='created' className='table-cell-light' />
          </Table>
        </Skeleton>
      </Card>
    </div>
  );
};

export default WorkspaceList;
