import { Breadcrumb, Button, Space, Table, Tooltip, List, message, Avatar } from "antd";
import * as React from "react";
import { FileBrowserProps, FileItem } from "../../models/FileBrowser";
import S3Img from "../../static/img/s3.png";
import { MdCreateNewFolder, MdFileDownload, MdFileUpload, MdFolder, MdInfo, MdSync } from "react-icons/md";
import "./FileBrowser.scss";
import Search from "antd/lib/input/Search";
import Column from "antd/lib/table/Column";
import { bytesToSize, getReadableTime } from "../../services/Utils";
import { FaFile, FaFileCsv, FaFileImage, FaFilePdf } from "react-icons/fa";
import VirtualList from "rc-virtual-list";

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

export const FileBrowser: React.FC<FileBrowserProps> = ({ storageService, name, cwd, items }) => {
  const onScroll = (e: any) => {};

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
      </div>
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
          <Search placeholder='Search by prefix' style={{ width: 200 }} />
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
  );
};

export const FileManager: React.FC = () => {
  return (
    <FileBrowser
      storageService='AWS-S3'
      name='gigahex-data'
      cwd={["user", "hadoop", "data"]}
      items={[
        {
          name: "normal-directory",
          size: 2342423,
          isDirectory: true,
          dateCreated: 1647268008,
        },
        {
          name: "another-directory",
          size: 2342,
          isDirectory: true,
          dateCreated: 1647267008,
        },
        {
          name: "locations.csv",
          size: 2342,
          isDirectory: false,
          extension: "csv",
          dateCreated: 1647267008,
        },
        {
          name: "india.png",
          size: 2342,
          isDirectory: false,
          extension: "png",
          dateCreated: 1647267008,
        },
        {
          name: "docs.pdf",
          size: 2342,
          isDirectory: false,
          extension: "pdf",
          dateCreated: 1647267008,
        },
      ]}
    />
  );
};
