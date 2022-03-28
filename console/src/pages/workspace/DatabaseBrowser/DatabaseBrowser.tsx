import { Row, Col, Button, Menu, message, Dropdown, Space, Table, Card, Skeleton, Breadcrumb, Modal, Input, Tree } from "antd";
import React, { FC, ReactNode } from "react";
import { FaDatabase } from "react-icons/fa";
import { BiPlay, BiCaretDown } from "react-icons/bi";
import { AiOutlineTable } from "react-icons/ai";
import { MdPlayArrow } from "react-icons/md";
import Editor from "@monaco-editor/react";
import "./DatabaseBrowser.scss";
import { MailOutlined } from "@ant-design/icons";
import CustomScroll from "react-custom-scroll";

const { Column } = Table;
const { SubMenu } = Menu;
const { DirectoryTree } = Tree;

const DBBrowserHeader: FC<{ clickQueryTo: () => void; onClickToRun: () => void }> = ({ clickQueryTo, onClickToRun }) => {
  function handleButtonClick(e: any) {
    message.info("Click on left button.");
    console.log("click left button", e);
  }

  const menu = (
    <Menu>
      <Menu.Item key='1' onClick={clickQueryTo}>
        Save as
      </Menu.Item>
    </Menu>
  );
  return (
    <div className='db-browser-header'>
      <div className='left-container'>
        <Space align='center' size={5}>
          <i className='db-icon'>
            <FaDatabase style={{ marginTop: 5 }} />
          </i>
          <span className='db-label'>Database</span>
        </Space>
      </div>
      <div className='right-container'>
        <div>
          <Breadcrumb separator='>'>
            <Breadcrumb.Item>
              <a href='#'>Queries</a>
            </Breadcrumb.Item>
            <Breadcrumb.Item className='query-file-name'>Untitled.sql</Breadcrumb.Item>
          </Breadcrumb>
        </div>
        <div>
          <Space size='middle' align='center'>
            <Dropdown.Button className='query-save-btn' size='small' onClick={handleButtonClick} icon={<BiCaretDown />} overlay={menu}>
              Save
            </Dropdown.Button>
            <Button
              type='primary'
              size='small'
              onClick={onClickToRun}
              icon={<MdPlayArrow style={{ marginRight: 4 }} />}
              className='query-run-btn'>
              Run
            </Button>
          </Space>
        </div>
      </div>
    </div>
  );
};

const DBList: FC = () => {
  function handleMenuClick(e: any) {
    message.info("Click on menu item.");
    console.log("click", e);
  }
  return (
    <div className='db-list-container'>
      <CustomScroll heightRelativeToParent='100vh'>
        <Menu onClick={handleMenuClick} mode='inline' defaultOpenKeys={["sub1"]} className='db-menu'>
          <SubMenu key='sub1' icon={<MailOutlined />} title='Public'>
            <Menu.Item
              key='1'
              className='center-name'
              icon={
                <i>
                  <AiOutlineTable style={{ fontSize: 15 }} />
                </i>
              }>
              Collection 1
            </Menu.Item>
            <Menu.Item
              key='2'
              icon={
                <i>
                  <AiOutlineTable style={{ fontSize: 15 }} />
                </i>
              }>
              Collection 2
            </Menu.Item>
            <Menu.Item key='3' icon={<AiOutlineTable style={{ fontSize: 15 }} />}>
              Collection 3
            </Menu.Item>
          </SubMenu>
          <SubMenu key='sub2' icon={<MailOutlined />} title='Public'>
            <Menu.Item key='4' icon={<AiOutlineTable style={{ fontSize: 15 }} />}>
              Collection 1
            </Menu.Item>
            <Menu.Item key='5' icon={<AiOutlineTable style={{ fontSize: 15 }} />}>
              Collection 2
            </Menu.Item>
            <Menu.Item key='6' icon={<AiOutlineTable style={{ fontSize: 15 }} />}>
              Collection 3
            </Menu.Item>
          </SubMenu>
        </Menu>
      </CustomScroll>
    </div>
  );
};

const QueryResultTable: FC = () => {
  return (
    <Skeleton loading={false} paragraph={{ rows: 4 }} active>
      <Card
        title='Query Result'
        extra={
          <Button size='small' className='query-result-export-btn'>
            Export <BiCaretDown style={{ marginLeft: 5 }} />
          </Button>
        }
        className='card-shadow-light query-result-table'
        bordered={false}>
        <Table dataSource={[]} pagination={false} bordered={false} className='tbl-cluster'>
          <Column title='NAME' dataIndex='' className='result-col table-cell-light' />
          <Column title='ADDRESS' dataIndex='' className='result-col table-cell-light' />
          <Column title='DATE' dataIndex='' className='result-col table-cell-light' />
          <Column title='COLUMN' dataIndex='' className='result-col table-cell-light' />
        </Table>
      </Card>
    </Skeleton>
  );
};

interface QueryFolder {
  title: ReactNode;
  key: string;
  isLeaf?: boolean;
  children?: Array<QueryFolder>;
}

const DatabaseBrowse: FC<{ orgSlugId: string; workspaceId: number; databaseId: number }> = () => {
  const [saveQuery, setSaveQuery] = React.useState<{ isQueryModal: boolean; fileName: string; queries: string; selectedFolderKey: string }>(
    {
      isQueryModal: true,
      fileName: "",
      queries: "",
      selectedFolderKey: "",
    }
  );

  const [folderName, setFolderName] = React.useState<string>("");

  const [queryFolders, setQueryFolders] = React.useState<QueryFolder[]>([
    {
      title: "parent 0",
      key: "0-0",
      children: [
        { title: "leaf 0-0", key: "0-0-0", isLeaf: true },
        { title: "leaf 0-1", key: "0-0-1", isLeaf: true },
        { title: "leaf 0-2", key: "0-0-2", children: [] },
      ],
    },
    {
      title: "parent 1",
      key: "0-1",
      children: [
        { title: "leaf 1-0", key: "0-1-0", isLeaf: true },
        { title: "leaf 1-1", key: "0-1-1", isLeaf: true },
      ],
    },
  ]);

  const clickQueryTo = () => {
    setSaveQuery({ ...saveQuery, isQueryModal: true });
  };
  const cancelSaveQuery = () => {
    setSaveQuery({ ...saveQuery, isQueryModal: false });
  };

  const onSaveQuery = () => {};
  const onClickToRun = () => {};

  const onSelect = (keys: React.Key[], info: any) => {
    setSaveQuery({ ...saveQuery, selectedFolderKey: keys[0] as string });
  };

  const onCreateFolder = () => {
    setQueryFolders((prv) => []);
    const addedNewFolder = queryFolders.map((f) => {
      if (f.key === saveQuery.selectedFolderKey) {
        return {
          ...f,
          children: [
            ...f.children,
            {
              title: (
                <Input
                  placeholder='Folder Name'
                  onChange={(e) => {
                    setFolderName(e.target.value);
                  }}
                />
              ),
              key: `${queryFolders.length + 1}`,
              isLeaf: false,
              children: [],
            },
          ],
        };
      } else {
        return { ...f };
      }
    });

    setQueryFolders(addedNewFolder);
  };

  // console.log(saveQuery);

  return (
    <div className='database-browser-wrapper'>
      <DBBrowserHeader clickQueryTo={clickQueryTo} onClickToRun={onClickToRun} />
      <div className='height-100 db-info-container'>
        <DBList />
        <div className='query-container'>
          <div className='query-editor'>
            <Editor height='40vh' defaultLanguage='sql' language='sql' defaultValue='// some comment' />
          </div>
          <QueryResultTable />
        </div>
      </div>

      <Modal
        title='Save Query To'
        visible={saveQuery.isQueryModal}
        onOk={onSaveQuery}
        footer={null}
        className='save-query-modal'
        onCancel={cancelSaveQuery}>
        <div className='query-file-name'>Name</div>
        <Input placeholder='File Name' onChange={(e) => setSaveQuery({ ...saveQuery, fileName: e.target.value })} />

        <div className='select-folder-container'>
          <DirectoryTree onSelect={onSelect} treeData={queryFolders} className='folder-tree-container' />
        </div>

        <div className='footer-btns'>
          <Button onClick={onCreateFolder}>New Folder</Button>
          <Button type='primary'>Save</Button>
        </div>
      </Modal>
    </div>
  );
};

export default DatabaseBrowse;
