import React, { FC } from "react";
import "./AddDatasource.scss";
import { Drawer, Menu } from "antd";

import DatasourceCard from "../../../components/Cards/DatasourceCard";

import { IconAWS } from "../../../components/Icons/PlatformIcons";

const AddDatasource: FC<{ orgSlugId: string; workspaceId: number }> = () => {
  const [selectedKey, setSelectedKey] = React.useState("file-system");
  const [sourceDrawer, setSrouceDrawer] = React.useState<{ isOpen: boolean; name: string }>({
    isOpen: false,
    name: "",
  });
  const selectMenu = (e: any) => {
    setSelectedKey(e.key);
  };

  const onCloseDrawer = () => {
    setSrouceDrawer({ ...sourceDrawer, isOpen: false });
  };

  const openSourceDrawer = (name: string) => {
    setSrouceDrawer({ ...sourceDrawer, isOpen: true, name: name });
  };

  return (
    <div className='add-datasource-wrapper'>
      <div className='header-title'>Add Datasource</div>
      <div className='data-source-menu'>
        <Menu onClick={selectMenu} selectedKeys={[selectedKey]} mode='horizontal'>
          <Menu.Item key='file-system'>
            <a className='file-system' href='#file-system'>
              File System
            </a>
          </Menu.Item>
          <Menu.Item key='databases'>
            <a className='databases' href='#databases'>
              Databases
            </a>
          </Menu.Item>
          <Menu.Item key='messaging-system'>
            <a className='messaging-system' href='#messaging-system'>
              Messaging System
            </a>
          </Menu.Item>
        </Menu>
      </div>
      <div className='data-source-list'>
        <div id='file-system' className='data-source-section'>
          <div className='tabs-title'>File System</div>
          <div className='sources-cards'>
            <DatasourceCard
              sourceIcon={<IconAWS className='source-icon' selected={true} />}
              sourceName='AWS S3'
              sourceDesc='Object storage built to retrives lorem sdf sdfsdfs dsfd sdfdsf sdfsdfsd ssfddsf'
              onClickAdd={openSourceDrawer}
              btnText='ADD BUCKET'
            />
          </div>
        </div>
        <div id='databases' className='data-source-section'>
          <div className='tabs-title'>Databases</div>
          <div className='sources-cards'>
            <DatasourceCard
              sourceIcon={<IconAWS className='source-icon' selected={true} />}
              sourceName='AWS S3'
              sourceDesc='Object storage built to retrives'
              onClickAdd={openSourceDrawer}
              btnText='ADD BUCKET'
            />
          </div>
        </div>
        <div id='messaging-system' className='data-source-section'>
          <div className='tabs-title'>Messaging System</div>
          <div className='sources-cards'>
            <DatasourceCard
              sourceIcon={<IconAWS className='source-icon' selected={true} />}
              sourceName='AWS S3'
              sourceDesc='Object storage built to retrives'
              onClickAdd={openSourceDrawer}
              btnText='ADD BUCKET'
            />
          </div>
        </div>
      </div>

      <Drawer title={sourceDrawer.name} placement='right' width={300} onClose={onCloseDrawer} visible={sourceDrawer.isOpen}>
        <p>Some contents...</p>
        <p>Some contents...</p>
        <p>Some contents...</p>
      </Drawer>
    </div>
  );
};

export default AddDatasource;
