import { Breadcrumb, Button, Space, Table, Tooltip, List, message, Avatar, Skeleton, Alert, Dropdown, Menu, Popconfirm, Modal } from "antd";
import * as React from "react";
import { FileBrowserProps, FileItem, FileListing } from "../../models/FileBrowser";
import S3Img from "../../static/img/s3.png";
import { MdCreateNewFolder, MdFileDownload, MdFileUpload, MdFolder, MdInfo, MdOutlineMoreHoriz, MdSync } from "react-icons/md";
import "./FileBrowser.scss";
import { bytesToSize, getReadableTime } from "../../services/Utils";
import { FaFile, FaFileCsv, FaFileImage, FaFilePdf } from "react-icons/fa";
import VirtualList from "rc-virtual-list";
import ContentLoader from "react-content-loader";
import Connections from "../../services/Connections";
import ServiceConnectionBuilder from "../../pages/workspace/AddDatasource/ServiceConnectionBuilder";

const FileIcon: React.FC<{ isDirectory: boolean; fileType: string }> = ({ isDirectory, fileType }) => {
  if (isDirectory) {
    return (
      <i>
        <MdFolder />
      </i>
    );
  } else {
    switch (fileType) {
      case "csv":
        return (
          <i>
            <FaFileCsv />
          </i>
        );

      case "png":
      case "jpeg":
      case "jpg":
        return (
          <i>
            <FaFileImage />
          </i>
        );

      case "pdf":
        return (
          <i>
            <FaFilePdf />
          </i>
        );

      default:
        return (
          <i>
            <FaFile />
          </i>
        );
    }
  }
};

export const FileBrowser: React.FC<FileBrowserProps> = ({
  connectionId,
  storageService,
  name,
  editMode,
  cwd,
  items,
  closeEditDrawer,
  showEditDrawer,
}) => {
  const onScroll = (e: any) => {};

  const handleDeleteConnection = () => {
    Connections.deleteConnection(connectionId, (r) => {
      message.success("Connection was deleted");
    });
  };

  const deleteWarning = (name: string) => {
    Modal.warning({
      title: (
        <span>
          Delete <b>{name}</b>
        </span>
      ),
      content: `Are you sure you want to delete the connection?`,
      onOk: handleDeleteConnection,
      okCancel: true,
      okText: "Yes",
      cancelText: "No",
    });
  };

  const menu = (
    <Menu>
      <Menu.Item key='1' onClick={(e) => deleteWarning(name)}>
        Delete
      </Menu.Item>
    </Menu>
  );

  return (
    <>
      <div className='workspace-content-header'>
        <Space>
          <img src={S3Img} />
          <div>{name}</div>
          <div>&gt;</div>
          <Breadcrumb separator='>'>
            {cwd.map((d, i) => (
              <Breadcrumb.Item key={i}>{d}</Breadcrumb.Item>
            ))}
          </Breadcrumb>
        </Space>
        <div>
          <Dropdown.Button overlay={menu} onClick={showEditDrawer} placement='bottomLeft' trigger={["click"]} icon={<MdOutlineMoreHoriz />}>
            Edit
          </Dropdown.Button>
        </div>
      </div>
      {items.length > 0 && (
        <>
          <div className='file-browser-controls'>
            <Space>
              <Tooltip title='Refresh'>
                <Button icon={<MdSync />}></Button>
              </Tooltip>

              <Tooltip title='New folder'>
                <Button icon={<MdCreateNewFolder />}></Button>
              </Tooltip>

              <Tooltip title='Upload file'>
                <Button icon={<MdFileUpload />}></Button>
              </Tooltip>
            </Space>
          </div>
          <div>
            <List className='file-list'>
              <VirtualList data={items} itemHeight={40} itemKey='email' onScroll={onScroll}>
                {(item) => (
                  <List.Item
                    key={item.name}
                    className='file-item'
                    actions={[<Button icon={<MdInfo />}></Button>, <Button icon={<MdFileDownload />}></Button>]}>
                    <List.Item.Meta
                      avatar={<FileIcon isDirectory={item.isDirectory} fileType={item.extension || ""} />}
                      title={item.name}
                      description={item.isDirectory ? "--" : bytesToSize(item.size)}
                    />
                  </List.Item>
                )}
              </VirtualList>
            </List>
          </div>
        </>
      )}

      <ServiceConnectionBuilder service={storageService} isOpen={editMode} onClose={closeEditDrawer} initialValues={{ name: name }} />
    </>
  );
};

export const FileManager: React.FC<{ connectionId: number }> = ({ connectionId }) => {
  const [fileStatus, setFileStatus] = React.useState<{
    isValid: boolean;
    loading: boolean;
    editMode: boolean;
    listing?: FileListing;
    cwd: string[];
    err?: string;
  }>({
    isValid: false,
    loading: true,
    editMode: false,
    cwd: ["/"],
  });

  React.useEffect(() => {
    Connections.listRootDirs(
      connectionId,
      (r) => {
        setFileStatus({ ...fileStatus, loading: false, listing: r, err: undefined });
      },
      (err) => {
        setFileStatus({
          ...fileStatus,
          loading: false,
          err: err.error,
          listing: {
            fsName: err.name,
            files: [],
            hasMore: false,
            provider: err.provider,
          },
        });
      }
    );
  }, [connectionId]);

  const closeEditDrawer = () => {
    setFileStatus({ ...fileStatus, editMode: false });
  };

  const showEditDrawer = () => {
    setFileStatus({ ...fileStatus, editMode: true });
  };

  return (
    <>
      <Skeleton avatar paragraph={{ rows: 4 }} loading={fileStatus.loading}>
        {fileStatus.listing && (
          <FileBrowser
            connectionId={connectionId}
            cwd={fileStatus.cwd}
            items={fileStatus.listing.files}
            storageService={fileStatus.listing.provider}
            editMode={fileStatus.editMode}
            name={fileStatus.listing.fsName}
            closeEditDrawer={closeEditDrawer}
            showEditDrawer={showEditDrawer}
          />
        )}
        {fileStatus.err && (
          <div style={{ margin: "100px 20px 20px 20px" }}>
            <Alert message='Error' description={fileStatus.err} type='error' showIcon />
            <Space style={{ marginTop: 20, alignItems: "center" }}></Space>
          </div>
        )}
      </Skeleton>
    </>
  );
};
